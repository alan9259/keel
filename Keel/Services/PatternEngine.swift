import Foundation
import SwiftData

/// One grounded observation about her own data, rendered as a Looking back / Cycle
/// card. Nothing here invents a statistic: every number is a real count or range from
/// her own logs, framed as something to notice, never a diagnosis.
///
/// Each finding describes ONE measure. Keel never links two measures in a sentence
/// (e.g. "after less sleep, your resting heart rate was higher"): that reads as a
/// claim about cause, so the detectors that did so were removed for V1. Showing
/// readings side by side elsewhere is fine.
struct PatternFinding {
    enum Kind: String {
        case cycleVariability
    }

    let kind: Kind
    let title: String
    let detail: String
    let timeframe: String
    let icon: String
    let accent: InsightAccent
}

/// Derives single-measure observations from her own logs. A pure value type over
/// already-extracted data so it is easy to test; build it with `PatternEngine.build`.
///
/// Detectors: cycle-length variability (her real range of cycle lengths). Her
/// most-logged symptoms are covered by Looking back's month summary (`MonthSummary`).
struct PatternEngine {
    /// A day's worth of her logs, flattened off the SwiftData models.
    struct DayCheckIn {
        let day: Date
    }

    let checkIns: [DayCheckIn]
    /// First day of each logged period run (start of day), ascending.
    let periodStarts: [Date]

    func findings() -> [PatternFinding] {
        var out: [PatternFinding] = []
        if let f = cycleVariability() { out.append(f) }
        return out
    }

    /// Distinct calendar days she has checked in on (any window).
    var loggedDayCount: Int { Set(checkIns.map { $0.day }).count }

    // MARK: - Cycle-length variability

    private func cycleVariability() -> PatternFinding? {
        guard periodStarts.count >= 3 else { return nil }
        let sorted = periodStarts.sorted()
        var intervals: [Int] = []
        for i in 1..<sorted.count {
            let gap = sorted[i].days(since: sorted[i - 1])
            // Ignore obvious mis-logs / spotting so the range stays meaningful.
            if gap >= 15 && gap <= 90 { intervals.append(gap) }
        }
        guard intervals.count >= 2, let lo = intervals.min(), let hi = intervals.max() else { return nil }
        // A persistent 7+ day swing between cycles is a recognised early sign of
        // the perimenopausal transition. We report her real range, not a guess.
        guard hi - lo >= 7 else { return nil }
        return PatternFinding(
            kind: .cycleVariability,
            title: "Your cycle length",
            detail: "Your recent cycles ranged from about \(lo) to \(hi) days apart. That's the kind of detail that can be useful to bring to your GP.",
            timeframe: "Across your last \(sorted.count) logged cycles",
            icon: "arrow.left.and.right",
            accent: .sage)
    }

    // MARK: - Helpers

    /// First day of each run of logged period days.
    static func periodStarts(from periodDays: Set<Date>) -> [Date] {
        periodDays.filter { !periodDays.contains($0.adding(days: -1)) }.sorted()
    }
}

// MARK: - Building from the store

@MainActor
extension PatternEngine {
    /// Reads the last `window` days of check-ins and cycle logs, flattening them into
    /// the pure engine. Cycle variability needs several months, hence 120 days.
    static func build(context: ModelContext, window: Int = 120, today: Date = Date()) -> PatternEngine {
        let floor = today.startOfDay.adding(days: -(window - 1))

        let checkInDescriptor = FetchDescriptor<CheckIn>(
            predicate: #Predicate { $0.deletedAt == nil && $0.date >= floor },
            sortBy: [SortDescriptor(\.date, order: .reverse)]
        )
        let checkIns = ((try? context.fetch(checkInDescriptor)) ?? []).map { ci in
            DayCheckIn(day: ci.date.startOfDay)
        }

        let cycleDescriptor = FetchDescriptor<CycleEntry>(
            predicate: #Predicate { $0.deletedAt == nil && $0.date >= floor }
        )
        let periodDays = Set(((try? context.fetch(cycleDescriptor)) ?? []).map { $0.date.startOfDay })

        return PatternEngine(
            checkIns: checkIns,
            periodStarts: periodStarts(from: periodDays))
    }
}
