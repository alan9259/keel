import Foundation
import UserNotifications

/// Gentle local reminders for medications and daily check-ins. No alarm-style
/// interruptions — soft, configurable nudges.
@MainActor
@Observable
final class NotificationService {
    private let center = UNUserNotificationCenter.current()
    private let medPrefix = "keel.med."
    private let checkInID = "keel.dailyCheckIn"
    private let hydrationPrefix = "keel.hydration."
    private let movementPrefix = "keel.movement."
    private let windDownID = "keel.winddown"

    // Medication reminder actions, handled by `NotificationCoordinator`.
    static let medCategoryID = "keel.med.reminder"
    static let markTakenActionID = "keel.med.markTaken"
    static let alwaysTakenActionID = "keel.med.alwaysTaken"
    // Auto-logged medicines get their own category with no "mark taken" actions:
    // there is nothing to tap, since Keel logs the dose for her automatically.
    static let medAutoCategoryID = "keel.med.auto"

    @discardableResult
    func requestAuthorization() async -> Bool {
        do {
            return try await center.requestAuthorization(options: [.alert, .sound, .badge])
        } catch {
            return false
        }
    }

    /// Route delivered-notification taps to a handler (the `NotificationCoordinator`).
    func setDelegate(_ delegate: any UNUserNotificationCenterDelegate) {
        center.delegate = delegate
    }

    /// Register the medication reminder category so its "Mark taken" / "Always mark
    /// taken" buttons appear on the notification. Called once at launch.
    func registerCategories() {
        let markTaken = UNNotificationAction(identifier: Self.markTakenActionID,
                                             title: "Mark taken", options: [])
        let alwaysTaken = UNNotificationAction(identifier: Self.alwaysTakenActionID,
                                               title: "Always mark taken", options: [])
        let category = UNNotificationCategory(identifier: Self.medCategoryID,
                                              actions: [markTaken, alwaysTaken],
                                              intentIdentifiers: [], options: [])
        // Auto-log reminders are informational, so their category carries no actions.
        let autoCategory = UNNotificationCategory(identifier: Self.medAutoCategoryID,
                                                  actions: [], intentIdentifiers: [], options: [])
        center.setNotificationCategories([category, autoCategory])
    }

    /// Stable per-day key ("20260731") used in reminder identifiers so a single
    /// day's occurrence can be cancelled once she's logged the dose. `nonisolated`
    /// so pure helpers (and tests) can call it off the main actor.
    nonisolated static func dayKey(_ date: Date, calendar: Calendar = .current) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d%02d%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    /// One reminder occurrence the scheduler decided to create. Pure value, so the
    /// "which days, skip-if-logged, past-time, horizon budget" decision can be unit
    /// tested without `UNUserNotificationCenter`.
    struct Occurrence: Equatable {
        let slotID: String
        let dayKey: String
        let hour: Int
        let minute: Int
    }

    /// The dated occurrences a medication's reminders should occupy, given its
    /// schedule and what's already been logged today. This is the whole scheduling
    /// decision, factored out of `rescheduleMedication` so it can be tested
    /// deterministically: no side effects, `now`/`calendar` injected.
    ///
    /// Rules, all covered by tests:
    /// - `asNeeded` or no timed dose → nothing.
    /// - Only future moments (a dose time already past today is skipped).
    /// - Today is skipped when the whole day, or that dose's slot, is already logged.
    /// - Each dose gets its own slice of the horizon so several doses/meds stay
    ///   under iOS's 64-pending cap.
    nonisolated static func plannedOccurrences(
        schedule: DoseSchedule,
        cycleHorizon: Int = 24,
        loggedTodayWholeDay: Bool = false,
        loggedTodaySlots: Set<String> = [],
        now: Date,
        calendar: Calendar = .current
    ) -> [Occurrence] {
        let timed = schedule.sortedSlots.filter(\.hasTime)
        guard schedule.kind != .asNeeded, !timed.isEmpty else { return [] }
        let perSlot = max(cycleHorizon / timed.count, 2)
        var out: [Occurrence] = []
        for dose in timed {
            // Extra candidates so days we skip (already past, or logged) don't eat
            // into the per-dose budget.
            let days = schedule.upcomingDueDates(for: dose, from: now, count: perSlot + 3, calendar: calendar)
            var scheduled = 0
            for day in days where scheduled < perSlot {
                var when = calendar.dateComponents([.year, .month, .day], from: day)
                when.hour = dose.hour
                when.minute = dose.minute ?? 0
                guard let fire = calendar.date(from: when), fire > now else { continue }
                // "Today" is relative to the injected `now`, not the wall clock:
                // isDateInToday would compare to the real date and break the moment
                // `now` differs from it (tests, or a capture that straddles midnight).
                if calendar.isDate(day, inSameDayAs: now),
                   loggedTodayWholeDay || loggedTodaySlots.contains(dose.id.uuidString) { continue }
                out.append(Occurrence(slotID: dose.id.uuidString,
                                      dayKey: dayKey(day, calendar: calendar),
                                      hour: dose.hour ?? 0, minute: dose.minute ?? 0))
                scheduled += 1
            }
        }
        return out
    }

