import Foundation
import SwiftData
import SwiftUI

/// Composition root. Owns the container, services, and repositories, and injects
/// the current `ownerID` (from `AuthService`) into every repository so all
/// writes are stamped for row ownership. Views read collaborators from here via
/// `@Environment`, never constructing CloudKit/Supabase types themselves.
@MainActor
@Observable
final class AppEnvironment {
    let container: ModelContainer
    var context: ModelContext { container.mainContext }

    let settings: SettingsStore
    let auth: AuthService
    let sync: SyncEngine
    let health: HealthKitService
    let healthIngestor: HealthIngestor
    /// Snapshot backup of the whole archive to the user's private iCloud.
    let icloudBackup: ICloudBackupService
    private var lastAutoICloudBackup: Date?
    let notifications: NotificationService
    /// Delegate for medication-reminder taps (mark taken / always mark taken).
    private let notificationCoordinator = NotificationCoordinator()
    let speech: SpeechRecognitionService
    let chat: ChatService
    /// Writes the companion has drafted for her to confirm (never auto-applied).
    let proposals: CompanionProposals
    let treatments: TreatmentCatalogService

    let users: UserRepository
    let symptoms: SymptomRepository
    let checkIns: CheckInRepository
    let cycle: CycleRepository
    let medications: MedicationRepository
    let insights: InsightRepository
    /// Once-a-day reflection over her own data, kept as a dated record.
    let dailySummary: DailySummaryService

    init(container: ModelContainer, provider: SyncProvider) {
        self.container = container
        let context = container.mainContext

        self.settings = SettingsStore()
        let auth = AuthService()
        self.auth = auth
        let ownerID: OwnerIDProvider = { [weak auth] in auth?.ownerID ?? "" }

        self.users = UserRepository(context: context, ownerID: ownerID)
        self.symptoms = SymptomRepository(context: context, ownerID: ownerID)
        self.checkIns = CheckInRepository(context: context, ownerID: ownerID)
        self.cycle = CycleRepository(context: context, ownerID: ownerID)
        self.medications = MedicationRepository(context: context, ownerID: ownerID)
        self.insights = InsightRepository(context: context, ownerID: ownerID)
        self.dailySummary = DailySummaryService(context: context, ownerID: ownerID)

        self.sync = SyncEngine(context: context, provider: provider)
        self.health = HealthKitService()
        self.healthIngestor = HealthIngestor(context: context, ownerID: ownerID, symptoms: symptoms)
        self.icloudBackup = ICloudBackupService(containerIdentifier: AppEnvironment.cloudContainerID, context: context)
        self.notifications = NotificationService()
        self.speech = SpeechRecognitionService()

        // The companion agent: a read/analysis layer over the repositories, a
        // confirm-before-write proposal sink, and the shared toolbox both engines
        // drive.
        let proposals = CompanionProposals(context: context, checkIns: checkIns,
                                           symptoms: symptoms, ownerID: ownerID)
        self.proposals = proposals
        let dataService = CompanionDataService(context: context, checkIns: checkIns,
                                               symptoms: symptoms, cycle: cycle,
                                               medications: medications, users: users)
        let toolbox = CompanionToolbox(data: dataService, proposals: proposals)
        self.chat = AppEnvironment.makeCompanion(toolbox: toolbox)
        self.treatments = TreatmentCatalogService()

        // Medication reminder taps: register the "Mark taken" / "Always mark taken"
        // buttons and route them (and the plain tap) to the handlers below.
        notifications.registerCategories()
        notificationCoordinator.env = self
        notifications.setDelegate(notificationCoordinator)
    }

    /// Seed reference data on launch.
    func bootstrap() {
        symptoms.syncBuiltIns()
        insights.refreshDerived()
        medications.migrateLegacySchedules()
        refreshMedicationReminders()
        refreshLifestyleReminders()
        autoLogTodaysDoses()
        if users.currentProfile()?.healthKitAuthorized == true {
            syncHealthData()
        }
        // Write today's reflection on the first open of each calendar day. Runs
        // after the derivations above so it reads the freshest local data.
        Task { await dailySummary.refreshIfNeeded() }
    }

    /// How far back to import on a sync. A year gives the cycle and premenstrual
    /// detectors enough history; dedup keeps repeat launches cheap on writes.
    private static let healthImportDays = 365

