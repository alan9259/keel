#if DEBUG
import Foundation
import SwiftData
import HealthKit
import UserNotifications
import PDFKit
#if canImport(FoundationModels)
import FoundationModels
#endif

/// Launch-argument hooks used only for local verification (DEBUG builds). They
/// drive the *real* repositories so screenshots and the persistence check
/// exercise production code paths — never compiled into release.
///
/// Examples:
///   -uitOnboarded            skip onboarding, land on the dashboard
///   -uitReset                soft-delete existing check-ins/meds/cycle first
///   -uitSeedCheckIn          create one representative check-in
///   -uitSeedMed              add two sample medications
///   -uitPrintCounts          log current row counts to stdout
///   -uitRouteCycle|Meds|Patterns  push that screen on launch
enum DebugHarness {
    private static var args: Set<String> { Set(ProcessInfo.processInfo.arguments) }

    static var forcedOnboarded: Bool { args.contains("-uitOnboarded") }

    /// Skip re-arming reminders on launch, so the notification-permission prompt
    /// doesn't block automated screenshots. DEBUG-only; never in Release.
    static var suppressReminders: Bool { args.contains("-uitNoPrompt") }

    /// Start the dashboard on a specific day (for verifying day-scoped views).
    static var startSelDate: Date? { args.contains("-uitYesterday") ? Date.now.startOfDay.adding(days: -1) : nil }

    static var showCheckIn: Bool { args.contains("-uitShowCheckIn") }
    /// Auto-open the cycle day-log sheet, to screenshot it without a tap.
    static var showCycleSheet: Bool { args.contains("-uitCycleSheet") }

    /// Simulate picking a mood in the entry slide → hands off to the detail step.
    static var entryHandoff: Bool { args.contains("-uitEntryHandoff") }

    /// Auto-open the "more" symptom picker from the check-in for screenshots.
    /// The composer and edit hooks below both live inside it.
    static var showSymptomPicker: Bool {
        args.contains("-uitSymptomPicker") || openSymptomComposer || editSymptoms
    }

    /// Auto-open the inline "add your own" composer (Sleep & rest) for screenshots.
    static var openSymptomComposer: Bool { args.contains("-uitSymptomComposer") }

    /// Enter symptom edit mode in the picker for screenshots.
    static var editSymptoms: Bool { args.contains("-uitEditSymptoms") }

    /// Auto-open the treatment/supplement picker on the medications screen.
    static var showTreatmentPicker: Bool {
        args.contains("-uitAddTreatment") || showTreatmentDetail || supplementsTab
    }

    /// Jump straight to the details step on an off-label item, for screenshots.
    static var showTreatmentDetail: Bool {
        args.contains("-uitTreatmentDetail") || cycleScheduleDemo
    }

    /// Open that details step on a cycle schedule with a time set.
    static var cycleScheduleDemo: Bool { args.contains("-uitCycleSchedule") }

    /// Open the picker on the supplements tab.
    static var supplementsTab: Bool { args.contains("-uitSupplements") }

    /// Opt in to the sensitive (intimacy & bladder) group so its chips render.
    static var showSensitiveSymptoms: Bool { args.contains("-uitSensitiveSymptoms") }

    /// Backup restore stage: 0 idle, 1 confirm, 2 done.
    static var backupStage: Int {
        if args.contains("-uitBackupDone") { return 2 }
        if args.contains("-uitBackupConfirm") { return 1 }
        return 0
    }

    /// Auto-send a demo message on the chat screen to exercise streaming.
    static var chatDemoMessage: String? {
        args.contains("-uitChatDemo") ? "I've been struggling with sleep lately." : nil
    }

    /// Jump onboarding to a specific screen for screenshots.
    static var onboardingStartStep: Int? {
        if args.contains("-uitOnboardRightPlace") { return 1 }
        if args.contains("-uitOnboardCreate") { return 2 }
        if args.contains("-uitOnboardPathway") { return 3 }
        if args.contains("-uitOnboardHealth") { return 4 }
        if args.contains("-uitOnboardReady") { return 5 }
        return nil
    }

    /// Open the GP Visit Summary flow at a chosen step, so each screen can be shot
    /// without driving the taps between steps.
    static var gpInitialStep: GPSummaryFlowModel.Step? {
        if args.contains("-uitGPReview") { return .review }
        if args.contains("-uitGPDetails") { return .details }
        if args.contains("-uitGPPreview") { return .preview }
        return nil
    }

    static var initialRoute: MainRoute? {
        if args.contains("-uitRouteCycle") { return .cycle }
        if args.contains("-uitRouteMeds") { return .medications }
        if args.contains("-uitRoutePatterns") { return .patterns }
        if args.contains("-uitRouteMore") { return .more }
        if args.contains("-uitRouteProfile") { return .profile }
        if args.contains("-uitRouteChat") { return .chat }
        if args.contains("-uitRouteColour") { return .colourMode }
        if args.contains("-uitRouteThemes") { return .themes }
        if args.contains("-uitRouteMoodIcons") { return .moodIcons }
        if args.contains("-uitRouteReports") { return .reports }
        if args.contains("-uitRouteActivities") { return .activities }
        if args.contains("-uitRouteReminders") { return .reminders }
        if args.contains("-uitRouteHealth") { return .appleHealth }
        if args.contains("-uitRouteBackup") { return .backup }
        if args.contains("-uitRouteSettings") { return .settings }
        if args.contains("-uitRouteConnect") { return .connect }
        if args.contains("-uitRouteAbout") { return .about }
        if args.contains("-uitRouteSupport") { return .support }
        if args.contains("-uitRouteGPSummary") { return .gpSummary }
        if args.contains("-uitRoutePrivacy") { return .privacy }
        return nil
    }

    /// A multi-level push for testing nested navigation and the back-swipe
    /// following the hierarchy, e.g. Home -> More -> About.
    static var initialRouteStack: [MainRoute] {
        if args.contains("-uitRouteMoreAbout") { return [.more, .about] }
        return []
    }

