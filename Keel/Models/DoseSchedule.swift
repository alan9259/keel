import Foundation

/// One dose: the days it's taken, and optionally the time of day.
///
/// Days belong to the dose rather than to the schedule, so adding an evening
/// dose can't quietly inherit the days of the morning one. A dose with no time
/// is a day pattern with no reminder, which is what a patch changed on set days
/// usually wants.
///
/// The time is hour and minute rather than a `Date` so it means the same thing
/// wherever she is: 8:00 am is 8:00 am, not an instant that shifts with a
/// timezone.
struct DoseSlot: Identifiable, Equatable, Codable {
    var id: UUID = UUID()
    /// Calendar weekdays: 1 is Sunday through 7 is Saturday. Empty counts as
    /// every day, so an incomplete pattern never quietly hides an entry.
    var weekdays: Set<Int> = Set(1...7)
    var hour: Int?
    var minute: Int?

    init(id: UUID = UUID(), weekdays: Set<Int> = Set(1...7), hour: Int? = nil, minute: Int? = nil) {
        self.id = id
        self.weekdays = weekdays
        self.hour = hour
        self.minute = minute
    }

    /// Tolerant on purpose: reads entries written before days moved onto the
    /// dose, where a missing day set meant "whatever the schedule covers".
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        weekdays = try container.decodeIfPresent(Set<Int>.self, forKey: .weekdays) ?? Set(1...7)
        hour = try container.decodeIfPresent(Int.self, forKey: .hour)
        minute = try container.decodeIfPresent(Int.self, forKey: .minute)
    }

    var hasTime: Bool { hour != nil }

    /// True when it applies to the whole week.
    var isEveryDay: Bool { weekdays.isEmpty || weekdays.count == 7 }

    func applies(toWeekday weekday: Int) -> Bool {
        weekdays.isEmpty || weekdays.contains(weekday)
    }

    /// A `Date` carrying this hour and minute, for a picker to bind to.
    var date: Date? {
        get {
            guard let hour else { return nil }
            return Calendar.current.date(bySettingHour: hour, minute: minute ?? 0, second: 0, of: .now)
        }
        set {
            guard let newValue else { hour = nil; minute = nil; return }
            let parts = Calendar.current.dateComponents([.hour, .minute], from: newValue)
            hour = parts.hour
            minute = parts.minute
        }
    }

    /// "8:00 am", or "Any time" when she hasn't set one.
    var label: String {
        date.map { $0.formatted(date: .omitted, time: .shortened) } ?? "Any time"
    }

    /// Sorts by clock time; timeless doses come last.
    var minutesIntoDay: Int {
        guard let hour else { return .max }
        return hour * 60 + (minute ?? 0)
    }
}

/// When something is taken.
///
/// Two shapes cover what she'll actually be on: a weekly pattern (which includes
/// every day, and a patch changed on two set days), or a repeating cycle with a
/// pause at the end, which is how cyclic HRT is written on a script. "As
/// directed" covers everything that doesn't sit on a fixed pattern.
///
/// A value type so the rules live in one place and can be reasoned about without
/// a database: `Medication` stores the parts and hands them back as this.
struct DoseSchedule: Equatable {
    enum Kind: String, CaseIterable, Codable, Identifiable {
        case weekly
        case cycle
        case asNeeded

        var id: String { rawValue }

        var label: String {
            switch self {
            case .weekly: "Weekly"
            case .cycle: "Cycle"
            case .asNeeded: "As directed"
            }
        }
    }

    var kind: Kind = .weekly
    /// The doses. Always at least one; the first starts as every day with no
    /// time, which is a plain "she takes this" with nothing chasing her.
    var slots: [DoseSlot] = [DoseSlot()]
    /// The whole cycle in days, pause included.
    var cycleLength: Int = 28
    /// Days of pause at the end of each cycle.
    var pauseDays: Int = 7
    /// The day that counts as day 1 of the cycle.
    var anchor: Date?

    static let everyDay = DoseSchedule()

    /// Days she actually takes it in each cycle.
    var activeDays: Int { max(cycleLength - pauseDays, 0) }

    var sortedSlots: [DoseSlot] {
        slots.sorted { $0.minutesIntoDay < $1.minutesIntoDay }
    }

    /// Every weekday any dose falls on, which is what the schedule covers.
    var weekdays: Set<Int> {
        let union = slots.reduce(into: Set<Int>()) { days, slot in
            days.formUnion(slot.weekdays.isEmpty ? Set(1...7) : slot.weekdays)
        }
        return union.isEmpty ? Set(1...7) : union
    }

    // MARK: Rules

    /// Whether it's due on a given day. Anything without a usable pattern
    /// answers yes: better to show an entry she can skip than to hide one she
    /// needed.
    func isDue(on date: Date, calendar: Calendar = .current) -> Bool {
        switch kind {
        case .asNeeded:
            return true
        case .weekly:
            guard !slots.isEmpty else { return true }
            let weekday = calendar.component(.weekday, from: date)
            return slots.contains { $0.applies(toWeekday: weekday) }
        case .cycle:
            guard cycleLength > 0, activeDays > 0 else { return true }
            return cycleDay(for: date, calendar: calendar).map { $0 <= activeDays } ?? true
        }
    }

