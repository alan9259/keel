import Foundation

/// The Day / Week / Month view mode on the Activities screen.
enum ActivityPeriod: String, CaseIterable, Identifiable {
    case day, week, month
    var id: String { rawValue }
    var label: String {
        switch self {
        case .day: "Day"
        case .week: "Week"
        case .month: "Month"
        }
    }
    /// The calendar unit a "previous/next" step moves by.
    var component: Calendar.Component {
        switch self {
        case .day: .day
        case .week: .weekOfYear
        case .month: .month
        }
    }
}

/// Pure date-window and roll-up helpers for the Activities period views, so the
/// window maths and the total/average aggregation are unit-tested without SwiftData
/// or the wall clock (inject `Calendar`/dates).
enum ActivityAggregation {

    /// The date interval a period covers, anchored on `date`.
    static func interval(for period: ActivityPeriod, containing date: Date, calendar: Calendar) -> DateInterval {
        switch period {
        case .day:
            return oneDay(date, calendar)
        case .week:
            return calendar.dateInterval(of: .weekOfYear, for: date) ?? oneDay(date, calendar)
        case .month:
            return calendar.dateInterval(of: .month, for: date) ?? oneDay(date, calendar)
        }
    }

    private static func oneDay(_ date: Date, _ calendar: Calendar) -> DateInterval {
        let start = calendar.startOfDay(for: date)
        let end = calendar.date(byAdding: .day, value: 1, to: start) ?? start
        return DateInterval(start: start, end: end)
    }

    /// Each day (start-of-day) within the interval, in order.
    static func days(in interval: DateInterval, calendar: Calendar) -> [Date] {
        var out: [Date] = []
        var day = calendar.startOfDay(for: interval.start)
        while day < interval.end {
            out.append(day)
            guard let next = calendar.date(byAdding: .day, value: 1, to: day) else { break }
            day = next
        }
        return out
    }

    /// Step the anchor one period earlier/later, clamped so it never moves into a
    /// period that starts after the one containing `notAfter` (today) — no future.
    static func shift(_ anchor: Date, by delta: Int, period: ActivityPeriod,
                      calendar: Calendar, notAfter: Date) -> Date {
        guard let moved = calendar.date(byAdding: period.component, value: delta, to: anchor) else { return anchor }
        let movedStart = interval(for: period, containing: moved, calendar: calendar).start
        let capStart = interval(for: period, containing: notAfter, calendar: calendar).start
        return movedStart > capStart ? anchor : moved
    }

    /// Whether the period containing `anchor` is the one containing `now` (so the
    /// "next" control can be disabled).
    static func isCurrent(_ anchor: Date, period: ActivityPeriod, calendar: Calendar, now: Date) -> Bool {
        interval(for: period, containing: anchor, calendar: calendar).start
            == interval(for: period, containing: now, calendar: calendar).start
    }

    enum Mode { case total, average }

    struct Rollup: Equatable {
        /// nil when no day in range had data.
        let summary: Double?
        /// One value per day in range (0 where a day had no data), for the bar chart.
        let perDay: [Double]
        let daysWithData: Int
    }

    /// Roll a metric's per-day values up over `days`: sum for `.total`, mean of the
    /// days that had data for `.average`.
    static func rollup(byDay: [Date: Double], days: [Date], mode: Mode, calendar: Calendar) -> Rollup {
        let values = days.map { byDay[calendar.startOfDay(for: $0)] }
        let present = values.compactMap { $0 }
        let summary: Double?
        switch mode {
        case .total: summary = present.isEmpty ? nil : present.reduce(0, +)
        case .average: summary = present.isEmpty ? nil : present.reduce(0, +) / Double(present.count)
        }
        return Rollup(summary: summary, perDay: values.map { $0 ?? 0 }, daysWithData: present.count)
    }
}
