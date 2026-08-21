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

    /// True when resting heart rate tends to run higher on the mornings after
    /// shorter sleep, from her own paired days. Nil when there isn't enough to say.
    /// A real comparison (like the sleep→energy detector), never an invented figure.
    static func restingHeartRateHigherAfterShortSleep(
        restingHRByDay: [Date: Double],
        sleepHoursByDay: [Date: Double],
        calendar: Calendar = .current
    ) -> Bool? {
        var afterShort: [Double] = []
        var afterGood: [Double] = []
        for (day, rhr) in restingHRByDay {
            guard let sleep = sleepHoursByDay[calendar.startOfDay(for: day)] else { continue }
            if sleep < 6.5 { afterShort.append(rhr) }
            else if sleep >= 7 { afterGood.append(rhr) }
        }
        guard afterShort.count >= 3, afterGood.count >= 3 else { return nil }
        let short = afterShort.reduce(0, +) / Double(afterShort.count)
        let good = afterGood.reduce(0, +) / Double(afterGood.count)
        return short - good >= 2 // ~2 bpm higher after short sleep is worth noticing
    }
}