    /// Pull the broad Apple Health slice (sleep, activity, vitals, symptoms,
    /// menstrual flow) and merge it into Keel's store, so it feeds the dashboard,
    /// patterns, and companion automatically ("less to log"). Backfill only, so a
    /// manual entry is never overwritten.
    ///
    /// Requires the HealthKit capability on a signed build; a no-op otherwise
    /// (the authorization request fails on an unsigned build).
    func syncHealthData() {
        Task {
            guard await health.requestAuthorization() else { return }
            let snapshot = await health.snapshot(lastDays: Self.healthImportDays)
            ingestHealthSnapshot(snapshot)
        }
    }

    /// Merge a snapshot into the store. Internal so it can be driven with synthetic
    /// data in tests (HealthKit itself needs a signed build to read anything).
    func ingestHealthSnapshot(_ snapshot: HealthSnapshot) {
        let summary = healthIngestor.ingest(snapshot)
        if summary.activity + summary.vitals + summary.symptomsLinked
            + summary.symptomsArchived + summary.flow > 0 { requestSync() }
    }

    /// Back-compat shim for the sleep-only ingestion probe.
    func ingestSleepSamples(_ sleepByDay: [Date: Double]) {
        ingestHealthSnapshot(HealthSnapshot(sleepByDay: sleepByDay))
    }

    /// Master notifications switch (Settings). Off clears every pending Keel
    /// notification; on re-lays the ones she's opted into.
    func setPushNotificationsEnabled(_ on: Bool) {
        settings.pushNotifications = on
        if on {
            refreshMedicationReminders()
            refreshLifestyleReminders()
        } else {
            notifications.cancelAll()
        }
    }

    /// Re-lay medication reminders. Cyclic schedules are laid out a few weeks at
    /// a time, because a repeating calendar trigger can't express a pause, so
    /// they need topping up whenever the app is opened.
    func refreshMedicationReminders() {
        #if DEBUG
        if DebugHarness.suppressReminders { return }
        #endif
        guard settings.pushNotifications else { return }
        let pending = medications.active().map { ($0.id, $0.name, $0.schedule) }
        let horizon = NotificationService.cycleHorizon(activeMedications: pending.count)
        Task {
            for (id, name, schedule) in pending {
                await notifications.rescheduleMedication(id: id, name: name, schedule: schedule,
                                                        cycleHorizon: horizon)
            }
        }
    }

    /// Re-arm the lifestyle reminders she has enabled. Calendar triggers persist
    /// across launches, but not across a reinstall, so we top them up on open.
    /// Only touches ones already enabled, so nothing is scheduled she didn't ask for.
    func refreshLifestyleReminders() {
        #if DEBUG
        if DebugHarness.suppressReminders { return }
        #endif
        guard settings.pushNotifications else { return }
        let enabled = settings.enabledReminderIDs
        guard !enabled.isDisjoint(with: ["dailyCheckIn", "hydration", "movement", "winddown"]) else { return }
        let c = settings.reminderConfig
        Task {
            guard await notifications.requestAuthorization() else { return }
            if enabled.contains("dailyCheckIn") { notifications.scheduleDailyCheckInReminder(hour: c.checkInHour, minute: c.checkInMinute) }
            if enabled.contains("hydration") { notifications.scheduleHydration(startHour: c.hydrationStartHour, endHour: c.hydrationEndHour, everyHours: c.hydrationIntervalHours) }
            if enabled.contains("movement") { notifications.scheduleMovement(hour: c.movementHour, minute: c.movementMinute, weekdaysOnly: c.movementWeekdaysOnly) }
            if enabled.contains("winddown") { notifications.scheduleWindDown(hour: c.windDownHour, minute: c.windDownMinute) }
        }
    }

    // MARK: Medication reminder taps

    /// Mark a medicine's dose taken from a reminder tap / "Mark taken" button.
    func markMedicationTaken(medicationID: UUID, slot: String?, on day: Date) {
        guard let med = medications.active(id: medicationID) else { return }
        medications.setTaken(med, on: day, slot: slot, taken: true)
        Haptics.success()
        requestSync()
    }

    /// "Always mark taken": turn on auto-logging for the medicine and log this dose.
    func enableMedicationAutoLog(medicationID: UUID, slot: String?, on day: Date) {
        guard let med = medications.active(id: medicationID) else { return }
        medications.setAutoLog(med, true)
        medications.setTaken(med, on: day, slot: slot, taken: true)
        Haptics.success()
        requestSync()
    }