    /// iOS keeps at most 64 pending notifications per app and silently drops the
    /// rest, so a cycle's individually-scheduled days get a budget rather than
    /// crowding everything else out.
    nonisolated static func cycleHorizon(activeMedications: Int) -> Int {
        let budget = 48 / max(activeMedications, 1)
        return min(max(budget, 4), 24)
    }

    /// Bring a medication's reminders in line with its schedule. No time set
    /// means no reminder: she asked for a schedule, not a nudge.
    ///
    /// Reminders are laid out as individual, dated occurrences (not a repeating
    /// trigger) up to `cycleHorizon`, topped up whenever the app opens. That lets
    /// us cancel or skip a single day once she's logged the dose, which a
    /// repeating calendar trigger can't express, and it's the only way a cycle's
    /// "21 days on, 7 off" pause can be honoured too.
    ///
    /// - Parameters:
    ///   - autoLog: this medicine logs its own doses, so its reminders are
    ///     informational (different wording, no "mark taken" actions).
    ///   - loggedTodayWholeDay / loggedTodaySlots: doses already logged *today*
    ///     (the only day that can be, since the future isn't logged yet); their
    ///     reminder for today is skipped so she isn't nudged for something done.
    func rescheduleMedication(id: UUID, name: String, schedule: DoseSchedule,
                              autoLog: Bool = false, cycleHorizon: Int = 24,
                              loggedTodayWholeDay: Bool = false,
                              loggedTodaySlots: Set<String> = []) async {
        await cancelMedicationReminders(medicationID: id)
        // The whole "which occurrences" decision is the pure `plannedOccurrences`
        // (unit-tested); here we just turn each into a scheduled request.
        let occurrences = Self.plannedOccurrences(
            schedule: schedule, cycleHorizon: cycleHorizon,
            loggedTodayWholeDay: loggedTodayWholeDay, loggedTodaySlots: loggedTodaySlots,
            now: Date())
        for occ in occurrences {
            let key = occ.slotID.prefix(8)
            var when = DateComponents()
            when.hour = occ.hour
            when.minute = occ.minute
            // The occurrence's dayKey (YYYYMMDD) pins the exact date.
            when.year = Int(occ.dayKey.prefix(4))
            when.month = Int(occ.dayKey.dropFirst(4).prefix(2))
            when.day = Int(occ.dayKey.suffix(2))
            add(id: "\(medPrefix)\(id.uuidString).\(key).d\(occ.dayKey)", name: name,
                medicationID: id, slot: occ.slotID, autoLog: autoLog,
                trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: false))
        }
    }

    private func add(id: String, name: String, medicationID: UUID, slot: String?,
                     autoLog: Bool, trigger: UNNotificationTrigger) {
        let content = UNMutableNotificationContent()
        content.title = "Time for \(name)"
        if autoLog {
            // Nothing to tap: the dose is logged for her next time she opens Keel.
            content.body = "A gentle reminder to take it. Keel logs this one for you."
            content.categoryIdentifier = Self.medAutoCategoryID
        } else {
            content.body = "A gentle reminder. Tap to mark it taken."
            content.categoryIdentifier = Self.medCategoryID
        }
        content.sound = .default
        // Carried back to the tap handler so it knows which dose to log.
        content.userInfo = ["medicationID": medicationID.uuidString, "slot": slot ?? ""]
        center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
    }

    /// A general soft reminder (not medication-specific).
    private func add(id: String, title: String, body: String, trigger: UNNotificationTrigger) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
    }

    /// Synchronously clear specific pending requests. This runs BEFORE the `add`s
    /// that follow, unlike the old fetch-pending-and-filter-by-prefix cancel, which
    /// deferred its work to a `Task` that ran AFTER the adds and deleted the very
    /// reminders just scheduled — the reason lifestyle reminders never fired while
    /// medication ones (which `await` their cancel) did.
    private func remove(_ ids: [String]) {
        center.removePendingNotificationRequests(withIdentifiers: ids)
    }

    // Every identifier a lifestyle reminder could occupy, so a reschedule or cancel
    // clears the whole set regardless of the current window or weekday pattern.
    private var hydrationIDs: [String] { (0...23).map { "\(hydrationPrefix)\($0)" } }
    private var movementIDs: [String] { (1...7).map { "\(movementPrefix)\($0)" } + ["\(movementPrefix)daily"] }

    // MARK: - Lifestyle reminders

    /// Hydration nudges every `everyHours` across a waking window only, so it goes
    /// quiet overnight. Each hour is its own daily-repeating trigger (a plain
    /// interval trigger would fire around the clock).
    /// `tip`, when present, is a fresh Apple-Intelligence line woven in place of the
    /// generic second sentence (see `LifestyleTipWriter`); nil keeps the static copy.
    func scheduleHydration(startHour: Int = 8, endHour: Int = 21, everyHours: Int = 2, tip: String? = nil) {
        remove(hydrationIDs)
        let body = "A small sip counts. " + (tip ?? "Staying hydrated can help with energy and headaches.")
        var hour = startHour
        while hour <= endHour {
            var when = DateComponents()
            when.hour = hour
            add(id: "\(hydrationPrefix)\(hour)", title: "A glass of water?",
                body: body,
                trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: true))
            hour += everyHours
        }
    }

    /// A gentle daily movement nudge, weekdays by default.
    func scheduleMovement(hour: Int = 14, minute: Int = 0, weekdaysOnly: Bool = true, tip: String? = nil) {
        remove(movementIDs)
        let title = "A little movement?"
        let body = "A short walk or a stretch. " + (tip ?? "Whatever feels good today.")
        if weekdaysOnly {
            for weekday in 2...6 { // Monday…Friday (1 = Sunday)
                var when = DateComponents()
                when.weekday = weekday
                when.hour = hour
                when.minute = minute
                add(id: "\(movementPrefix)\(weekday)", title: title, body: body,
                    trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: true))
            }
        } else {
            var when = DateComponents()
            when.hour = hour
            when.minute = minute
            add(id: "\(movementPrefix)daily", title: title, body: body,
                trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: true))
        }
    }

    /// A single evening wind-down nudge.
    func scheduleWindDown(hour: Int = 21, minute: Int = 30, tip: String? = nil) {
        remove([windDownID])
        var when = DateComponents()
        when.hour = hour
        when.minute = minute
        add(id: windDownID, title: "Time to wind down",
            body: tip ?? "A calmer evening can make for a better night's sleep.",
            trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: true))
    }

    func cancelHydration() { remove(hydrationIDs) }
    func cancelMovement() { remove(movementIDs) }
    func cancelWindDown() { remove([windDownID]) }
    func cancelDailyCheckIn() { remove([checkInID]) }

    /// Clear every pending Keel notification. Used by the master notifications
    /// switch. Every pending request in this app is one of ours.
    func cancelAll() {
        center.removeAllPendingNotificationRequests()
    }

    /// Clears every pending reminder for one medication, whatever shape its
    /// schedule was in when they were made.
    func cancelMedicationReminders(medicationID: UUID) async {
        let prefix = "\(medPrefix)\(medicationID.uuidString)"
        let pending = await center.pendingNotificationRequests()
        let ids = pending.map(\.identifier).filter { $0.hasPrefix(prefix) }
        guard !ids.isEmpty else { return }
        center.removePendingNotificationRequests(withIdentifiers: ids)
    }

    /// Cancel a medication's pending reminder(s) for a single day, once she's
    /// logged the dose. `slot` nil clears the whole day (a home-log "took it
    /// today" tick); a slot clears just that dose, so a later dose that day still
    /// reminds her.
    func cancelMedicationReminders(medicationID: UUID, on day: Date, slot: String? = nil) async {
        let base = "\(medPrefix)\(medicationID.uuidString)"
        let daySuffix = ".d\(Self.dayKey(day))"
        let slotKey = slot.map { String($0.prefix(8)) }
        let pending = await center.pendingNotificationRequests()
        let ids = pending.map(\.identifier).filter { id in
            guard id.hasPrefix(base), id.hasSuffix(daySuffix) else { return false }
            if let slotKey { return id.contains(".\(slotKey).") }
            return true
        }
        guard !ids.isEmpty else { return }
        center.removePendingNotificationRequests(withIdentifiers: ids)
    }

    /// A single, non-guilt-inducing daily check-in nudge.
    func scheduleDailyCheckInReminder(hour: Int = 9, minute: Int = 0) {
        var components = DateComponents()
        components.hour = hour
        components.minute = minute
        let content = UNMutableNotificationContent()
        content.title = "How are you feeling today?"
        content.body = "A quick check-in sharpens your picture."
        content.sound = .default
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
        center.add(UNNotificationRequest(identifier: checkInID, content: content, trigger: trigger))
    }
}

