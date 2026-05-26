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
        center.setNotificationCategories([category])
    }

    /// iOS keeps at most 64 pending notifications per app and silently drops the
    /// rest, so a cycle's individually-scheduled days get a budget rather than
    /// crowding everything else out.
    static func cycleHorizon(activeMedications: Int) -> Int {
        let budget = 48 / max(activeMedications, 1)
        return min(max(budget, 4), 24)
    }

    /// Bring a medication's reminders in line with its schedule. No time set
    /// means no reminder: she asked for a schedule, not a nudge.
    ///
    /// A repeating calendar trigger can't express "21 days on, 7 off", so a
    /// cycle's active days are laid out individually up to `cycleHorizon` and
    /// topped up whenever the app opens.
    func rescheduleMedication(id: UUID, name: String, schedule: DoseSchedule,
                              cycleHorizon: Int = 24) async {
        await cancelMedicationReminders(medicationID: id)
        // Only doses with a time raise anything: a day pattern on its own is a
        // record of what she takes, not a request to be nudged.
        let timed = schedule.sortedSlots.filter(\.hasTime)
        guard schedule.kind != .asNeeded, !timed.isEmpty else { return }
        // Split the budget across the doses, since each needs its own run.
        let perSlot = max(cycleHorizon / timed.count, 2)

        for dose in timed {
            let key = dose.id.uuidString.prefix(8)
            switch schedule.kind {
            case .weekly:
                let days = dose.weekdays.isEmpty ? Set(1...7) : dose.weekdays
                for weekday in days.sorted() {
                    var when = DateComponents()
                    when.weekday = weekday
                    when.hour = dose.hour
                    when.minute = dose.minute ?? 0
                    add(id: "\(medPrefix)\(id.uuidString).\(key).w\(weekday)", name: name,
                        medicationID: id, slot: dose.id.uuidString,
                        trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: true))
                }
            case .cycle:
                let days = schedule.upcomingDueDates(for: dose, from: .now, count: perSlot)
                for (index, day) in days.enumerated() {
                    var when = Calendar.current.dateComponents([.year, .month, .day], from: day)
                    when.hour = dose.hour
                    when.minute = dose.minute ?? 0
                    add(id: "\(medPrefix)\(id.uuidString).\(key).c\(index)", name: name,
                        medicationID: id, slot: dose.id.uuidString,
                        trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: false))
                }
            case .asNeeded:
                break
            }
        }
    }

    private func add(id: String, name: String, medicationID: UUID, slot: String?,
                     trigger: UNNotificationTrigger) {
        let content = UNMutableNotificationContent()
        content.title = "Time for \(name)"
        content.body = "A gentle reminder. Tap to mark it taken."
        content.sound = .default
        content.categoryIdentifier = Self.medCategoryID
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

    private func cancel(prefix: String) {
        Task {
            let pending = await center.pendingNotificationRequests()
            let ids = pending.map(\.identifier).filter { $0.hasPrefix(prefix) }
            if !ids.isEmpty { center.removePendingNotificationRequests(withIdentifiers: ids) }
        }
    }

    // MARK: - Lifestyle reminders

    /// Hydration nudges every `everyHours` across a waking window only, so it goes
    /// quiet overnight. Each hour is its own daily-repeating trigger (a plain
    /// interval trigger would fire around the clock).
    func scheduleHydration(startHour: Int = 8, endHour: Int = 21, everyHours: Int = 2) {
        cancel(prefix: hydrationPrefix)
        var hour = startHour
        while hour <= endHour {
            var when = DateComponents()
            when.hour = hour
            add(id: "\(hydrationPrefix)\(hour)", title: "A glass of water?",
                body: "A small sip counts. Staying hydrated can help with energy and headaches.",
                trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: true))
            hour += everyHours
        }
    }

    /// A gentle daily movement nudge, weekdays by default.
    func scheduleMovement(hour: Int = 14, minute: Int = 0, weekdaysOnly: Bool = true) {
        cancel(prefix: movementPrefix)
        let title = "A little movement?"
        let body = "A short walk or a stretch. Whatever feels good today."
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
    func scheduleWindDown(hour: Int = 21, minute: Int = 30) {
        cancel(prefix: windDownID)
        var when = DateComponents()
        when.hour = hour
        when.minute = minute
        add(id: windDownID, title: "Time to wind down",
            body: "A calmer evening can make for a better night's sleep.",
            trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: true))
    }

    func cancelHydration() { cancel(prefix: hydrationPrefix) }
    func cancelMovement() { cancel(prefix: movementPrefix) }
    func cancelWindDown() { cancel(prefix: windDownID) }
    func cancelDailyCheckIn() { cancel(prefix: checkInID) }

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