    /// The doses due on a given day, in clock order. A cycle's doses all fall on
    /// its active days, so their weekdays don't narrow them further.
    func dueSlots(on date: Date, calendar: Calendar = .current) -> [DoseSlot] {
        guard isDue(on: date, calendar: calendar) else { return [] }
        guard kind == .weekly else { return sortedSlots }
        let weekday = calendar.component(.weekday, from: date)
        return sortedSlots.filter { $0.applies(toWeekday: weekday) }
    }

    /// 1-based day within the cycle, or nil when there's nothing to count from.
    func cycleDay(for date: Date, calendar: Calendar = .current) -> Int? {
        guard kind == .cycle, cycleLength > 0 else { return nil }
        let start = calendar.startOfDay(for: anchor ?? date)
        let day = calendar.startOfDay(for: date)
        guard let diff = calendar.dateComponents([.day], from: start, to: day).day else { return nil }
        // Modulo that also handles days before the anchor.
        return ((diff % cycleLength) + cycleLength) % cycleLength + 1
    }

    /// The next day it's due after the given one, searching a bounded window.
    func nextDue(after date: Date, calendar: Calendar = .current, within days: Int = 90) -> Date? {
        var cursor = calendar.startOfDay(for: date)
        for _ in 0..<days {
            guard let next = calendar.date(byAdding: .day, value: 1, to: cursor) else { return nil }
            cursor = next
            if isDue(on: cursor, calendar: calendar) { return cursor }
        }
        return nil
    }

    /// Upcoming days a given dose falls due, today included when it qualifies.
    /// Used to lay out reminders for a cycle, which a repeating calendar trigger
    /// can't express.
    func upcomingDueDates(for slot: DoseSlot, from date: Date, count: Int,
                          calendar: Calendar = .current) -> [Date] {
        guard count > 0 else { return [] }
        var found: [Date] = []
        var cursor = calendar.startOfDay(for: date)
        // Bounded so a schedule that's somehow never due can't spin forever.
        var remaining = count * max(cycleLength, 7) + count
        while found.count < count, remaining > 0 {
            if isDue(on: cursor, calendar: calendar),
               kind == .cycle || slot.applies(toWeekday: calendar.component(.weekday, from: cursor)) {
                found.append(cursor)
            }
            guard let next = calendar.date(byAdding: .day, value: 1, to: cursor) else { break }
            cursor = next
            remaining -= 1
        }
        return found
    }

    // MARK: Wording

    /// "Every day at 8:00 am and 8:00 pm", or "Every day at 8:00 am, Sat & Sun
    /// also at 10:00 am".
    var summary: String {
        let base: String
        switch kind {
        case .asNeeded: return "As directed" + timesSuffix(scopedByDay: false)
        case .cycle: return "\(activeDays) days on, \(pauseDays) off" + timesSuffix(scopedByDay: false)
        case .weekly: base = Self.phrase(for: weekdays)
        }

        let timed = sortedSlots.filter(\.hasTime)
        guard !timed.isEmpty else { return base }
        // A dose covering the whole pattern reads as part of the main sentence;
        // anything narrower gets its own clause.
        let covering = timed.filter { Self.effectiveDays($0) == weekdays }
        let narrower = timed.filter { Self.effectiveDays($0) != weekdays }
        var text = covering.isEmpty ? base : "\(base) at \(Self.list(covering.map(\.label)))"
        for slot in narrower {
            text += ", \(Self.phrase(for: Self.effectiveDays(slot))) also at \(slot.label)"
        }
        return text
    }

    /// Times phrase for the shapes where weekdays don't apply.
    private func timesSuffix(scopedByDay: Bool) -> String {
        let labels = sortedSlots.filter(\.hasTime).map(\.label)
        return labels.isEmpty ? "" : " at \(Self.list(labels))"
    }

    private static func effectiveDays(_ slot: DoseSlot) -> Set<Int> {
        slot.weekdays.isEmpty ? Set(1...7) : slot.weekdays
    }

    /// "Mon, Wed & Fri", collapsing the sets that have their own name.
    static func phrase(for days: Set<Int>) -> String {
        if days.isEmpty || days.count == 7 { return "Every day" }
        if days == [2, 3, 4, 5, 6] { return "Weekdays" }
        if days == [1, 7] { return "Weekends" }
        return list(orderedWeekdays().filter { days.contains($0) }.map { shortName($0) })
    }

    /// "a", "a and b", "a, b and c".
    static func list(_ items: [String]) -> String {
        guard items.count > 1 else { return items.first ?? "" }
        return items.dropLast().joined(separator: ", ") + " and " + items[items.count - 1]
    }

    // MARK: Weekday helpers

    /// Weekdays in the reader's own week order, so the row starts where her
    /// calendar starts.
    static func orderedWeekdays(calendar: Calendar = .current) -> [Int] {
        let first = calendar.firstWeekday
        return (0..<7).map { (first - 1 + $0) % 7 + 1 }
    }

    static func shortName(_ weekday: Int, calendar: Calendar = .current) -> String {
        calendar.shortWeekdaySymbols[weekday - 1]
    }

    static func initial(_ weekday: Int, calendar: Calendar = .current) -> String {
        calendar.veryShortWeekdaySymbols[weekday - 1]
    }

    static func fullName(_ weekday: Int, calendar: Calendar = .current) -> String {
        calendar.weekdaySymbols[weekday - 1]
    }
}
