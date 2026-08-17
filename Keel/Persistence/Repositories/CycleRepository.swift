import Foundation
import SwiftData

@MainActor
protocol CycleRepositoring {
    func entries(from start: Date, to end: Date) -> [CycleEntry]
    func isPeriodDay(_ date: Date) -> Bool
    func flow(on date: Date) -> FlowLevel?
    func setFlow(_ level: FlowLevel?, on date: Date)
    func togglePeriodDay(_ date: Date)
    func lastPeriodStart(before date: Date) -> Date?
    func estimatedPhase(on date: Date) -> CyclePhase
    func stats(lookbackDays: Int, now: Date) -> CycleStats
}

@MainActor
struct CycleRepository: CycleRepositoring {
    let context: ModelContext
    let ownerID: OwnerIDProvider

    func entries(from start: Date, to end: Date) -> [CycleEntry] {
        let lower = start.startOfDay
        let upper = end.startOfDay.adding(days: 1)
        let descriptor = FetchDescriptor<CycleEntry>(
            predicate: #Predicate { $0.deletedAt == nil && $0.date >= lower && $0.date < upper },
            sortBy: [SortDescriptor(\.date)]
        )
        return (try? context.fetch(descriptor)) ?? []
    }

    private func periodEntry(on date: Date) -> CycleEntry? {
        let start = date.startOfDay
        let end = start.adding(days: 1)
        let descriptor = FetchDescriptor<CycleEntry>(
            predicate: #Predicate { $0.deletedAt == nil && $0.date >= start && $0.date < end }
        )
        return (try? context.fetch(descriptor))?.first
    }

    func isPeriodDay(_ date: Date) -> Bool {
        periodEntry(on: date) != nil
    }

    /// The heaviness she logged for a day, or nil if it isn't a period day.
    func flow(on date: Date) -> FlowLevel? {
        periodEntry(on: date)?.flowLevel
    }

    /// Set (or clear) a day's flow. `nil` removes the period day; a level upserts
    /// it. Manual edits win: a level she sets is tagged `.manual` and, like the
    /// rest of Keel, isn't silently replaced by an import.
    func setFlow(_ level: FlowLevel?, on date: Date) {
        let existing = periodEntry(on: date)
        if let level {
            if let existing {
                existing.flowLevel = level
                existing.source = .manual
                existing.touch()
            } else {
                context.insert(CycleEntry(date: date.startOfDay, type: .periodStart,
                                          flowLevel: level, source: .manual, ownerID: ownerID()))
            }
        } else {
            existing?.softDelete()
        }
        try? context.save()
    }

    /// Tap a day to log/unlog a period day (the calendar grid). Logs it as a plain
    /// period without a specific level; the detail sheet is where levels are set.
    func togglePeriodDay(_ date: Date) {
        if let existing = periodEntry(on: date) {
            existing.softDelete()
        } else {
            let entry = CycleEntry(date: date.startOfDay, type: .periodStart,
                                   flowLevel: .unspecified, ownerID: ownerID())
            context.insert(entry)
        }
        try? context.save()
    }

    /// Cycle arithmetic over her recent period starts (for the timeline, the
    /// history and the gentle next-period estimate). Reads real entries only.
    func stats(lookbackDays: Int = 400, now: Date = .now) -> CycleStats {
        let from = now.startOfDay.adding(days: -lookbackDays)
        let days = entries(from: from, to: now)
            .filter { $0.type != .periodEnd }
            .map { $0.date.startOfDay }
        return CycleStats(starts: CycleStats.periodStarts(fromDays: days))
    }

    func lastPeriodStart(before date: Date) -> Date? {
        let ceiling = date.startOfDay.adding(days: 1)
        var descriptor = FetchDescriptor<CycleEntry>(
            predicate: #Predicate { $0.deletedAt == nil && $0.date < ceiling },
            sortBy: [SortDescriptor(\.date, order: .reverse)]
        )
        descriptor.fetchLimit = 1
        return (try? context.fetch(descriptor))?.first?.date
    }

    /// Rough phase estimate. Perimenopausal cycles are irregular, so this is a
    /// gentle heuristic, not a clinical prediction.
    func estimatedPhase(on date: Date) -> CyclePhase {
        guard let lastStart = lastPeriodStart(before: date) else { return .unknown }
        let day = date.days(since: lastStart)
        switch day {
        case 0..<5: return .menstrual
        case 5..<13: return .follicular
        case 13..<16: return .ovulation
        case 16..<40: return .luteal
        default: return .unknown
        }
    }
}
