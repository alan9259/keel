import Foundation
import SwiftData

@MainActor
protocol CheckInRepositoring {
    @discardableResult
    func create(mood: Mood, energy: Int, notes: String?, symptoms: [(symptom: Symptom, severity: Int)], date: Date) -> CheckIn
    func update(_ checkIn: CheckIn, mood: Mood, energy: Int, notes: String?, symptoms: [(symptom: Symptom, severity: Int)])
    func delete(_ checkIn: CheckIn)
    func recent(limit: Int) -> [CheckIn]
    func all() -> [CheckIn]
    func todays() -> CheckIn?
    func count(since date: Date) -> Int
    func trackingDayCount(startDate: Date) -> Int
}

@MainActor
struct CheckInRepository: CheckInRepositoring {
    let context: ModelContext
    let ownerID: OwnerIDProvider

    @discardableResult
    func create(mood: Mood, energy: Int, notes: String?, symptoms: [(symptom: Symptom, severity: Int)], date: Date = .now) -> CheckIn {
        let owner = ownerID()
        let checkIn = CheckIn(
            date: date,
            mood: mood,
            energy: energy,
            notes: notes?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
            ownerID: owner
        )
        context.insert(checkIn)
        for item in symptoms {
            let link = CheckInSymptom(
                checkIn: checkIn, symptom: item.symptom,
                severity: item.severity, ownerID: owner
            )
            context.insert(link)
        }
        try? context.save()
        return checkIn
    }

    /// Edit an existing entry in place: mood, energy, notes, and its symptoms.
    /// Symptom links are reconciled, a deselected symptom is tombstoned (history
    /// stays), a newly selected one is added, and a kept one's severity updated.
    func update(_ checkIn: CheckIn, mood: Mood, energy: Int, notes: String?,
                symptoms: [(symptom: Symptom, severity: Int)]) {
        checkIn.mood = mood
        checkIn.energy = energy
        checkIn.notes = notes?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty

        var wanted: [UUID: Int] = [:]
        var byID: [UUID: Symptom] = [:]
        for item in symptoms {
            wanted[item.symptom.id] = item.severity
            byID[item.symptom.id] = item.symptom
        }
        // Update or remove the links already on the entry.
        for link in checkIn.symptomLinks where !link.isTombstoned {
            guard let id = link.symptom?.id else { continue }
            if let severity = wanted[id] {
                if link.severity != severity { link.severity = severity; link.touch() }
                wanted[id] = nil
            } else {
                link.softDelete()
            }
        }
        // Add the newly selected ones.
        let owner = ownerID()
        for (id, severity) in wanted {
            guard let symptom = byID[id] else { continue }
            context.insert(CheckInSymptom(checkIn: checkIn, symptom: symptom, severity: severity, ownerID: owner))
        }
        checkIn.touch()
        try? context.save()
    }

    /// Remove an entry: soft-delete it and tombstone its symptom links, so it drops
    /// out of every view but the deletion still syncs (history isn't hard-erased).
    func delete(_ checkIn: CheckIn) {
        for link in checkIn.symptomLinks where !link.isTombstoned { link.softDelete() }
        checkIn.softDelete()
        try? context.save()
    }

    func recent(limit: Int) -> [CheckIn] {
        var descriptor = FetchDescriptor<CheckIn>(
            predicate: #Predicate { $0.deletedAt == nil },
            sortBy: [SortDescriptor(\.date, order: .reverse)]
        )
        descriptor.fetchLimit = limit
        return (try? context.fetch(descriptor)) ?? []
    }

    func all() -> [CheckIn] {
        let descriptor = FetchDescriptor<CheckIn>(
            predicate: #Predicate { $0.deletedAt == nil },
            sortBy: [SortDescriptor(\.date, order: .reverse)]
        )
        return (try? context.fetch(descriptor)) ?? []
    }

    func todays() -> CheckIn? {
        let start = Date.now.startOfDay
        let end = start.adding(days: 1)
        let descriptor = FetchDescriptor<CheckIn>(
            predicate: #Predicate { $0.deletedAt == nil && $0.date >= start && $0.date < end },
            sortBy: [SortDescriptor(\.date, order: .reverse)]
        )
        return (try? context.fetch(descriptor))?.first
    }

    func count(since date: Date) -> Int {
        let start = date.startOfDay
        let descriptor = FetchDescriptor<CheckIn>(
            predicate: #Predicate { $0.deletedAt == nil && $0.date >= start }
        )
        return (try? context.fetchCount(descriptor)) ?? 0
    }

    /// Distinct calendar days that have at least one check-in — the "you've been
    /// tracking for N days" figure shown around the app.
    func trackingDayCount(startDate: Date) -> Int {
        let days = Set(all().map { $0.date.startOfDay })
        return max(days.count, 0)
    }
}

extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