    @MainActor
    static func apply(env: AppEnvironment) {
        let args = self.args
        guard args.contains(where: { $0.hasPrefix("-uit") }) else { return }

        // Ensure a stable identity so ownerID stamping works in the harness.
        if env.auth.ownerID.isEmpty { env.auth.continueLocally(name: "Mara") }

        if args.contains("-uitDark") { env.settings.colourMode = .dark }
        if args.contains("-uitThemeSlate") { env.settings.themeID = "slate" }
        if args.contains("-uitHealthConnected") {
            _ = env.users.upsertProfile(firstName: "there", email: nil, appleUserID: nil)
            env.users.setHealthKitAuthorized(true)
        }
        if args.contains("-uitAllReminders") {
            env.settings.enabledReminderIDs = ["dailyCheckIn", "medication", "hydration", "movement", "winddown"]
        }
        if args.contains("-uitMultiEntry") {
            env.symptoms.syncBuiltIns()
            let sym = env.symptoms.allActive().first { $0.name == "Hot flushes" }
            let picks = sym.map { [(symptom: $0, severity: 2)] } ?? []
            env.checkIns.create(mood: .good, energy: 70, notes: "Morning felt steady.",
                                symptoms: [], date: Date.now.startOfDay.addingTimeInterval(8 * 3600))
            env.checkIns.create(mood: .low, energy: 40, notes: "Rough afternoon, a flush hit.",
                                symptoms: picks, date: Date.now.startOfDay.addingTimeInterval(15 * 3600))
        }
        if args.contains("-uitMedDemo") {
            (try? env.context.fetch(FetchDescriptor<MedicationLog>()))?.forEach { env.context.delete($0) }
            try? env.context.save()
            let med = env.medications.active().first
                ?? env.medications.add(name: "Vitamin D", dosage: "2000 IU", timing: "Every day", method: nil)
            // Home-log adherence is whole-day (nil slot), matching how she ticks it
            // on the home screen. Every active med is tracked by default, so it shows.
            env.medications.setTaken(med, on: .now, slot: nil, taken: true) // TODAY only
        }
        if showSensitiveSymptoms { env.settings.showsSensitiveSymptoms = true }

        if args.contains("-uitReset") {
            env.checkIns.all().forEach { $0.softDelete() }
            env.medications.active().forEach { env.medications.archive($0) }
            try? env.context.save()
        }

        if args.contains("-uitSeedCheckIn") {
            env.symptoms.syncBuiltIns()
            let names = ["Brain fog", "Hot flushes", "Anxious"]
            let severities = [2, 3, 1]   // moderate, severe, mild
            let picks: [(symptom: Symptom, severity: Int)] = env.symptoms.allActive()
                .compactMap { s in names.firstIndex(of: s.name).map { (s, severities[$0]) } }
            env.checkIns.create(
                mood: .okay,
                energy: 62,
                notes: "Slept poorly, a bit foggy today.",
                symptoms: picks
            )
        }

        if args.contains("-uitSeedVitals") {
            // 16 days of resting HR + HRV, with resting HR running higher after the
            // shorter-sleep nights, so the "Your body lately" card and its sleep note
            // both render on the sim (which has no real Apple Health data).
            let owner = env.auth.ownerID
            let today = Date().startOfDay
            for i in 0..<16 {
                let day = today.adding(days: -i)
                let short = i.isMultiple(of: 2)
                env.context.insert(HealthSample(typeID: "restingHeartRate", day: day,
                                                value: short ? 66 : 60, unit: "bpm", source: .healthKit, ownerID: owner))
                env.context.insert(HealthSample(typeID: "hrv", day: day,
                                                value: 42 + Double(i % 3), unit: "ms", source: .healthKit, ownerID: owner))
                env.context.insert(ActivityLog(date: day, activityID: "sleep",
                                               amount: short ? 6.0 : 8.0, source: .healthKit, ownerID: owner))
                // Overnight wrist temperature runs a touch higher after short sleep too.
                env.context.insert(HealthSample(typeID: "wristTemperature", day: day,
                                                value: short ? 35.6 : 35.1, unit: "°C", source: .healthKit, ownerID: owner))
            }
            // Weight + blood pressure are measured occasionally, not daily.
            for i in stride(from: 0, to: 16, by: 4) {
                env.context.insert(HealthSample(typeID: "bodyMass", day: today.adding(days: -i),
                                                value: 68.0 + Double(i) * 0.1, unit: "kg", source: .healthKit, ownerID: owner))
                env.context.insert(HealthSample(typeID: "bloodPressureSystolic", day: today.adding(days: -i),
                                                value: 120 + Double(i), unit: "mmHg", source: .healthKit, ownerID: owner))
                env.context.insert(HealthSample(typeID: "bloodPressureDiastolic", day: today.adding(days: -i),
                                                value: 78 + Double(i % 3), unit: "mmHg", source: .healthKit, ownerID: owner))
            }
            try? env.context.save()
        }

        if args.contains("-uitSeedSymptoms") {
            // Hot flushes on check-in days + night sweats logged ONLY in Apple Health
            // (no check-in those days), to prove the report/patterns merge both.
            let owner = env.auth.ownerID
            let today = Date().startOfDay
            let hot = env.symptoms.allActive().first { $0.name == "Hot flushes" }
            for i in [0, 1, 3, 5] {
                let ci = CheckIn(date: today.adding(days: -i), mood: .okay, energy: 55, ownerID: owner)
                env.context.insert(ci)
                if let hot { env.context.insert(CheckInSymptom(checkIn: ci, symptom: hot, severity: 2, ownerID: owner)) }
            }
            for i in [2, 4] {
                env.context.insert(HealthSample(typeID: "symptom.night_sweats", day: today.adding(days: -i),
                                                value: 2, unit: "severity", source: .healthKit, ownerID: owner))
            }
            try? env.context.save()
        }

        if args.contains("-uitSeedEating") {
            // Alcohol logged yes on hot-flush days and no on clear days → a strong
            // trigger correlation; plus a couple of today's answers so the panel shows
            // mixed yes/no/blank state on the sim.
            let owner = env.auth.ownerID
            let today = Date().startOfDay
            let hot = env.symptoms.allActive().first { $0.name == "Hot flushes" }
            for i in [1, 3, 5, 7] {
                let day = today.adding(days: -i)
                env.context.insert(ActivityLog(date: day, activityID: "eat.alcohol", amount: 1, ownerID: owner))
                let ci = CheckIn(date: day, mood: .okay, energy: 55, ownerID: owner)
                env.context.insert(ci)
                if let hot { env.context.insert(CheckInSymptom(checkIn: ci, symptom: hot, severity: 2, ownerID: owner)) }
            }
            for i in [10, 12, 14, 16] {
                env.context.insert(ActivityLog(date: today.adding(days: -i), activityID: "eat.alcohol", amount: 0, ownerID: owner))
                env.context.insert(CheckIn(date: today.adding(days: -i), mood: .good, energy: 70, ownerID: owner))
            }
            env.context.insert(ActivityLog(date: today, activityID: "eat.protein", amount: 1, ownerID: owner))
            env.context.insert(ActivityLog(date: today, activityID: "eat.caffeine", amount: 0, ownerID: owner))
            try? env.context.save()
        }

        if args.contains("-uitSeedProfile") {
            // A local (non-Apple) profile with basic details filled, so the Profile
            // screen's edit fields render populated and the "Create an account" upgrade
            // path is visible on the sim (Sign in with Apple can't run unsigned).
            env.users.updateBasicInfo(firstName: "Mischa", lastName: "Reed",
                                      birthYear: 1977, mobile: "0400 000 000",
                                      email: "mischa@example.com")
        }

        if args.contains("-uitSeedCycle") {
            // Regular-ish recent cycles (28, 29, 27 days) plus a current period, so
            // the timeline shows a cycle in progress and a next-period estimate.
            let today = Date().startOfDay
            let owner = env.auth.ownerID
            // Past period starts (single days is enough to define a start).
            for off in [-84, -56, -27] {
                env.context.insert(CycleEntry(date: today.adding(days: off), type: .periodStart,
                                              flowLevel: .medium, ownerID: owner))
            }
            // A current period, days 1-4 of this cycle, with varying flow.
            let flows: [FlowLevel] = [.heavy, .medium, .light, .spotting]
            for (i, level) in flows.enumerated() {
                env.context.insert(CycleEntry(date: today.adding(days: -3 + i), type: .periodStart,
                                              flowLevel: level, ownerID: owner))
            }
            try? env.context.save()
        }

        if args.contains("-uitSeedPumpMed") {
            // A gel dosed in pumps (the requested unit), to check it renders "2 pumps".
            let med = Medication(name: "Oestrogen gel", dosage: "2 pumps",
                                 doseAmount: 2, doseUnit: .pumps, timing: "daily", method: .gel,
                                 kind: .treatment,
                                 schedule: DoseSchedule(kind: .weekly, slots: [DoseSlot(hour: 8, minute: 0)]),
                                 ownerID: env.auth.ownerID)
            env.context.insert(med)
            try? env.context.save()
        }

        if args.contains("-uitSeedAutoLogMed") {
            // The reported case: an auto-logged medicine whose tick is OFF. Before the
            // fix it never appeared on the home Medicines list; now it should.
            var tracked = TreatmentDraft(name: "Oestrogen gel", kind: .treatment)
            tracked.schedule.slots = [DoseSlot(hour: 8, minute: 0)]
            _ = env.medications.add(tracked)

            var auto = TreatmentDraft(name: "Testosterone cream or gel", kind: .treatment)
            auto.schedule.slots = [DoseSlot(hour: 8, minute: 0)]
            let med = env.medications.add(auto)
            med.isTracked = false     // tick off
            med.autoLogDoses = true   // but set to auto-log
            try? env.context.save()
            env.autoLogTodaysDoses()  // record today's dose, as auto-log does
        }

        if args.contains("-uitSeedMed") {
            for (name, amount, unit) in [("Magnesium", 400.0, DoseUnit.mg), ("Vitamin D", 2000.0, .iu)] {
                var draft = TreatmentDraft(name: name, kind: .supplement)
                draft.doseAmount = amount
                draft.doseUnit = unit
                draft.schedule.slots = [DoseSlot(hour: 8, minute: 0)]
                _ = env.medications.add(draft)
            }
        }

        // One of each schedule shape, including two that aren't due today, so the
        // resting state on the list can be seen.
        if args.contains("-uitSeedSchedules") {
            let today = Calendar.current.component(.weekday, from: .now)
            let tomorrow = today % 7 + 1

            var daily = TreatmentDraft(name: "Magnesium", kind: .supplement)
            daily.doseAmount = 400; daily.doseUnit = .mg
            // Two doses a day, and a third only at weekends.
            daily.schedule.slots = [
                DoseSlot(hour: 8, minute: 0),
                DoseSlot(hour: 20, minute: 0),
                DoseSlot(weekdays: [1, 7], hour: 10, minute: 30),
            ]
            _ = env.medications.add(daily)

            var patch = TreatmentDraft(name: "Oestrogen patch", kind: .treatment,
                                       catalogGroupID: "oestrogen", method: .patch)
            patch.doseAmount = 50; patch.doseUnit = .mcg
            patch.schedule.slots = [DoseSlot(weekdays: [tomorrow])]
            _ = env.medications.add(patch)

            var cyclic = TreatmentDraft(name: "Prometrium", kind: .treatment,
                                        catalogGroupID: "progesterone", method: .capsule)
            cyclic.doseAmount = 100; cyclic.doseUnit = .mg
            cyclic.schedule.kind = .cycle
            cyclic.schedule.cycleLength = 28
            cyclic.schedule.pauseDays = 7
            // Anchored so today lands on day 24: mid-pause.
            cyclic.schedule.anchor = Calendar.current.date(byAdding: .day, value: -23,
                                                           to: Calendar.current.startOfDay(for: .now))
            _ = env.medications.add(cyclic)
        }

        if args.contains("-uitSeedWeek") {
            let cal = Calendar.current
            let energies = [45, 60, 52, 74, 58, 80, 66]
            let sleeps = [6.0, 7.5, 5.5, 8.0, 6.5, 7.0, 8.5]
            for i in 0..<7 {
                guard let day = cal.date(byAdding: .day, value: -(6 - i), to: .now) else { continue }
                let ci = CheckIn(date: day, mood: .okay, energy: energies[i], ownerID: env.auth.ownerID)
                env.context.insert(ci)
                env.context.insert(ActivityLog(date: day, activityID: "sleep", amount: sleeps[i], ownerID: env.auth.ownerID))
            }
            try? env.context.save()
        }

        if args.contains("-uitPrintCounts") {
            let checkIns = env.checkIns.all().count
            let meds = env.medications.active().count
            print("KEEL_UITEST checkIns=\(checkIns) meds=\(meds)")
            fflush(stdout)
        }

        if args.contains("-uitBackupRoundtrip") {
            runBackupRoundtrip(env: env)
        }

        if args.contains("-uitCompanionTools") {
            runCompanionToolsProbe(env: env)
        }

        if args.contains("-uitCompanionProposal") {
            runCompanionProposalProbe(env: env)
        }

        if args.contains("-uitGeminiLimiter") {
            runGeminiLimiterProbe()
        }

        if args.contains("-uitCompanionReply") {
            runCompanionReplyProbe(env: env)
        }

        if args.contains("-uitAIStatus") {
            printAIStatus()
        }

        if args.contains("-uitAppleReply") {
            runAppleReplyProbe(env: env)
        }

        if args.contains("-uitAppleAddEntry") {
            runAppleAddEntryProbe(env: env)
        }

        if args.contains("-uitOfflineAddEntry") {
            runOfflineAddEntryProbe(env: env)
        }

        if args.contains("-uitAppleBare") {
            runAppleBareProbe()
        }

        if args.contains("-uitGeminiReply") {
            runGeminiReplyProbe(env: env)
        }

        if args.contains("-uitEditCheckIn") {
            runEditCheckInProbe(env: env)
        }

        if args.contains("-uitPastEntry") {
            runPastEntryProbe(env: env)
        }

        if args.contains("-uitSkipSignup") {
            runSkipSignupProbe(env: env)
        }

        if args.contains("-uitReturningUser") {
            runReturningUserProbe(env: env)
        }

        if args.contains("-uitHealthIngest") {
            runHealthIngestProbe(env: env)
        }

        if args.contains("-uitInsights") {
            runInsightsProbe(env: env)
        }

        if args.contains("-uitDailySummary") {
            runDailySummaryProbe(env: env)
        }

        if args.contains("-uitGPSummary") {
            runGPSummaryProbe(env: env)
        }

        if args.contains("-uitHealthImport") {
            runHealthImportProbe(env: env)
        }

        if args.contains("-uitMedToggleTest") {
            runMedToggleTest(env: env)
        }

        if args.contains("-uitTrackDump") {
            let dump = env.medications.active().map { "\($0.name)=\($0.isTracked)" }
            print("KEEL_TRACKDUMP \(dump)")
            fflush(stdout)
        }

        if args.contains("-uitMedNotifTest") {
            runMedNotifTest(env: env)
        }

        if args.contains("-uitManySymptoms") {
            env.symptoms.syncBuiltIns()
            let picks = env.symptoms.allActive().prefix(12).map { (symptom: $0, severity: 2) }
            env.checkIns.create(mood: .okay, energy: 60, notes: nil, symptoms: Array(picks), date: .now)
        }

        if args.contains("-uitReminderDump") {
            runReminderDump(env: env)
        }

        if args.contains("-uitMedReminderProbe") {
            runMedReminderProbe(env: env)
        }

        if args.contains("-uitTipProbe") {
            runTipProbe(env: env)
        }

        if args.contains("-uitSeedSchema") {
            runSchemaSeed(env: env)
        }

        if args.contains("-uitMedCancelFlow") {
            runMedCancelFlow(env: env)
        }

        if args.contains("-uitSupportRegion") {
            let cur = CrisisResources.matching().map(\.name).joined(separator: ",")
            let forced = CrisisResources.matching(
                locale: Locale(identifier: "en_NZ"),
                timeZone: TimeZone(identifier: "Australia/Brisbane")!).map(\.name).joined(separator: ",")
            print("KEEL_SUPPORTREGION tz=\(TimeZone.current.identifier) region=\(Locale.current.region?.identifier ?? "nil") current=[\(cur)] enNZ+Brisbane=[\(forced)]")
            fflush(stdout)
        }
    }

