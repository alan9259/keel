import Foundation

/// A gentle read of one imported vital (resting heart rate, HRV) over recent days:
/// a recent average, a direction, and the day-values for a sparkline. Pure and
/// `Calendar`-injectable, so it is easy to test. Never invents a figure: every
/// number is her own Apple Health data, and the framing is "notice", not a verdict.
struct VitalTrend {
    struct Point: Equatable { let day: Date; let value: Double }
    let points: [Point]

    /// Values in day order, for the sparkline.
    var values: [Double] { points.sorted { $0.day < $1.day }.map(\.value) }
    var count: Int { points.count }

    /// Rounded recent average, or nil when there's nothing to average.
    var average: Int? {
        guard !points.isEmpty else { return nil }
        return Int((points.map(\.value).reduce(0, +) / Double(points.count)).rounded())
    }

    enum Direction { case up, steady, down }

    /// Recent-half vs earlier-half direction, only once there are a few days and the
    /// shift is meaningful (≥5%). Nil when it can't fairly say.
    var direction: Direction? {
        let v = values
        guard v.count >= 6 else { return nil }
        let half = v.count / 2
        let earlier = Array(v.prefix(half))
        let recent = Array(v.suffix(v.count - half))
        let e = earlier.reduce(0, +) / Double(earlier.count)
        let r = recent.reduce(0, +) / Double(recent.count)
        guard e > 0 else { return nil }
        if r >= e * 1.05 { return .up }
        if r <= e * 0.95 { return .down }
        return .steady
    }

    /// How her resting heart rate on the mornings after shorter-sleep nights (<6.5h)
    /// compares with mornings after good-sleep nights (≥7h), from her own paired days.
    /// A plain comparison with no judgement baked in — each caller picks how big a
    /// gap is worth surfacing. Nil until there are at least three of each.
    struct SleepHRGap: Equatable {
        let afterShortAvg: Double
        let afterGoodAvg: Double
        let shortNights: Int
        let goodNights: Int
        /// bpm higher after short sleep (negative if lower).
        var gap: Double { afterShortAvg - afterGoodAvg }
        var pairedDays: Int { shortNights + goodNights }
    }

    /// Generic version: splits any per-day value (resting HR, wrist temperature, …)
    /// into mornings after short vs good sleep and returns their averages + counts.
    static func sleepSplitGap(
        valueByDay: [Date: Double],
        sleepHoursByDay: [Date: Double],
        calendar: Calendar = .current
    ) -> SleepHRGap? {
        var afterShort: [Double] = []
        var afterGood: [Double] = []
        for (day, value) in valueByDay {
            guard let sleep = sleepHoursByDay[calendar.startOfDay(for: day)] else { continue }
            if sleep < 6.5 { afterShort.append(value) }
            else if sleep >= 7 { afterGood.append(value) }
        }
        guard afterShort.count >= 3, afterGood.count >= 3 else { return nil }
        return SleepHRGap(
            afterShortAvg: afterShort.reduce(0, +) / Double(afterShort.count),
            afterGoodAvg: afterGood.reduce(0, +) / Double(afterGood.count),
            shortNights: afterShort.count,
            goodNights: afterGood.count)
    }

    static func restingHRSleepGap(
        restingHRByDay: [Date: Double],
        sleepHoursByDay: [Date: Double],
        calendar: Calendar = .current
    ) -> SleepHRGap? {
        sleepSplitGap(valueByDay: restingHRByDay, sleepHoursByDay: sleepHoursByDay, calendar: calendar)
    }

    /// True when resting heart rate tends to run higher on the mornings after
    /// shorter sleep, from her own paired days. Nil when there isn't enough to say.
    /// A real comparison (like the sleep→energy detector), never an invented figure.
    static func restingHeartRateHigherAfterShortSleep(
        restingHRByDay: [Date: Double],
        sleepHoursByDay: [Date: Double],
        calendar: Calendar = .current
    ) -> Bool? {
        guard let gap = restingHRSleepGap(
            restingHRByDay: restingHRByDay, sleepHoursByDay: sleepHoursByDay, calendar: calendar) else { return nil }
        return gap.gap >= 2 // ~2 bpm higher after short sleep is worth noticing
    }
}
