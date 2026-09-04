import Foundation
import SwiftData
import HealthKit

/// Merges an Apple Health `HealthSnapshot` (activity + vitals) into Keel's own store,
/// read-only and idempotent. **Symptoms and menstrual flow are not imported** — she logs
/// those in Keel directly. Activity projects into `ActivityLog`; vitals are archived as
/// `HealthSample`.
///
/// Rules:
///  - Backfill only. A day she logged herself is never overwritten.
///  - Every projected row is tagged `source = .healthKit` for provenance, and deduped
///    by its natural key so re-running on each launch adds nothing new.
@MainActor
final class HealthIngestor {
    private let context: ModelContext
    private let ownerID: OwnerIDProvider
    private let symptoms: SymptomRepository

    init(context: ModelContext, ownerID: @escaping OwnerIDProvider, symptoms: SymptomRepository) {
        self.context = context
        self.ownerID = ownerID
        self.symptoms = symptoms
    }

    struct Summary {
        var activity = 0
        var vitals = 0
        var symptomsLinked = 0
        var symptomsArchived = 0
        var flow = 0
    }

    @discardableResult
    func ingest(_ snapshot: HealthSnapshot) -> Summary {
        var summary = Summary()

        var amounts = snapshot.activityAmounts
        if !snapshot.sleepByDay.isEmpty { amounts["sleep"] = snapshot.sleepByDay }
        for (activityID, byDay) in amounts {
            summary.activity += ingestActivity(activityID, byDay)
        }

        for series in snapshot.vitals {
            summary.vitals += ingestVitals(series)
        }

        // Symptoms and menstrual flow are no longer imported from Apple Health — she
        // logs symptoms and cycle in Keel directly. Only activity + vitals import here.
        try? context.save()
        return summary
    }

    /// One-time cleanup for the discontinued symptom + flow imports: remove any
    /// previously-imported HealthKit symptom links, `symptom.*` archive samples, and
    /// HealthKit-sourced cycle entries. Idempotent — nothing re-creates them, since the
    /// ingestor no longer writes them. Returns how many rows it removed.
    @discardableResult
    func purgeDiscontinuedHealthImports() -> Int {
        var removed = 0
        let links = (try? context.fetch(FetchDescriptor<CheckInSymptom>())) ?? []
        for link in links where link.source == .healthKit { context.delete(link); removed += 1 }

        let cycles = (try? context.fetch(FetchDescriptor<CycleEntry>())) ?? []
        for entry in cycles where entry.source == .healthKit { context.delete(entry); removed += 1 }

        let samples = (try? context.fetch(FetchDescriptor<HealthSample>())) ?? []
        for sample in samples where sample.typeID.hasPrefix("symptom.") { context.delete(sample); removed += 1 }

        if removed > 0 { try? context.save() }
        return removed
    }

    // MARK: Activity log (sleep, steps, exercise, meditation)

    private func ingestActivity(_ activityID: String, _ byDay: [Date: Double]) -> Int {
        var wrote = 0
        // One fetch for the whole type, indexed by day, rather than a fetch per day:
        // this runs on the main actor on every sync, and per-day fetches over a year
        // (times several metrics) froze the UI.
        let descriptor = FetchDescriptor<ActivityLog>(
            predicate: #Predicate { $0.deletedAt == nil && $0.activityID == activityID }
        )
        let existingByDay = Dictionary(
            ((try? context.fetch(descriptor)) ?? []).map { ($0.date.startOfDay, $0) },
            uniquingKeysWith: { first, _ in first }
        )
        for (rawDay, rawValue) in byDay where rawValue > 0 {
            let day = rawDay.startOfDay
            let value = (rawValue * 10).rounded() / 10
            if let existing = existingByDay[day] {
                // Never overwrite a value she typed by hand: Health and her manual
                // entries own different days and don't compete. For Health-authored
                // rows, refresh to the latest (steps/exercise/energy change through
                // the day, Apple revises recent days, and a corrected sleep reading
                // should win over the earlier inflated one).
                if existing.source != .manual, existing.amount != value {
                    existing.amount = value
                    existing.touch()
                    wrote += 1
                }
            } else {
                context.insert(ActivityLog(date: day, activityID: activityID, amount: value,
                                           source: .healthKit, ownerID: ownerID()))
                wrote += 1
            }
        }
        return wrote
    }

    // MARK: Vitals / workload → HealthSample

    private func ingestVitals(_ series: HealthSnapshot.VitalSeries) -> Int {
        var wrote = 0
        let typeID = series.typeID
        // One fetch per type, indexed by day (see ingestActivity): a year of vitals
        // across several series is thousands of rows, and a fetch per day froze the UI.
        let descriptor = FetchDescriptor<HealthSample>(
            predicate: #Predicate { $0.deletedAt == nil && $0.typeID == typeID }
        )
        let existingByDay = Dictionary(
            ((try? context.fetch(descriptor)) ?? []).map { ($0.day.startOfDay, $0) },
            uniquingKeysWith: { first, _ in first }
        )
        for (rawDay, rawValue) in series.byDay {
            let day = rawDay.startOfDay
            let value = (rawValue * 10).rounded() / 10
            if let existing = existingByDay[day] {
                // Vitals (heart rate, HRV, …) are Health-authored; refresh to the latest.
                if existing.value != value { existing.value = value; existing.touch(); wrote += 1 }
            } else {
                context.insert(HealthSample(typeID: typeID, day: day, value: value,
                                            unit: series.unit, ownerID: ownerID()))
                wrote += 1
            }
        }
        return wrote
    }

}
