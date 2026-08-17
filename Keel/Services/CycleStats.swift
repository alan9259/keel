import Foundation

/// Cycle arithmetic over her own logged period starts: cycle lengths, a typical
/// range, the day of the current cycle, and a gently-gated estimate of the next
/// period. Pure and `Calendar`-injectable, so it is fully unit-testable and never
/// depends on the wall clock.
///
/// The estimate is deliberately conservative. In perimenopause cycles are often
/// irregular, so a confident single-day prediction would usually be wrong. We only
/// offer a window, and only when the recent cycles are consistent enough to mean
/// something; otherwise the UI shows the history and says it's too variable to
/// estimate. Nothing here invents a number: every figure is derived from her data.
struct CycleStats {
    /// Ascending, de-duplicated period-start days.
    let starts: [Date]
    var calendar: Calendar = .current

    /// Only a handful of recent cycles inform the "typical" and the estimate;
    /// older ones matter less as her body changes.
    static let recentWindow = 6
    /// Cycles the estimate is based on.
    static let estimateBasisCount = 3
    /// If the recent cycles spread wider than this, they're too variable to
    /// estimate a next period from.
    static let maxSpreadDays = 7

    /// Period starts derived from a set of period days: a start is a period day
    /// whose previous day was not a period day.
    static func periodStarts(fromDays days: [Date], calendar: Calendar = .current) -> [Date] {
        let set = Set(days.map { calendar.startOfDay(for: $0) })
        return set.filter { day in
            let prev = calendar.date(byAdding: .day, value: -1, to: day) ?? day
            return !set.contains(prev)
        }.sorted()
    }

    /// Length in days between each consecutive pair of starts, oldest first.
    var cycleLengths: [Int] {
        guard starts.count >= 2 else { return [] }
        return zip(starts, starts.dropFirst()).map { earlier, later in
            calendar.dateComponents([.day], from: earlier, to: later).day ?? 0
        }
    }

    /// The most recent cycle lengths (up to `recentWindow`), newest last.
    var recentLengths: [Int] { Array(cycleLengths.suffix(Self.recentWindow)) }

    var lastStart: Date? { starts.last }

    /// 1-based day of the current cycle on `date`, or nil if there's no start yet
    /// or the date is before the last start.
    func cycleDay(on date: Date) -> Int? {
        guard let last = lastStart else { return nil }
        let days = calendar.dateComponents([.day], from: last, to: calendar.startOfDay(for: date)).day ?? -1
        return days >= 0 ? days + 1 : nil
    }

    /// The span her recent cycles have run, e.g. 24...31, or nil with fewer than
    /// two cycles.
    var typicalRange: ClosedRange<Int>? {
        let recent = recentLengths
        guard recent.count >= 2, let lo = recent.min(), let hi = recent.max() else { return nil }
        return lo...hi
    }

    /// The last few cycles, only if they're consistent enough to forecast from.
    private var estimateBasis: [Int]? {
        let recent = Array(cycleLengths.suffix(Self.estimateBasisCount))
        guard recent.count == Self.estimateBasisCount,
              let lo = recent.min(), let hi = recent.max(), hi - lo <= Self.maxSpreadDays else { return nil }
        return recent
    }

    /// Whether a next-period estimate is trustworthy enough to show at all.
    var canEstimate: Bool { estimateBasis != nil }

    /// The window her next period is likely to fall in, based on her recent
    /// range, or nil when there isn't enough consistency to say.
    func estimatedWindow() -> ClosedRange<Date>? {
        guard let basis = estimateBasis, let last = lastStart,
              let lo = basis.min(), let hi = basis.max(),
              let from = calendar.date(byAdding: .day, value: lo, to: last),
              let to = calendar.date(byAdding: .day, value: hi, to: last) else { return nil }
        return from...to
    }
}
