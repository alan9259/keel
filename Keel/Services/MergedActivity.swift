import Foundation

/// Unites her manual activity (`ActivityLog`) with imported Apple Health activity
/// (`HealthActivitySample`), which live in separate stores since HealthKit data is kept
/// local-only and isolated. For any (`activityID`, day) a value she entered herself wins;
/// otherwise the imported value is used. Readers pass both fetched/queried arrays.
enum MergedActivity {

    /// The amount for one activity on one day, or nil if neither store has one > 0.
    static func amount(_ activityID: String, on day: Date,
                       manual: [ActivityLog], imported: [HealthActivitySample]) -> Double? {
        if let m = manual.first(where: {
            $0.deletedAt == nil && $0.activityID == activityID && $0.date.isSameDay(as: day) && $0.amount > 0
        }) { return m.amount }
        if let h = imported.first(where: {
            $0.deletedAt == nil && $0.activityID == activityID && $0.date.isSameDay(as: day) && $0.amount > 0
        }) { return h.amount }
        return nil
    }

    /// All daily amounts for one activity, keyed by start-of-day, manual winning per day.
    static func byDay(_ activityID: String,
                      manual: [ActivityLog], imported: [HealthActivitySample]) -> [Date: Double] {
        var out: [Date: Double] = [:]
        for h in imported where h.deletedAt == nil && h.activityID == activityID && h.amount > 0 {
            out[h.date.startOfDay] = h.amount
        }
        for m in manual where m.deletedAt == nil && m.activityID == activityID && m.amount > 0 {
            out[m.date.startOfDay] = m.amount   // her own entry wins
        }
        return out
    }
}
