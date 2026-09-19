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
    /// previously-imported HealthKit symptom links, `symptom.*` archive samples,
    /// HealthKit-sourced cycle entries, and legacy HealthKit `ActivityLog` rows (which
    /// now re-import into `HealthActivitySample`). Idempotent — nothing re-creates the
    /// symptom/cycle rows, and imported activity re-populates the health store on the
    /// next sync. Returns how many rows it removed.
    @discardableResult
    func purgeDiscontinuedHealthImports() -> Int {
        var removed = 0
        let links = (try? context.fetch(FetchDescriptor<CheckInSymptom>())) ?? []
        for link in links where link.source == .healthKit { context.delete(link); removed += 1 }

        let cycles = (try? context.fetch(FetchDescriptor<CycleEntry>())) ?? []
        for entry in cycles where entry.source == .healthKit { context.delete(entry); removed += 1 }

        let samples = (try? context.fetch(FetchDescriptor<HealthSample>())) ?? []
        for sample in samples where sample.typeID.hasPrefix("symptom.") { context.delete(sample); removed += 1 }

        // Legacy imported activity moves out of ActivityLog into HealthActivitySample.
        let activity = (try? context.fetch(FetchDescriptor<ActivityLog>())) ?? []
        for log in activity where log.source == .healthKit { context.delete(log); removed += 1 }

        // Mindful minutes are no longer imported: drop any imported "meditation" rows.
        // Only the imported copy (HealthActivitySample) — never her own manual entries.
        let imported = (try? context.fetch(FetchDescriptor<HealthActivitySample>())) ?? []
        for sample in imported where sample.activityID == "meditation" { context.delete(sample); removed += 1 }

        if removed > 0 { try? context.save() }
        return removed
    }

    /// Delete ALL Apple Health imports (activity + vitals) from the local health store,
    /// on her request ("Remove imported Apple Health data"). They re-import on the next
    /// sync if she keeps Health connected. Returns how many rows it removed.
    @discardableResult
    func purgeAllImportedHealthData() -> Int {
        var removed = 0
        for row in (try? context.fetch(FetchDescriptor<HealthActivitySample>())) ?? [] { context.delete(row); removed += 1 }
        for row in (try? context.fetch(FetchDescriptor<HealthSample>())) ?? [] { context.delete(row); removed += 1 }
        if removed > 0 { try? context.save() }
        return removed
    }

    // MARK: Activity log (sleep, steps, exercise, meditation)

    private func ingestActivity(_ activityID: String, _ byDay: [Date: Double]) -> Int {
        var wrote = 0
        // Imported activity lives in the local-only health store (`HealthActivitySample`),
        // kept separate from her manual `ActivityLog` entries; readers merge the two,
        // with her manual value winning for a day. One fetch for the whole type, indexed
        // by day (per-day fetches over a year froze the UI).
        let descriptor = FetchDescriptor<HealthActivitySample>(
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
                // Health-authored: refresh to the latest (Apple revises recent days).
                if existing.amount != value {
                    existing.amount = value
                    existing.touch()
                    wrote += 1
                }
            } else {
                context.insert(HealthActivitySample(date: day, activityID: activityID, amount: value,
                                                    ownerID: ownerID()))
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