    /// Schedules every lifestyle reminder, waits for any deferred async work, then
    /// dumps what's actually pending. The old async cancel would wipe the just-added
    /// hydration/movement/wind-down here (leaving 0); the sync fix keeps them.
    /// (Scheduling adds to the pending queue without needing notification auth.)
    @MainActor
    private static func runReminderDump(env: AppEnvironment) {
        let n = env.notifications
        n.scheduleDailyCheckInReminder(hour: 9, minute: 0)
        n.scheduleHydration(startHour: 8, endHour: 20, everyHours: 2)
        n.scheduleMovement(hour: 14, minute: 0, weekdaysOnly: true)
        n.scheduleWindDown(hour: 21, minute: 30)
        Task {
            try? await Task.sleep(for: .milliseconds(600)) // let any deferred cancel run
            let ids = await UNUserNotificationCenter.current().pendingNotificationRequests().map(\.identifier)
            func c(_ p: String) -> Int { ids.filter { $0.hasPrefix(p) }.count }
            print("KEEL_REMINDERDUMP total=\(ids.count) dailyCheckIn=\(c("keel.dailyCheckIn")) hydration=\(c("keel.hydration.")) movement=\(c("keel.movement.")) winddown=\(c("keel.winddown"))")
            fflush(stdout)
        }
    }

    /// Drives the REAL "log a med from home" path end to end and checks the
    /// reminder is gone: schedule a dose an hour out, log it via
    /// `toggleMedicationFromHome` (cancel-on-log), then reschedule as a later
    /// launch would (skip-on-reschedule). Expect pending 1 -> 0 -> 0.
    /// Launch WITHOUT -uitNoPrompt (that suppresses reminder scheduling).
    @MainActor
    private static func runMedCancelFlow(env: AppEnvironment) {
        let n = env.notifications
        let ctx = env.context
        env.settings.pushNotifications = true // refreshMedicationReminders early-returns otherwise
        (try? ctx.fetch(FetchDescriptor<MedicationLog>()))?.forEach { ctx.delete($0) }
        env.medications.active().forEach { env.medications.archive($0) }
        try? ctx.save()

        let cal = Calendar.current
        let future = cal.date(byAdding: .hour, value: 1, to: Date())!
        let h = cal.component(.hour, from: future), m = cal.component(.minute, from: future)
        let slot = DoseSlot(weekdays: Set(1...7), hour: h, minute: m)
        let sched = DoseSchedule(kind: .weekly, slots: [slot])
        let med = Medication(name: "CancelFlow", dosage: "1 tab", timing: "\(h):\(m)", isTracked: true,
                             schedule: sched, ownerID: "cancelflow", syncStatus: .synced)
        ctx.insert(med)
        try? ctx.save()
        let medID = med.id

        Task {
            _ = await n.requestAuthorization()
            // Count TODAY's occurrence specifically (id ends in .d<todayKey>), not
            // the whole horizon — future days are supposed to stay scheduled.
            let dc = Calendar.current.dateComponents([.year, .month, .day], from: Date())
            let suffix = String(format: ".d%04d%02d%02d", dc.year ?? 0, dc.month ?? 0, dc.day ?? 0)
            func todayForMed() async -> Int {
                let reqs = await UNUserNotificationCenter.current().pendingNotificationRequests()
                return reqs.filter { $0.identifier.hasPrefix("keel.med.\(medID.uuidString)") && $0.identifier.hasSuffix(suffix) }.count
            }
            env.refreshMedicationReminders()
            try? await Task.sleep(for: .milliseconds(2500))
            let scheduledToday = await todayForMed()

            env.toggleMedicationFromHome(med, on: Date(), currentlyTaken: false) // log it (home path)
            try? await Task.sleep(for: .milliseconds(2500))
            let afterLogToday = await todayForMed()

            env.refreshMedicationReminders() // a later launch's reschedule
            try? await Task.sleep(for: .milliseconds(2500))
            let afterRescheduleToday = await todayForMed()

            print("KEEL_MEDCANCEL today: scheduled=\(scheduledToday) afterLog=\(afterLogToday) afterReschedule=\(afterRescheduleToday) (expect 1, 0, 0)")
            fflush(stdout)
        }
    }

