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

    // MARK: - Phase

    /// The start of the cycle that `date` falls in: the most recent period start on
    /// or before it. Since `starts` already excludes isolated spotting (the caller
    /// builds them from menstruation days), a stray spotting day or a backfilled
    /// older cycle can't move this anchor.
    func currentCycleStart(on date: Date) -> Date? {
        let day = calendar.startOfDay(for: date)
        return starts.last { $0 <= day }
    }

    /// Median of her recent cycle lengths, for scaling the phase to her own cycle
    /// rather than a fixed 28 days. Nil until there's at least one full cycle.
    var medianRecentLength: Int? {
        let sorted = recentLengths.sorted()
        guard !sorted.isEmpty else { return nil }
        let mid = sorted.count / 2
        return sorted.count.isMultiple(of: 2) ? (sorted[mid - 1] + sorted[mid]) / 2 : sorted[mid]
    }

    /// A gentle estimate of where she is in her cycle, anchored to the current
    /// cycle start and scaled to her own length when there's enough history. This
    /// is a guide, not a prediction: perimenopausal cycles are irregular, so it
    /// stays `.unknown` before a start and once she's well past a plausible cycle.
    func phase(on date: Date) -> CyclePhase {
        guard let start = currentCycleStart(on: date) else { return .unknown }
        let day = calendar.dateComponents([.day], from: start, to: calendar.startOfDay(for: date)).day ?? -1
        guard day >= 0 else { return .unknown }

        // Scale to her median length once there are a few cycles; otherwise use the
        // textbook 28-day boundaries.
        if cycleLengths.count >= Self.estimateBasisCount, let med = medianRecentLength, med >= 20 {
            let ovulation = max(6, med - 14) // luteal phase runs ~14 days to the next period
            switch day {
            case 0..<5: return .menstrual
            case 5..<(ovulation - 1): return .follicular
            case (ovulation - 1)..<(ovulation + 2): return .ovulation
            case (ovulation + 2)..<(med + 8): return .luteal
            default: return .unknown
            }
        }
        switch day {
        case 0..<5: return .menstrual
        case 5..<13: return .follicular
        case 13..<16: return .ovulation
        case 16..<40: return .luteal
        default: return .unknown
        }
    }
}
