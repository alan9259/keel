import Foundation
import SwiftData

@MainActor
protocol CycleRepositoring {
    func entries(from start: Date, to end: Date) -> [CycleEntry]
    func isPeriodDay(_ date: Date) -> Bool
    func togglePeriodDay(_ date: Date)
    func lastPeriodStart(before date: Date) -> Date?
    func estimatedPhase(on date: Date) -> CyclePhase
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

    /// Tap a day to log/unlog a period day.
    func togglePeriodDay(_ date: Date) {
        if let existing = periodEntry(on: date) {
            existing.softDelete()
        } else {
            let entry = CycleEntry(date: date.startOfDay, type: .periodStart, ownerID: ownerID())
            context.insert(entry)
        }
        try? context.save()
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