    /// Inserts one fully-populated, tombstoned record of every one of the 12
    /// entities, with relationships linked, so SwiftData's CloudKit mirroring
    /// creates every record type AND every field in the Development schema in a
    /// single signed run (an unpopulated field never appears, and Production can't
    /// JIT-create later). The records carry `deletedAt` (so they're invisible in
    /// the app and every entity's `deletedAt` field is created) and an
    /// `ownerID` of "schema-seed" so they're easy to find and delete afterwards.
    /// Only mirrors on a signed build signed into iCloud; a no-op to the schema
    /// otherwise (still writes locally, harmlessly).
    @MainActor
    private static func runSchemaSeed(env: AppEnvironment) {
        let ctx = env.context
        let now = Date()
        let owner = "schema-seed"

        // Catalog + a check-in that links to it (populates both relationship sides).
        let symptom = Symptom(name: "Schema seed", category: .body, isCustom: true,
                              isArchived: true, isDefaultChip: true, ownerID: owner,
                              createdAt: now, updatedAt: now, deletedAt: now, syncStatus: .synced)
        ctx.insert(symptom)

        let checkIn = CheckIn(date: now, mood: .okay, energy: 55, notes: "Schema seed",
                              ownerID: owner, createdAt: now, updatedAt: now, deletedAt: now, syncStatus: .synced)
        ctx.insert(checkIn)

        let link = CheckInSymptom(checkIn: checkIn, symptom: symptom, severity: 2, source: .healthKit,
                                  ownerID: owner, createdAt: now, updatedAt: now, deletedAt: now, syncStatus: .synced)
        ctx.insert(link)

        let cycle = CycleEntry(date: now, type: .periodStart, source: .healthKit, ownerID: owner,
                               createdAt: now, updatedAt: now, deletedAt: now, syncStatus: .synced)
        ctx.insert(cycle)

        // Medication with a cycle schedule (populates every schedule-derived field)
        // + a log linked to it.
        let slot = DoseSlot(weekdays: [2, 4, 6], hour: 8, minute: 30)
        let schedule = DoseSchedule(kind: .cycle, slots: [slot], cycleLength: 28, pauseDays: 7, anchor: now)
        let med = Medication(name: "Schema seed", dosage: "50 mg", doseAmount: 50, doseUnit: .mg,
                             timing: "8:30 am", method: .tablet, isActive: true, isTracked: true,
                             autoLogDoses: true, kind: .treatment, catalogGroupID: "seed", schedule: schedule,
                             date: now, doseChangedAt: now, note: "seed", isOffLabel: true, isCompounded: true,
                             ownerID: owner, createdAt: now, updatedAt: now, deletedAt: now, syncStatus: .synced)
        med.frequencyRaw = DoseFrequency.daily.rawValue   // legacy fields, not set by init
        med.timeOfDayRaw = TimeOfDay.morning.rawValue
        ctx.insert(med)

        let medLog = MedicationLog(date: now, slot: "seed-slot", taken: true, medication: med, ownerID: owner,
                                   createdAt: now, updatedAt: now, deletedAt: now, syncStatus: .synced)
        ctx.insert(medLog)

        let insight = Insight(title: "Schema seed", detail: "Seed detail", timeframe: "This week",
                              iconKey: "sparkles", accent: .terracotta, generatedAt: now, ownerID: owner,
                              createdAt: now, updatedAt: now, deletedAt: now, syncStatus: .synced)
        ctx.insert(insight)

        let activity = ActivityLog(date: now, activityID: "water", amount: 4, ownerID: owner,
                                   createdAt: now, updatedAt: now, deletedAt: now, syncStatus: .synced)
        ctx.insert(activity)

        let chat = ChatMessage(role: .user, text: "Schema seed", ownerID: owner,
                               createdAt: now, updatedAt: now, deletedAt: now, syncStatus: .synced)
        ctx.insert(chat)

        let daily = DailySummary(day: now, text: "Schema seed", source: .deterministic, signalsJSON: "[]",
                                 generatedAt: now, ownerID: owner, createdAt: now, updatedAt: now,
                                 deletedAt: now, syncStatus: .synced)
        ctx.insert(daily)

        let sample = HealthSample(typeID: "heartRate", day: now, value: 72, unit: "bpm", source: .healthKit,
                                  ownerID: owner, createdAt: now, updatedAt: now, deletedAt: now, syncStatus: .synced)
        ctx.insert(sample)

        let profile = UserProfile(firstName: "Schema seed", email: "seed@example.com",
                                  appleUserID: "seed-apple-id", pathway: .both, healthKitAuthorized: true,
                                  trackingStartDate: now, ownerID: owner, createdAt: now, updatedAt: now,
                                  deletedAt: now, syncStatus: .synced)
        profile.region = "AU"; profile.localeID = "en_AU"; profile.timeZoneID = "Australia/Sydney"
        profile.appVersion = "1.0.1"; profile.deviceModel = "Simulator"; profile.osVersion = "26.5"
        ctx.insert(profile)

        do {
            try ctx.save()
            let count = (try? ctx.fetchCount(FetchDescriptor<HealthSample>(predicate: #Predicate { $0.ownerID == owner }))) ?? -1
            print("KEEL_SCHEMASEED inserted one of each of \(KeelSchema.models.count) entities (tombstoned, owner=schema-seed). "
                + "On a signed build signed into iCloud these mirror to CloudKit Development and create every record type + field. sampleCheck=\(count)")
        } catch {
            print("KEEL_SCHEMASEED save error: \(error)")
        }
        fflush(stdout)
    }

    /// Verifies the lifestyle-tip plumbing: a passed tip is woven into the body
    /// (deterministic, no model needed), and the on-device writer returns nil on
    /// the simulator so the static copy stands. Live tip generation needs a real
    /// Apple-Intelligence device on iOS 26 and can't be exercised here.
    @MainActor
    private static func runTipProbe(env: AppEnvironment) {
        let n = env.notifications
        Task {
            _ = await n.requestAuthorization()
            n.scheduleHydration(startHour: 8, endHour: 8, everyHours: 2, tip: "Keep a glass by your desk.")
            n.scheduleMovement(hour: 14, minute: 0, weekdaysOnly: false, tip: "Try a slow lap of the garden.")
            n.scheduleWindDown(hour: 21, minute: 30, tip: "Dim the lights an hour before bed.")
            try? await Task.sleep(for: .milliseconds(500))
            let reqs = await UNUserNotificationCenter.current().pendingNotificationRequests()
            func body(_ prefix: String) -> String {
                reqs.first { $0.identifier.hasPrefix(prefix) }?.content.body ?? "nil"
            }
            let modelTip = await LifestyleTipWriter.tip(for: .hydration) // nil on the simulator
            print("KEEL_TIPPROBE hydration='\(body("keel.hydration."))'")
            print("KEEL_TIPPROBE movement='\(body("keel.movement."))'")
            print("KEEL_TIPPROBE winddown='\(body("keel.winddown"))' modelTipOnSim=\(modelTip ?? "nil")")
            fflush(stdout)
        }
    }

    /// Drives the real medication scheduler for three cases and reports what
    /// actually lands in the pending queue: an auto-log dose (informational
    /// wording + no-action category), a manual dose (tap-to-log category), and a
    /// dose already logged today (skipped). Then cancels the manual one for the
    /// day and confirms it's gone. A dose one hour out keeps every occurrence in
    /// the future so nothing is dropped for being past.
    @MainActor
    private static func runMedReminderProbe(env: AppEnvironment) {
        let n = env.notifications
        let cal = Calendar.current
        let now = Date()
        let future = cal.date(byAdding: .hour, value: 1, to: now)!
        let h = cal.component(.hour, from: future), m = cal.component(.minute, from: future)
        let slot = DoseSlot(hour: h, minute: m)
        let sched = DoseSchedule(kind: .weekly, slots: [slot])
        let autoID = UUID(), manualID = UUID(), loggedID = UUID()

        Task {
            // The simulator only surfaces pending requests once auth is granted.
            _ = await n.requestAuthorization()
            // Small horizon so three meds stay well under iOS's 64-pending cap.
            await n.rescheduleMedication(id: autoID, name: "AutoMed", schedule: sched, autoLog: true, cycleHorizon: 3)
            await n.rescheduleMedication(id: manualID, name: "ManualMed", schedule: sched, autoLog: false, cycleHorizon: 3)
            await n.rescheduleMedication(id: loggedID, name: "LoggedMed", schedule: sched, autoLog: false,
                                         cycleHorizon: 3, loggedTodaySlots: [slot.id.uuidString])
            try? await Task.sleep(for: .milliseconds(500)) // let center.add register
            let todaySuffix = ".d\(NotificationService.dayKey(now))"
            var reqs = await UNUserNotificationCenter.current().pendingNotificationRequests()
            func todayReq(_ id: UUID) -> UNNotificationRequest? {
                reqs.first { $0.identifier.hasPrefix("keel.med.\(id.uuidString)") && $0.identifier.hasSuffix(todaySuffix) }
            }
            func hasAny(_ id: UUID) -> Bool { reqs.contains { $0.identifier.hasPrefix("keel.med.\(id.uuidString)") } }
            let a = todayReq(autoID), man = todayReq(manualID), lg = todayReq(loggedID)
            print("KEEL_MEDREMINDER autoTodayCat=\(a?.content.categoryIdentifier ?? "nil") "
                + "autoBody='\(a?.content.body ?? "nil")' manualTodayCat=\(man?.content.categoryIdentifier ?? "nil") "
                + "manualBody='\(man?.content.body ?? "nil")' loggedTodaySkipped=\(lg == nil) loggedHasFuture=\(hasAny(loggedID))")

            await n.cancelMedicationReminders(medicationID: manualID, on: now, slot: slot.id.uuidString)
            try? await Task.sleep(for: .milliseconds(300))
            reqs = await UNUserNotificationCenter.current().pendingNotificationRequests()
            print("KEEL_MEDREMINDER afterCancel manualTodayGone=\(todayReq(manualID) == nil) "
                + "manualFutureKept=\(hasAny(manualID)) autoTodayKept=\(todayReq(autoID) != nil)")
            fflush(stdout)
        }
    }

    /// Exercises the medication-reminder handlers the notification delegate calls:
    /// `markMedicationTaken` (the "Mark taken" tap) and `autoLogTodaysDueDoses`
    /// (auto-log on app open). Proves the reminder actually logs a dose.
    @MainActor
    private static func runMedNotifTest(env: AppEnvironment) {
        func wipeLogs() {
            (try? env.context.fetch(FetchDescriptor<MedicationLog>()))?.forEach { env.context.delete($0) }
            try? env.context.save()
        }
        guard let med = env.medications.active().first(where: { $0.hasSchedule })
            ?? env.medications.active().first else {
            print("KEEL_MEDNOTIF no-med"); fflush(stdout); return
        }
        // 1) The "Mark taken" tap → a whole-day log for today.
        wipeLogs()
        env.markMedicationTaken(medicationID: med.id, slot: nil, on: .now)
        let markTaken = env.medications.isTaken(med, on: .now, slot: nil)
        // 2) Auto-log: enable it and run today's fill; expect ≥1 past-due timed dose.
        wipeLogs()
        env.medications.setAutoLog(med, true)
        let autoLogged = env.medications.autoLogTodaysDueDoses().count
        print("KEEL_MEDNOTIF med='\(med.name)' markTaken=\(markTaken) autoLogged=\(autoLogged) flag=\(med.autoLogDoses)")
        fflush(stdout)
    }

    /// Replicates the dashboard `toggleMed` for a scheduled med on TODAY, then
    /// reports whether YESTERDAY was affected. Proves per-day independence.
    @MainActor
    private static func runMedToggleTest(env: AppEnvironment) {
        (try? env.context.fetch(FetchDescriptor<MedicationLog>()))?.forEach { env.context.delete($0) }
        try? env.context.save()

        let med = env.medications.active().first { $0.hasSchedule }
            ?? env.medications.active().first
            ?? env.medications.add(name: "Vitamin D", dosage: "2000 IU", timing: "Every day", method: nil)

        func anyTaken(on day: Date) -> Bool {
            let start = day.startOfDay, end = start.adding(days: 1)
            let d = FetchDescriptor<MedicationLog>(predicate: #Predicate { log in
                log.deletedAt == nil && log.taken && log.date >= start && log.date < end
            })
            return ((try? env.context.fetchCount(d)) ?? 0) > 0
        }

        let today = Date.now, yest = Date.now.adding(days: -1)
        let slots = med.schedule.dueSlots(on: today)
        if slots.isEmpty {
            env.medications.setTaken(med, on: today, slot: nil, taken: true)
        } else {
            for s in slots { env.medications.setTaken(med, on: today, slot: s.id.uuidString, taken: true) }
        }
        let logs = ((try? env.context.fetch(FetchDescriptor<MedicationLog>())) ?? [])
            .map { "\($0.date.formatted(.dateTime.month().day())):slot=\($0.slot ?? "nil"):taken=\($0.taken)" }
        print("KEEL_MEDTOGGLE med='\(med.name)' hasSchedule=\(med.hasSchedule) slotsToday=\(slots.count) afterTickToday today=\(anyTaken(on: today)) yesterday=\(anyTaken(on: yest)) logs=\(logs)")

        // Now exercise the home-log untick: clearTaken should wipe every slot for
        // the day, leaving no non-deleted logs, so it reads untaken everywhere.
        env.medications.clearTaken(med, on: today)
        let liveAfterClear = ((try? env.context.fetch(FetchDescriptor<MedicationLog>(
            predicate: #Predicate { $0.deletedAt == nil }))) ?? []).count
        print("KEEL_MEDCLEAR afterUntick today=\(anyTaken(on: today)) liveLogs=\(liveAfterClear)")
        fflush(stdout)
    }

    /// Drives the broad Apple Health merge with a synthetic snapshot (HealthKit
    /// itself needs a signed build), checking: activity backfill, vitals →
    /// HealthSample, a symptom attaching to an existing check-in (tagged
    /// healthKit), a symptom on a check-in-less day archiving to HealthSample,
    /// menstrual flow → a cycle entry, and that a second run adds nothing.
    @MainActor
    private static func runHealthImportProbe(env: AppEnvironment) {
        env.symptoms.syncBuiltIns()
        (try? env.context.fetch(FetchDescriptor<CheckIn>()))?.forEach { env.context.delete($0) }
        (try? env.context.fetch(FetchDescriptor<ActivityLog>()))?.forEach { env.context.delete($0) }
        (try? env.context.fetch(FetchDescriptor<CheckInSymptom>()))?.forEach { env.context.delete($0) }
        (try? env.context.fetch(FetchDescriptor<CycleEntry>()))?.forEach { env.context.delete($0) }
        (try? env.context.fetch(FetchDescriptor<HealthSample>()))?.forEach { env.context.delete($0) }
        try? env.context.save()

        let owner = env.auth.ownerID
        let today = Date().startOfDay
        let noCheckInDay = today.adding(days: -3)
        // An existing check-in today, so a Health symptom has something to attach to.
        env.context.insert(CheckIn(date: today, mood: .okay, energy: 60, ownerID: owner))
        try? env.context.save()

        var snap = HealthSnapshot()
        snap.sleepByDay = [today: 7.4, today.adding(days: -1): 6.2]
        snap.activityAmounts = [
            "steps": [today: 8200],
            "exercise": [today: 35],
            "meditation": [today.adding(days: -1): 12],
        ]
        snap.vitals = [
            .init(typeID: "heartRate", unit: "bpm", byDay: [today: 68]),
            .init(typeID: "hrv", unit: "ms", byDay: [today: 42]),
            .init(typeID: "activeEnergy", unit: "kcal", byDay: [today: 430]),
        ]
        snap.symptoms = [
            .init(day: today, hkIdentifier: HKCategoryTypeIdentifier.hotFlashes.rawValue, severity: 2),
            .init(day: today, hkIdentifier: HKCategoryTypeIdentifier.moodChanges.rawValue, severity: 1),
            .init(day: noCheckInDay, hkIdentifier: HKCategoryTypeIdentifier.nightSweats.rawValue, severity: 3),
        ]
        snap.menstrualFlow = [today.adding(days: -2): .medium]

        env.ingestHealthSnapshot(snap)
        let afterFirst = healthRowTotals(env)
        env.ingestHealthSnapshot(snap) // idempotency
        let afterSecond = healthRowTotals(env)

        // Changed Health data should REFRESH (steps updated); sleep stays backfill-only.
        var changed = snap
        changed.activityAmounts["steps"] = [today: 12000]
        changed.sleepByDay = [today: 9.9] // must NOT overwrite the existing 7.4
        env.ingestHealthSnapshot(changed)
        func amount(_ id: String, _ day: Date) -> Double? {
            (try? env.context.fetch(FetchDescriptor<ActivityLog>()))?
                .first { $0.deletedAt == nil && $0.activityID == id && $0.date.isSameDay(as: day) }?.amount
        }
        let stepsUpdated = amount("steps", today) == 12000
        let sleepPreserved = amount("sleep", today) == 7.4

        let links = (try? env.context.fetch(FetchDescriptor<CheckInSymptom>())) ?? []
        let hkLinks = links.filter { $0.source == .healthKit }
        let cycles = (try? env.context.fetch(FetchDescriptor<CycleEntry>())) ?? []
        let hkCycles = cycles.filter { $0.source == .healthKit }
        let samples = (try? env.context.fetch(FetchDescriptor<HealthSample>())) ?? []
        let archived = samples.filter { $0.typeID.hasPrefix("symptom.") }
        let todayCheckIn = (try? env.context.fetch(FetchDescriptor<CheckIn>()))?.first { $0.date.isSameDay(as: today) }
        let hotFlushesTagged = (todayCheckIn?.symptomLinks ?? []).contains {
            $0.source == .healthKit && $0.symptom?.name == "Hot flushes"
        }

        print("KEEL_HKIMPORT activityLogs=\(afterFirst.activity) healthSamples=\(afterFirst.samples) hkSymptomLinks=\(hkLinks.count) hkCycleEntries=\(hkCycles.count) archivedSymptomSamples=\(archived.count) todayHotFlushesTagged=\(hotFlushesTagged) secondRunAddedNothing=\(afterFirst == afterSecond) stepsUpdated=\(stepsUpdated) sleepPreserved=\(sleepPreserved)")
        fflush(stdout)
    }

    @MainActor
    private static func healthRowTotals(_ env: AppEnvironment) -> (activity: Int, samples: Int, links: Int, cycles: Int) {
        (
            (try? env.context.fetchCount(FetchDescriptor<ActivityLog>())) ?? -1,
            (try? env.context.fetchCount(FetchDescriptor<HealthSample>())) ?? -1,
            (try? env.context.fetchCount(FetchDescriptor<CheckInSymptom>())) ?? -1,
            (try? env.context.fetchCount(FetchDescriptor<CycleEntry>())) ?? -1
        )
    }

    /// Seeds a clear sleep↔energy pattern and a recurring symptom, then derives
    /// insights and prints them, so the real (non-mock) derivation can be checked.
    /// Seeds a realistic 12-week picture, renders the GP Visit Summary PDF to a known
    /// path, and prints the path, overflow status and whether any author/creator/
    /// producer metadata survives. Read the PDF off the sim to check the layout.
    @MainActor
    private static func runGPSummaryProbe(env: AppEnvironment) {
        env.symptoms.syncBuiltIns()
        (try? env.context.fetch(FetchDescriptor<CheckIn>()))?.forEach { env.context.delete($0) }
        (try? env.context.fetch(FetchDescriptor<CheckInSymptom>()))?.forEach { env.context.delete($0) }
        (try? env.context.fetch(FetchDescriptor<CycleEntry>()))?.forEach { env.context.delete($0) }
        (try? env.context.fetch(FetchDescriptor<Medication>()))?.forEach { env.context.delete($0) }
        (try? env.context.fetch(FetchDescriptor<MedicationLog>()))?.forEach { env.context.delete($0) }
        try? env.context.save()
        let owner = env.auth.ownerID
        let names = ["Trouble sleeping", "Hot flushes", "Anxious", "Brain fog", "Joint pain",
                     "Night sweats", "Irritable", "Low mood"]
        let picks = names.compactMap { n in env.symptoms.allActive().first { $0.name == n } }
        for i in 0..<84 where i % 2 == 0 {
            let day = Date().startOfDay.adding(days: -i)
            let mood: Mood = [.okay, .good, .low, .difficult][i / 2 % 4]
            let chosen = picks.prefix((i / 2 % 5) + 1).map { (symptom: $0, severity: (i % 3) + 1) }
            env.checkIns.create(mood: mood, energy: 20 + (i % 5) * 20, notes: nil,
                                symptoms: Array(chosen), date: day)
        }
        // Cycle: two period runs and a standalone spotting day.
        for k in [70, 69, 68, 42, 41, 40] { env.cycle.togglePeriodDay(Date().startOfDay.adding(days: -k)) }
        env.cycle.setFlow(.spotting, on: Date().startOfDay.adding(days: -20))
        // Treatment.
        let oestrogel = Medication(name: "Oestrogel", dosage: "2 pumps", timing: "Morning",
                                   kind: .treatment, catalogGroupID: "oestrogen",
                                   date: Date().adding(days: -200), doseChangedAt: Date().adding(days: -14), ownerID: owner)
        let mag = Medication(name: "Magnesium glycinate", dosage: "400mg", timing: "Night", kind: .supplement, ownerID: owner)
        let sert = Medication(name: "Sertraline", dosage: "50mg", timing: "Morning", kind: .treatment, ownerID: owner)
        // A stopped MHT (shows in the MHT table with "Stopped [date]") and a stopped
        // supplement (contributes a "stopped X" change but no supplement row).
        let stoppedPatch = Medication(name: "Estradot patch", dosage: "50mcg", timing: "",
                                      isActive: false, kind: .treatment, catalogGroupID: "oestrogen",
                                      stoppedAt: Date().adding(days: -21), ownerID: owner)
        let stoppedSup = Medication(name: "Evening primrose oil", dosage: "1000mg", timing: "",
                                    isActive: false, kind: .supplement, stoppedAt: Date().adding(days: -30), ownerID: owner)
        [oestrogel, mag, sert, stoppedPatch, stoppedSup].forEach { env.context.insert($0) }
        env.users.updateBasicInfo(firstName: "Mischa", lastName: nil, birthYear: 1977, mobile: nil, email: nil)
        try? env.context.save()

        var inputs = GPSummaryInputs()
        inputs.period = .twelveWeeks
        inputs.includeName = true
        inputs.includeAge = true
        inputs.priorities = ["Get on top of the night sweats", "Talk through mood dips", "Sleep"]
        inputs.impactAreas = ["Sleep", "Work or concentration", "Emotional wellbeing"]
        inputs.impactOverall = "Significant"
        inputs.questions = ["Should I adjust my MHT dose?", "Is a blood test worth doing?"]

        let service = GPSummaryService(context: env.context, checkIns: env.checkIns,
                                       medications: env.medications, cycle: env.cycle,
                                       users: env.users)
        let document = service.makeDocument(inputs: inputs)
        let renderer = GPSummaryPDFRenderer(document: document)
        let data = renderer.render()
        let url = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("keel-gp-summary.pdf")
        try? data.write(to: url)

        var author = "", creator = "", producer = ""
        if let pdf = PDFDocument(data: data) {
            let a = pdf.documentAttributes ?? [:]
            author = (a[PDFDocumentAttribute.authorAttribute] as? String) ?? ""
            creator = (a[PDFDocumentAttribute.creatorAttribute] as? String) ?? ""
            producer = (a[PDFDocumentAttribute.producerAttribute] as? String) ?? ""
        }
        print("KEEL_GPSUMMARY path=\(url.path) bytes=\(data.count) pageOneOverflow=\(renderer.pageOneOverflowed) author='\(author)' creator='\(creator)' producer='\(producer)'")
        fflush(stdout)
    }

    @MainActor
    private static func runInsightsProbe(env: AppEnvironment) {
        env.symptoms.syncBuiltIns()
        // Clean slate: -uitReset doesn't clear ActivityLogs, so wipe check-in data
        // so leftover sleep rows from earlier probes can't skew the test.
        (try? env.context.fetch(FetchDescriptor<CheckIn>()))?.forEach { env.context.delete($0) }
        (try? env.context.fetch(FetchDescriptor<ActivityLog>()))?.forEach { env.context.delete($0) }
        (try? env.context.fetch(FetchDescriptor<CheckInSymptom>()))?.forEach { env.context.delete($0) }
        try? env.context.save()
        let owner = env.auth.ownerID
        let symptom = env.symptoms.allActive().first { $0.name == "Hot flushes" } ?? env.symptoms.allActive().first
        for i in 0..<8 {
            let day = Date().startOfDay.adding(days: -i)
            let lowSleep = i % 2 == 0
            let checkIn = CheckIn(date: day, mood: .okay, energy: lowSleep ? 40 : 75, ownerID: owner)
            env.context.insert(checkIn)
            env.context.insert(ActivityLog(date: day, activityID: "sleep", amount: lowSleep ? 5.5 : 8.0, ownerID: owner))
            if let symptom, i < 4 {
                env.context.insert(CheckInSymptom(checkIn: checkIn, symptom: symptom, severity: 2, ownerID: owner))
            }
        }
        try? env.context.save()
        env.insights.refreshDerived()
        for insight in env.insights.all() {
            print("KEEL_INSIGHT title='\(insight.title)' when='\(insight.timeframe)' detail='\(insight.detail.prefix(90))'")
        }
        fflush(stdout)
    }

    /// Seeds data that trips all five `PatternEngine` detectors, generates the
    /// daily summary (deterministic on the sim; Apple Intelligence narrates on a
    /// capable OS), and prints the findings, source, stored text, and history
    /// count. Also seeds a prior day so the "looking back" history has content.
    @MainActor
    private static func runDailySummaryProbe(env: AppEnvironment) {
        env.symptoms.syncBuiltIns()
        // Clean slate: -uitReset leaves ActivityLogs/cycle behind, so wipe every
        // signal so leftover rows can't skew the detectors.
        (try? env.context.fetch(FetchDescriptor<CheckIn>()))?.forEach { env.context.delete($0) }
        (try? env.context.fetch(FetchDescriptor<ActivityLog>()))?.forEach { env.context.delete($0) }
        (try? env.context.fetch(FetchDescriptor<CheckInSymptom>()))?.forEach { env.context.delete($0) }
        (try? env.context.fetch(FetchDescriptor<CycleEntry>()))?.forEach { env.context.delete($0) }
        (try? env.context.fetch(FetchDescriptor<DailySummary>()))?.forEach { env.context.delete($0) }
        (try? env.context.fetch(FetchDescriptor<HealthSample>()))?.forEach { env.context.delete($0) }
        try? env.context.save()

        let owner = env.auth.ownerID
        let today = Date().startOfDay
        let hot = env.symptoms.allActive().first { $0.name == "Hot flushes" } ?? env.symptoms.allActive().first

        // Period starts with a >7-day swing in interval (35, 25, 25) → variability.
        for offset in [-105, -70, -45, -20] {
            env.context.insert(CycleEntry(date: today.adding(days: offset), type: .periodStart, ownerID: owner))
        }
        // Recent symptom-free check-ins, alternating sleep → sleep↔energy link, with
        // resting HR running higher on the short-sleep days → sleep↔resting-HR link.
        for i in 0..<10 {
            let day = today.adding(days: -i)
            let low = i % 2 == 1
            env.context.insert(CheckIn(date: day, mood: low ? .low : .good, energy: low ? 40 : 75, ownerID: owner))
            env.context.insert(ActivityLog(date: day, activityID: "sleep", amount: low ? 5.5 : 8.0, ownerID: owner))
            env.context.insert(HealthSample(typeID: "restingHeartRate", day: day,
                                            value: low ? 66 : 60, unit: "bpm", source: .healthKit, ownerID: owner))
            env.context.insert(HealthSample(typeID: "wristTemperature", day: day,
                                            value: low ? 35.6 : 35.1, unit: "°C", source: .healthKit, ownerID: owner))
        }
        // Symptom days sitting inside the 7-day pre-period windows → premenstrual
        // clustering; the three most recent also make hot flushes the top symptom.
        for offset in [-26, -24, -22, -51, -49, -47, -76, -74, -110, -108] {
            let ci = CheckIn(date: today.adding(days: offset), mood: .okay, energy: 55, ownerID: owner)
            env.context.insert(ci)
            if let hot { env.context.insert(CheckInSymptom(checkIn: ci, symptom: hot, severity: 2, ownerID: owner)) }
        }
        // A prior day's reflection so the history section has something to show.
        env.context.insert(DailySummary(day: today.adding(days: -1),
                                        text: "Yesterday was steady. Nothing in particular stood out.",
                                        source: .deterministic, ownerID: owner, syncStatus: .synced))
        try? env.context.save()
        // Re-derive the insight cards from the fresh seed too (bootstrap derived
        // them from whatever the store held before this probe's wipe).
        env.insights.refreshDerived()

        Task { @MainActor in
            await env.dailySummary.regenerate()
            let kinds = PatternEngine.build(context: env.context).findings().map { $0.kind.rawValue }.joined(separator: ",")
            let stored = env.dailySummary.today()
            print("KEEL_DAILY findings=[\(kinds)] source=\(stored?.source.rawValue ?? "nil") history=\(env.dailySummary.history().count)")
            print("KEEL_DAILY_TEXT \((stored?.text ?? "nil").replacingOccurrences(of: "\n", with: " "))")
            fflush(stdout)
        }
    }

    /// Drives the sleep-ingestion path with synthetic samples (HealthKit itself
    /// needs a signed build), confirming a manual entry is preserved and empty
    /// days are backfilled.
    @MainActor
    private static func runHealthIngestProbe(env: AppEnvironment) {
        let today = Date().startOfDay
        let yesterday = today.adding(days: -1)
        let twoAgo = today.adding(days: -2)
        // A manual entry for today that ingestion must not overwrite.
        env.context.insert(ActivityLog(date: today, activityID: "sleep", amount: 7.0, ownerID: env.auth.ownerID))
        try? env.context.save()

        env.ingestSleepSamples([today: 8.0, yesterday: 6.5, twoAgo: 7.2])

        let sleep: (Date) -> Double? = { d in
            let s = d.startOfDay
            let desc = FetchDescriptor<ActivityLog>(
                predicate: #Predicate { $0.deletedAt == nil && $0.activityID == "sleep" && $0.date == s })
            return (try? env.context.fetch(desc))?.first?.amount
        }
        print("KEEL_HEALTH todayPreserved=\(sleep(today) ?? -1) yesterdayBackfilled=\(sleep(yesterday) ?? -1) twoAgoBackfilled=\(sleep(twoAgo) ?? -1)")
        fflush(stdout)
    }

    /// Establishes an identity + marks onboarding done, then reads it back through
    /// a fresh AuthService to confirm a returning user is restored (and would skip
    /// onboarding). Keychain columns are device-only; UserDefaults proves the logic.
    @MainActor
    private static func runReturningUserProbe(env: AppEnvironment) {
        env.auth.continueLocally()
        env.auth.markOnboarded()
        let fresh = AuthService()
        print("KEEL_RETURNING ownerRestored=\(!fresh.ownerID.isEmpty) onboardedRestored=\(fresh.hasCompletedOnboarding) keychainOwner=\(Keychain.string(for: "keel.ownerID") != nil) keychainOnboarded=\(Keychain.string(for: "keel.hasOnboarded") != nil)")
        fflush(stdout)
    }

    /// Simulates "continue without an account": confirms no personal data is
    /// stored, a stable Keychain-backed owner id exists, and the non-identifying
    /// context is captured on the profile.
    @MainActor
    private static func runSkipSignupProbe(env: AppEnvironment) {
        let profile = env.users.upsertProfile(firstName: "there", email: nil, appleUserID: nil)
        let owner = env.auth.ownerID
        let inKeychain = Keychain.string(for: "keel.ownerID") == owner && !owner.isEmpty
        print("KEEL_SKIP email=\(profile.email ?? "nil") appleID=\(profile.appleUserID ?? "nil") ownerPrefix=\(owner.prefix(6)) ownerInKeychain=\(inKeychain) keychain[\(Keychain.diagnose())] region=\(profile.region ?? "nil") locale=\(profile.localeID ?? "nil") tz=\(profile.timeZoneID ?? "nil") appVer=\(profile.appVersion ?? "nil") model=\(profile.deviceModel ?? "nil") os=\(profile.osVersion ?? "nil")")
        fflush(stdout)
    }

    /// Backfills an entry three days ago and confirms it lands on that day (not
    /// today), so adding to a past day works.
    @MainActor
    private static func runPastEntryProbe(env: AppEnvironment) {
        let target = Date.now.startOfDay.adding(days: -3)
        let before = env.checkIns.all().count
        let created = env.checkIns.create(mood: .good, energy: 70, notes: "Backfilled entry",
                                          symptoms: [], date: target)
        print("KEEL_PASTENTRY countBefore=\(before) countAfter=\(env.checkIns.all().count) onTargetDay=\(created.date.isSameDay(as: target)) distinctFromToday=\(!created.date.isSameDay(as: .now))")
        fflush(stdout)
    }

    /// Edits today's entry in place and prints before/after, so we can confirm an
    /// edit updates the same row (no duplicate) and reconciles its symptoms.
    @MainActor
    private static func runEditCheckInProbe(env: AppEnvironment) {
        env.symptoms.syncBuiltIns()
        let catalog = env.symptoms.allActive()
        if env.checkIns.todays() == nil {
            let picks = Array(catalog.prefix(2)).map { (symptom: $0, severity: 2) }
            env.checkIns.create(mood: .okay, energy: 60, notes: "Original note", symptoms: picks)
        }
        guard let entry = env.checkIns.todays() else { print("KEEL_EDIT no_entry"); fflush(stdout); return }
        let countBefore = env.checkIns.all().count
        let noteBefore = entry.notes ?? ""
        let symBefore = entry.symptoms.count
        let entryID = entry.id

        // Change notes + energy, and swap the symptom set to a single new one.
        let newPick = catalog.last.map { [(symptom: $0, severity: 3)] } ?? []
        env.checkIns.update(entry, mood: .good, energy: 80, notes: "Edited note", symptoms: newPick)

        let after = env.checkIns.todays()
        print("KEEL_EDIT countBefore=\(countBefore) countAfter=\(env.checkIns.all().count) sameID=\(after?.id == entryID) noteBefore='\(noteBefore)' noteAfter='\(after?.notes ?? "")' symBefore=\(symBefore) symAfter=\(after?.symptoms.count ?? -1) energyAfter=\(after?.energy ?? -1)")
        fflush(stdout)
    }

    /// Streams directly from the Gemini engine (reading the proxy URL from the
    /// launch env), so we can verify the real cloud path end to end, including the
    /// tool-calling loop. The prompt nudges it to consult her data.
    @MainActor
    private static func runGeminiReplyProbe(env: AppEnvironment) {
        let vars = ProcessInfo.processInfo.environment
        guard let urlString = vars["KEEL_GEMINI_BASE_URL"], let url = URL(string: urlString) else {
            print("KEEL_GEMINI no_base_url (set SIMCTL_CHILD_KEEL_GEMINI_BASE_URL)")
            fflush(stdout)
            return
        }
        let engine = GeminiChatEngine(baseURL: url, apiKey: vars["KEEL_GEMINI_API_KEY"],
                                      model: "gemini-2.5-flash", toolbox: makeToolbox(env: env),
                                      limiter: GeminiRateLimiter())
        let history = [ChatTurn(role: .user,
                                text: "I've been waking at 3am feeling wired. Can you look at my recent sleep and check-ins and tell me if you notice anything?")]
        Task { @MainActor in
            var reply = ""
            do {
                for try await delta in engine.streamReply(history: history) { reply += delta }
                print("KEEL_GEMINI chars=\(reply.count) text=\(reply.replacingOccurrences(of: "\n", with: " ").prefix(500))")
            } catch {
                print("KEEL_GEMINI error=\(String(describing: error))")
            }
            fflush(stdout)
        }
    }

    /// The simplest possible Foundation Models call: no tools, no custom prompt.
    /// If this fails too, the simulator can't load the model and the issue is the
    /// environment, not our engine.
    private static func runAppleBareProbe() {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            Task {
                do {
                    let session = LanguageModelSession(instructions: "You are a friendly assistant.")
                    let response = try await session.respond(to: "Say hello in one short sentence.")
                    print("KEEL_BARE ok text=\(response.content)")
                } catch {
                    print("KEEL_BARE error=\(String(describing: error))")
                }
                fflush(stdout)
            }
        } else {
            print("KEEL_BARE os_below_26")
        }
        #else
        print("KEEL_BARE framework_not_in_sdk")
        #endif
    }

    /// Streams directly from the Apple Intelligence engine (bypassing the
    /// composite's silent fallback) and prints the reply or the exact error, so we
    /// can see why it isn't answering.
    @MainActor
    private static func runAppleReplyProbe(env: AppEnvironment) {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            let engine = AppleIntelligenceEngine(toolbox: makeToolbox(env: env))
            print("KEEL_APPLE available=\(engine.isAvailable())")
            let history = [ChatTurn(role: .user, text: "I've been waking at 3am and feeling wired.")]
            Task { @MainActor in
                var reply = ""
                do {
                    for try await delta in engine.streamReply(history: history) { reply += delta }
                    print("KEEL_APPLE chars=\(reply.count) text=\(reply.replacingOccurrences(of: "\n", with: " ").prefix(240))")
                } catch {
                    print("KEEL_APPLE error=\(String(describing: error))")
                }
                fflush(stdout)
            }
        } else {
            print("KEEL_APPLE os_below_26")
        }
        #else
        print("KEEL_APPLE framework_not_in_sdk")
        #endif
    }

    /// Verifies the OFFLINE fallback still drafts a log card from a clear request
    /// (so "add a check-in" works even with no AI engine available).
    @MainActor
    private static func runOfflineAddEntryProbe(env: AppEnvironment) {
        let fallback = LocalCompanionFallback(toolbox: makeToolbox(env: env))
        let history = [ChatTurn(role: .user, text: "Please add a check-in for me: I'm feeling good and my energy is about 70.")]
        Task { @MainActor in
            var reply = ""
            do {
                for try await delta in fallback.streamReply(history: history, system: "") { reply += delta }
            } catch {
                print("KEEL_OFFLINE error=\(error)"); fflush(stdout); return
            }
            let pending = env.proposals.pending
            print("KEEL_OFFLINE replyChars=\(reply.count) proposals=\(pending.count) first='\(pending.first?.summary ?? "none")'")
            fflush(stdout)
        }
    }

    /// Asks Apple Intelligence to add a check-in and checks whether it actually
    /// drafts a proposal (calls `propose_log_checkin`). Isolates the model/tool
    /// path from the write path, so a "can't add an entry" report can be pinned
    /// on the model not calling the tool vs the confirm not saving.
    @MainActor
    private static func runAppleAddEntryProbe(env: AppEnvironment) {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            let engine = AppleIntelligenceEngine(toolbox: makeToolbox(env: env))
            // Realistic, casual phrasings — the way she'd actually ask.
            let phrases = [
                "add an entry for me",
                "can you add an entry for me?",
                "log today for me",
                "I want to add a check-in",
                "note that I've got a headache",
            ]
            Task { @MainActor in
                for phrase in phrases {
                    for p in env.proposals.pending { env.proposals.dismiss(p) }
                    var reply = ""
                    do {
                        for try await delta in engine.streamReply(history: [ChatTurn(role: .user, text: phrase)]) { reply += delta }
                    } catch {
                        print("KEEL_ADDENTRY phrase='\(phrase)' error=\(String(describing: error))"); continue
                    }
                    print("KEEL_ADDENTRY phrase='\(phrase)' proposals=\(env.proposals.pending.count) reply='\(reply.replacingOccurrences(of: "\n", with: " ").prefix(90))'")
                    fflush(stdout)
                }
                fflush(stdout)
            }
        } else {
            print("KEEL_ADDENTRY os_below_26")
        }
        #else
        print("KEEL_ADDENTRY framework_not_in_sdk")
        #endif
    }

    /// Prints whether on-device Apple Intelligence can serve a reply here, and if
    /// not, the exact reason (so we know whether it's enable-able or not eligible).
    private static func printAIStatus() {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            switch SystemLanguageModel.default.availability {
            case .available:
                print("KEEL_AI available=true")
            case .unavailable(let reason):
                print("KEEL_AI available=false reason=\(String(describing: reason))")
            }
        } else {
            print("KEEL_AI available=false reason=os_below_26")
        }
        #else
        print("KEEL_AI available=false reason=framework_not_in_sdk")
        #endif
        fflush(stdout)
    }

    /// Drives the real composite `ChatService` end to end and prints the reply, so
    /// the engine selection + fallback chain (Apple Intelligence, else Gemini, else
    /// mock) can be verified to actually return text.
    @MainActor
    private static func runCompanionReplyProbe(env: AppEnvironment) {
        Task { @MainActor in
            let history = [ChatTurn(role: .user, text: "I've been waking at 3am and feeling wired.")]
            var reply = ""
            do {
                for try await delta in env.chat.streamReply(history: history, system: KeelChatPrompt.system) {
                    reply += delta
                }
                print("KEEL_REPLY chars=\(reply.count) text=\(reply.replacingOccurrences(of: "\n", with: " ").prefix(240))")
            } catch {
                print("KEEL_REPLY error=\(error)")
            }
            fflush(stdout)
        }
    }

    /// Drives the Gemini free-tier limiter past its daily budget to confirm it
    /// starts throwing `rateLimited`. Uses a throwaway defaults suite so it never
    /// touches real counters.
    private static func runGeminiLimiterProbe() {
        let suite = UserDefaults(suiteName: "keel.test.limiter.\(UUID().uuidString)")!
        let limiter = GeminiRateLimiter(tier: GeminiFreeTier(requestsPerMinute: 100, requestsPerDay: 5),
                                        defaults: suite)
        Task {
            var ok = 0, limited = 0
            for _ in 0..<7 {
                do { try await limiter.reserve(); ok += 1 } catch { limited += 1 }
            }
            print("KEEL_LIMITER perDay=5 attempts=7 ok=\(ok) limited=\(limited)")
            fflush(stdout)
        }
    }

    /// Runs every read tool against the seeded store and prints its JSON, so the
    /// agent's data layer can be verified without an LLM in the loop. Pair with
    /// the seed flags (e.g. -uitSeedCheckIn -uitSeedSchedules -uitSeedWeek).
    @MainActor
    private static func runCompanionToolsProbe(env: AppEnvironment) {
        let toolbox = makeToolbox(env: env)
        let readTools = ["get_recent_checkins", "get_symptom_trends", "get_sleep_and_energy",
                         "get_medications", "get_cycle_summary", "get_tracking_overview", "build_gp_report"]
        Task { @MainActor in
            for name in readTools {
                let out = await toolbox.run(name: name, arguments: [:])
                print("KEEL_TOOL \(name) => \(out)")
            }
            fflush(stdout)
        }
    }

    /// Exercises a confirmed write end to end: draft a symptom proposal, confirm
    /// it, and print the check-in-symptom link count before and after.
    @MainActor
    private static func runCompanionProposalProbe(env: AppEnvironment) {
        let toolbox = makeToolbox(env: env)
        Task { @MainActor in
            let linkCount = { (try? env.context.fetchCount(FetchDescriptor<CheckInSymptom>())) ?? -1 }
            let before = linkCount()
            let status = await toolbox.run(name: "propose_log_symptom",
                                           arguments: ["name": "Headache", "severity": "moderate"])
            let pending = env.proposals.pending.count
            if let proposal = env.proposals.pending.first { env.proposals.confirm(proposal) }
            let after = linkCount()
            print("KEEL_PROPOSAL pending=\(pending) linksBefore=\(before) linksAfter=\(after) added=\(after - before) status=\(status)")
            fflush(stdout)
        }
    }

    @MainActor
    private static func makeToolbox(env: AppEnvironment) -> CompanionToolbox {
        let data = CompanionDataService(context: env.context, checkIns: env.checkIns,
                                        symptoms: env.symptoms, cycle: env.cycle,
                                        medications: env.medications, users: env.users)
        return CompanionToolbox(data: data, proposals: env.proposals)
    }

    /// Exercises the real BackupService: export the seeded store → wipe → restore
    /// from the archive → print before/after counts so a round-trip can be
    /// verified without tapping the UI. Run alongside -uitSeedCheckIn/-uitSeedMed.
    @MainActor
    private static func runBackupRoundtrip(env: AppEnvironment) {
        let ctx = env.context
        func counts() -> String {
            let ci = (try? ctx.fetch(FetchDescriptor<CheckIn>()).count) ?? -1
            let sym = (try? ctx.fetch(FetchDescriptor<Symptom>()).count) ?? -1
            let links = (try? ctx.fetch(FetchDescriptor<CheckInSymptom>()).count) ?? -1
            let med = (try? ctx.fetch(FetchDescriptor<Medication>()).count) ?? -1
            let act = (try? ctx.fetch(FetchDescriptor<ActivityLog>()).count) ?? -1
            let chat = (try? ctx.fetch(FetchDescriptor<ChatMessage>()).count) ?? -1
            let daily = (try? ctx.fetch(FetchDescriptor<DailySummary>()).count) ?? -1
            let hk = (try? ctx.fetch(FetchDescriptor<HealthSample>()).count) ?? -1
            return "checkIns=\(ci) symptoms=\(sym) links=\(links) meds=\(med) activity=\(act) chat=\(chat) daily=\(daily) hk=\(hk)"
        }
        do {
            let before = counts()
            let archive = try BackupService.export(context: ctx)
            let data = try BackupService.encode(archive)
            let decoded = try BackupService.decode(data)
            let summary = try BackupService.restore(from: decoded, into: ctx)
            let after = counts()
            print("KEEL_BACKUP before[\(before)] jsonBytes=\(data.count) restoredCheckIns=\(summary.checkIns) after[\(after)] match=\(before == after)")
        } catch {
            print("KEEL_BACKUP error=\(error)")
        }
        fflush(stdout)
    }
}
#endif