    /// Fill in today's already-due doses for auto-log medicines. Called when the
    /// app opens (foreground) — the only time we can, since iOS won't run us at the
    /// dose time while closed. Today only; never past days, never the future.
    func autoLogTodaysDoses() {
        let count = medications.autoLogTodaysDueDoses()
        if count > 0 { requestSync() }
    }

    /// Fire-and-forget sync (offline-friendly; failures are logged, not fatal).
    func requestSync() {
        Task { await sync.syncNow() }
    }

    /// Chooses the live backend. CloudKit needs entitlements + a signed build, so
    /// until a team is configured we fall back to a local no-op provider. Swapping
    /// this line to `SupabaseSyncProvider(...)` is the entire backend migration.
    static func makeProvider() -> SyncProvider {
        #if targetEnvironment(simulator)
        return NoopSyncProvider()
        #else
        return CloudKitSyncProvider(containerIdentifier: cloudContainerID)
        #endif
    }

    /// The private CloudKit container, shared by the sync provider and iCloud
    /// backup. Must match the container declared in `Config/Keel.entitlements`.
    static let cloudContainerID = "iCloud.com.keel"

    /// Back up to iCloud when she's opted in and it's available. Fire-and-forget:
    /// failures are silent (a manual "Back up now" surfaces errors), and it's
    /// throttled so a background cycle can't hammer CloudKit.
    func autoBackupToICloud() {
        guard settings.icloudBackup, settings.autoBackup else { return }
        if let last = lastAutoICloudBackup, Date().timeIntervalSince(last) < 3600 { return }
        Task {
            guard await icloudBackup.availability().isAvailable else { return }
            lastAutoICloudBackup = Date()
            _ = try? await icloudBackup.backUpNow()
        }
    }

    /// Assembles the companion: Apple Intelligence first (on eligible devices
    /// running iOS 26+), then Gemini if `KEEL_GEMINI_BASE_URL` is configured, then
    /// `LocalCompanionFallback` so the chat always answers offline (and can still
    /// draft a log card).
    ///
    /// A Gemini key must never ship in the client: point `KEEL_GEMINI_BASE_URL` at
    /// a proxy / Supabase Edge Function that holds the key, and leave the app's key
    /// nil. `KEEL_GEMINI_API_KEY` is a dev-only convenience for calling
    /// generativelanguage.googleapis.com directly.
    static func makeCompanion(toolbox: CompanionToolbox) -> ChatService {
        var engines: [ChatEngine] = []

        let info = Bundle.main.infoDictionary
        var geminiBaseURL = info?["KEEL_GEMINI_BASE_URL"] as? String
        var geminiKey = info?["KEEL_GEMINI_API_KEY"] as? String
        var skipApple = false
        #if DEBUG
        // Launch env overrides for local testing without editing Info.plist, e.g.
        // SIMCTL_CHILD_KEEL_GEMINI_BASE_URL=http://localhost:8787. KEEL_DISABLE_APPLE
        // skips the on-device engine (which can't generate in the Simulator) so
        // interactive Gemini replies aren't held up behind its slow failure.
        let env = ProcessInfo.processInfo.environment
        if let overrideURL = env["KEEL_GEMINI_BASE_URL"], !overrideURL.isEmpty { geminiBaseURL = overrideURL }
        if let overrideKey = env["KEEL_GEMINI_API_KEY"], !overrideKey.isEmpty { geminiKey = overrideKey }
        skipApple = env["KEEL_DISABLE_APPLE"] != nil
        #endif

        #if canImport(FoundationModels)
        if !skipApple, #available(iOS 26.0, *) {
            engines.append(AppleIntelligenceEngine(toolbox: toolbox))
        }
        #endif

        if let urlString = geminiBaseURL, !urlString.isEmpty, let url = URL(string: urlString) {
            engines.append(GeminiChatEngine(baseURL: url, apiKey: geminiKey, model: "gemini-2.5-flash",
                                            toolbox: toolbox, limiter: GeminiRateLimiter()))
        }

        // The offline fallback can still draft a log card from a clear request, so
        // "add a check-in" keeps working even when no AI engine is available.
        return CompanionChatService(engines: engines, fallback: LocalCompanionFallback(toolbox: toolbox))
    }
}