// MARK: - Fun tips for recurring lifestyle reminders

/// The everyday areas the recurring lifestyle nudges cover. Only lifestyle areas
/// get a generated tip: medication reminders never do, so a "fun tip" can't drift
/// into medical advice (the DESIGN_PRINCIPLES boundary: support and inform, never
/// diagnose or prescribe).
enum LifestyleTipArea {
    case hydration, movement, windDown

    /// The one-line request handed to the on-device model.
    var prompt: String {
        switch self {
        case .hydration: "Give one gentle, fun tip about drinking water or staying hydrated through the day."
        case .movement: "Give one gentle, fun tip about a little easy movement, like a short walk or a stretch."
        case .windDown: "Give one gentle, fun tip about winding down in the evening for better sleep."
        }
    }
}

/// A short, warm tip for a recurring reminder, generated on-device by Apple
/// Intelligence when available. Returns nil otherwise (older OS, ineligible
/// device, simulator), so the caller keeps its static copy. Mirrors
/// `AppleSummaryNarrator`: a single request, not a conversation.
enum LifestyleTipWriter {
    static func tip(for area: LifestyleTipArea) async -> String? {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            return await AppleLifestyleTip.generate(for: area)
        }
        #endif
        return nil
    }
}

#if canImport(FoundationModels)
import FoundationModels

