import Foundation

/// User-configurable timing for the lifestyle reminders. Persisted in
/// `SettingsStore`; `RemindersView` edits it and reschedules the enabled nudges.
/// Medication reminders are set per item and aren't part of this.
struct ReminderConfig: Codable, Equatable {
    var checkInHour = 9
    var checkInMinute = 0

    var hydrationStartHour = 8
    var hydrationEndHour = 21
    /// Nudge every this many hours across the waking window.
    var hydrationIntervalHours = 2

    var movementHour = 14
    var movementMinute = 0
    var movementWeekdaysOnly = true

    var windDownHour = 21
    var windDownMinute = 30
}
