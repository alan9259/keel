import Foundation
import SwiftData

/// Tri-state daily logging for the eating panel, over the existing `ActivityLog`
/// store. The three states are what make the later correlation honest:
///
///  - `true`  (yes) → a row with `amount == 1`
///  - `false` (no)  → a row with `amount == 0`  (an explicit "didn't", not a blank)
///  - `nil`   (not logged) → no row at all, so a day she never answered is excluded
///    from any analysis rather than being counted as a clean "no".
///
/// This is why "no" must persist a real 0-row: absence has to mean "unknown", never
/// "didn't have it".
enum EatingLog {
    /// Her logged state for an item on a day, read from already-fetched rows.
    static func state(for id: String, on day: Date, in logs: [ActivityLog], calendar: Calendar = .current) -> Bool? {
        let target = calendar.startOfDay(for: day)
        guard let row = logs.first(where: {
            $0.deletedAt == nil && $0.activityID == id && calendar.isDate($0.date, inSameDayAs: target)
        }) else { return nil }
        return row.amount > 0.5
    }

    /// Set (or clear) her answer. `nil` removes the row so the day reads as "not
    /// logged"; `true`/`false` upserts a 1/0 row.
    @MainActor
    static func set(_ value: Bool?, for id: String, on day: Date, ownerID: String,
                    in context: ModelContext, calendar: Calendar = .current) {
        let target = calendar.startOfDay(for: day)
        let existing = existingRow(id: id, on: target, in: context, calendar: calendar)
        switch value {
        case .none:
            if let existing { context.delete(existing) }
        case .some(let answer):
            let amount: Double = answer ? 1 : 0
            if let existing {
                existing.amount = amount
                existing.touch()
            } else {
                context.insert(ActivityLog(date: target, activityID: id, amount: amount, ownerID: ownerID))
            }
        }
        try? context.save()
    }

    @MainActor
    private static func existingRow(id: String, on day: Date, in context: ModelContext, calendar: Calendar) -> ActivityLog? {
        let start = calendar.startOfDay(for: day)
        let end = calendar.date(byAdding: .day, value: 1, to: start) ?? start
        let descriptor = FetchDescriptor<ActivityLog>(
            predicate: #Predicate { $0.deletedAt == nil && $0.activityID == id && $0.date >= start && $0.date < end }
        )
        return (try? context.fetch(descriptor))?.first
    }
}