@available(iOS 26.0, macOS 26.0, *)
enum AppleLifestyleTip {
    @MainActor
    static func generate(for area: LifestyleTipArea) async -> String? {
        guard case .available = SystemLanguageModel.default.availability else { return nil }
        let session = LanguageModelSession(instructions: instructions)
        do {
            let response = try await session.respond(to: area.prompt)
            // Enforce the no-dashes rule deterministically, in case the model slips.
            let text = response.content.trimmingCharacters(in: .whitespacesAndNewlines)
                .replacingOccurrences(of: " — ", with: ", ")
                .replacingOccurrences(of: " – ", with: ", ")
                .replacingOccurrences(of: "—", with: ", ")
                .replacingOccurrences(of: "–", with: ", ")
            return text.isEmpty ? nil : text
        } catch {
            return nil
        }
    }

    static let instructions = """
    You write one short, fun notification tip for Keel, an app for a woman \
    navigating perimenopause.

    Rules you must not break:
    - One sentence only, about twelve words, warm and a little playful.
    - It is a lifestyle nudge, not medical advice. Never mention medication, \
    hormones, symptoms, diagnosis, or treatment. Never claim a health outcome or \
    cite any number or statistic.
    - Australian and New Zealand spelling. Say "hot flushes", never "hot flashes".
    - No dashes of any kind. Use full stops and commas.
    - Gentle and encouraging, never bossy or alarming.

    Return only the tip sentence, nothing else.
    """
}
#endif
