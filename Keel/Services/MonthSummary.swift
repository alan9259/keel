import Foundation
import SwiftData

/// "Looking back": a fixed-wording summary of the current month, built live from her
/// own check-ins. No generated text and no interpretation: how many days she checked
/// in, the symptoms she logged most often (with real day counts), and what she wrote.
/// Pure over flattened entries, with `now` and `Calendar` injected, so it's unit-tested.
struct MonthSummary: Equatable {
    struct Entry {
        let id: UUID
        let date: Date
        let symptoms: [String]
        let notes: String?
    }

    struct SymptomCount: Equatable {
        let name: String
        let days: Int
    }

    struct Note: Equatable, Identifiable {
        let id: UUID
        let date: Date
        let text: String
    }

    /// The calendar month this covers.
    let month: DateInterval
    /// Distinct days she checked in on this month.
    let daysCheckedIn: Int
    /// Most-logged symptoms this month, by distinct days, then name. At most `topLimit`.
    let topSymptoms: [SymptomCount]
    /// Her notes from this month, newest first.
    let notes: [Note]

    static let topLimit = 5
    /// How many notes "Looking back" shows before "See all notes".
    static let notePreviewLimit = 3

    /// The calendar month always means the Gregorian month, in her time zone. A phone set
    /// to another calendar (Hebrew, Coptic and Ethiopic have a 13th month; Islamic and
    /// Chinese months are lunar) would otherwise get a different range and a month
    /// number with no English name.
    nonisolated static func gregorian(like calendar: Calendar) -> Calendar {
        var g = Calendar(identifier: .gregorian)
        g.timeZone = calendar.timeZone
        g.locale = Locale(identifier: "en_AU")
        return g
    }

    nonisolated static func build(entries: [Entry], now: Date, calendar: Calendar) -> MonthSummary {
        let calendar = gregorian(like: calendar)
        let month = calendar.dateInterval(of: .month, for: now)
            ?? DateInterval(start: calendar.startOfDay(for: now), duration: 86_400)
        let inMonth = entries.filter { month.contains($0.date) && $0.date < month.end }

        let days = Set(inMonth.map { calendar.startOfDay(for: $0.date) })

        var symptomDays: [String: Set<Date>] = [:]
        for entry in inMonth {
            for name in entry.symptoms { symptomDays[name, default: []].insert(calendar.startOfDay(for: entry.date)) }
        }
        let top = symptomDays
            .map { SymptomCount(name: $0.key, days: $0.value.count) }
            .sorted { $0.days != $1.days ? $0.days > $1.days : $0.name < $1.name }
            .prefix(topLimit)

        return MonthSummary(month: month, daysCheckedIn: days.count, topSymptoms: Array(top),
                            notes: notes(from: inMonth))
    }

    /// Non-empty notes, trimmed, newest first.
    nonisolated static func notes(from entries: [Entry]) -> [Note] {
        entries
            .compactMap { entry -> Note? in
                guard let text = entry.notes?.trimmingCharacters(in: .whitespacesAndNewlines),
                      !text.isEmpty else { return nil }
                return Note(id: entry.id, date: entry.date, text: text)
            }
            .sorted { $0.date > $1.date }
    }

    // MARK: Copy (fixed wording)

    /// "October so far" for the current month.
    func heading(calendar: Calendar) -> String {
        // English month names to match the rest of Keel's copy (a calendar without a
        // language, like a bare UTC one, would otherwise give "M10").
        let g = Self.gregorian(like: calendar)
        let index = g.component(.month, from: month.start) - 1
        return "\(g.standaloneMonthSymbols[index]) so far"
    }

    var checkInLine: String {
        switch daysCheckedIn {
        case 0: "No check-ins yet this month."
        default: "You checked in on \(Self.days(daysCheckedIn)) this month."
        }
    }

    static func days(_ n: Int) -> String { n == 1 ? "1 day" : "\(n) days" }
}

// MARK: - Reading from the store

@MainActor
extension MonthSummary {
    /// This month's summary from her live (not deleted) check-ins.
    static func current(context: ModelContext, now: Date = .now, calendar: Calendar = .current) -> MonthSummary {
        let month = gregorian(like: calendar).dateInterval(of: .month, for: now)
        let start = month?.start ?? calendar.startOfDay(for: now)
        let end = month?.end ?? now
        let descriptor = FetchDescriptor<CheckIn>(
            predicate: #Predicate { $0.deletedAt == nil && $0.date >= start && $0.date < end })
        let entries = ((try? context.fetch(descriptor)) ?? []).map(Entry.init)
        return build(entries: entries, now: now, calendar: calendar)
    }

    /// Notes from check-ins, newest first, without loading their symptoms ("See all notes").
    static func notes(fromCheckIns checkIns: [CheckIn]) -> [Note] {
        notes(from: checkIns.map { Entry(id: $0.id, date: $0.date, symptoms: [], notes: $0.notes) })
    }
}

extension MonthSummary.Entry {
    @MainActor
    init(_ checkIn: CheckIn) {
        self.init(id: checkIn.id, date: checkIn.date, symptoms: checkIn.symptoms.map(\.name), notes: checkIn.notes)
    }
}
