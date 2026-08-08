import Foundation
import SwiftData
import HealthKit

/// Merges an Apple Health `HealthSnapshot` into Keel's own store, read-only and
/// idempotent. Everything with a natural home is projected into Keel's entities
/// so it shows up where she already looks (and feeds the patterns); everything
/// else is archived as a `HealthSample` so nothing she granted us is lost.
///
/// Rules:
///  - Backfill only. A day she logged herself is never overwritten.
///  - Imported symptoms attach to an existing check-in only. We never fabricate a
///    check-in (that would invent a mood she didn't choose); a symptom on a day
///    with no check-in is archived as a `HealthSample` instead.
///  - Every projected row is tagged `source = .healthKit` for provenance, and
///    deduped by its natural key so re-running on each launch adds nothing new.
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

    /// Health's Symptoms category → Keel catalog name + group. Names already in
    /// `SymptomCatalog` reuse that built-in chip; the rest become tagged customs.
    private static let symptomMap: [String: (name: String, category: SymptomCategory)] = [
        HKCategoryTypeIdentifier.hotFlashes.rawValue: ("Hot flushes", .body),
        HKCategoryTypeIdentifier.nightSweats.rawValue: ("Night sweats", .sleep),
        HKCategoryTypeIdentifier.moodChanges.rawValue: ("Mood swings", .mood),
        HKCategoryTypeIdentifier.fatigue.rawValue: ("Fatigue or low energy", .energy),
        HKCategoryTypeIdentifier.headache.rawValue: ("Headache", .body),
        HKCategoryTypeIdentifier.sleepChanges.rawValue: ("Trouble sleeping", .sleep),
        HKCategoryTypeIdentifier.vaginalDryness.rawValue: ("Vaginal dryness", .intimacy),
        HKCategoryTypeIdentifier.memoryLapse.rawValue: ("Memory slips", .cognition),
        HKCategoryTypeIdentifier.rapidPoundingOrFlutteringHeartbeat.rawValue: ("Palpitations", .body),
        HKCategoryTypeIdentifier.dizziness.rawValue: ("Dizziness", .body),
        HKCategoryTypeIdentifier.bloating.rawValue: ("Bloating", .digestion),
        HKCategoryTypeIdentifier.nausea.rawValue: ("Nausea", .digestion),
        HKCategoryTypeIdentifier.constipation.rawValue: ("Constipation", .digestion),
        HKCategoryTypeIdentifier.heartburn.rawValue: ("Heartburn", .digestion),
        HKCategoryTypeIdentifier.appetiteChanges.rawValue: ("Appetite changes", .digestion),
        HKCategoryTypeIdentifier.drySkin.rawValue: ("Dry skin", .skin),
        HKCategoryTypeIdentifier.hairLoss.rawValue: ("Hair thinning", .skin),
        HKCategoryTypeIdentifier.lowerBackPain.rawValue: ("Muscle aches", .aches),
        HKCategoryTypeIdentifier.generalizedBodyAche.rawValue: ("Muscle aches", .aches),
        HKCategoryTypeIdentifier.chills.rawValue: ("Chills", .body),
        HKCategoryTypeIdentifier.breastPain.rawValue: ("Breast tenderness", .body),
        HKCategoryTypeIdentifier.pelvicPain.rawValue: ("Pelvic pain", .intimacy),
    ]

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

        let (linked, archived) = ingestSymptoms(snapshot.symptoms)
        summary.symptomsLinked = linked
        summary.symptomsArchived = archived

        summary.flow = ingestFlow(snapshot.menstrualFlowDays)

        try? context.save()
        return summary
    }

    // MARK: Activity log (sleep, steps, exercise, meditation)

    private func ingestActivity(_ activityID: String, _ byDay: [Date: Double]) -> Int {
        var wrote = 0
        for (rawDay, rawValue) in byDay where rawValue > 0 {
            let day = rawDay.startOfDay
            let value = (rawValue * 10).rounded() / 10
            let descriptor = FetchDescriptor<ActivityLog>(
                predicate: #Predicate { $0.deletedAt == nil && $0.activityID == activityID && $0.date == day }
            )
            if let existing = (try? context.fetch(descriptor))?.first {
                // Steps/exercise/active-energy change through the day and Apple can
                // revise recent days, so refresh the stored value to the latest.
                // Sleep is left alone (backfill-only): it's the one activity she can
                // type by hand, and `ActivityLog` has no source tag to tell them apart.
                if activityID != "sleep", existing.amount != value {
                    existing.amount = value
                    existing.touch()
                    wrote += 1
                }
            } else {
                context.insert(ActivityLog(date: day, activityID: activityID, amount: value, ownerID: ownerID()))
                wrote += 1
            }
        }
        return wrote
    }

    // MARK: Vitals / workload → HealthSample

    private func ingestVitals(_ series: HealthSnapshot.VitalSeries) -> Int {
        var wrote = 0
        let typeID = series.typeID
        for (rawDay, rawValue) in series.byDay {
            let day = rawDay.startOfDay
            let descriptor = FetchDescriptor<HealthSample>(
                predicate: #Predicate { $0.deletedAt == nil && $0.typeID == typeID && $0.day == day }
            )
            let value = (rawValue * 10).rounded() / 10
            if let existing = (try? context.fetch(descriptor))?.first {
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

    // MARK: Symptoms

    private func ingestSymptoms(_ occurrences: [HealthSnapshot.SymptomOccurrence]) -> (linked: Int, archived: Int) {
        // Collapse to one occurrence per (day, symptom), keeping the worst severity.
        var worst: [String: (day: Date, hkID: String, severity: Int)] = [:]
        for occ in occurrences {
            let day = occ.day.startOfDay
            let key = "\(day.timeIntervalSince1970)|\(occ.hkIdentifier)"
            if let existing = worst[key], existing.severity >= occ.severity { continue }
            worst[key] = (day, occ.hkIdentifier, occ.severity)
        }

        var linked = 0, archived = 0
        for (_, occ) in worst {
            let mapped = Self.symptomMap[occ.hkID] ?? (name: Self.humanize(occ.hkID), category: .body)
            if let checkIn = checkIn(on: occ.day) {
                let symptom = symptoms.findOrCreateCustom(name: mapped.name, category: mapped.category)
                let already = checkIn.symptomLinks.contains { $0.deletedAt == nil && $0.symptom?.id == symptom.id }
                guard !already else { continue }
                context.insert(CheckInSymptom(checkIn: checkIn, symptom: symptom, severity: occ.severity,
                                              source: .healthKit, ownerID: ownerID()))
                linked += 1
            } else {
                let typeID = "symptom." + mapped.name.lowercased().replacingOccurrences(of: " ", with: "_")
                if insertSampleIfAbsent(typeID: typeID, day: occ.day, value: Double(occ.severity), unit: "severity") {
                    archived += 1
                }
            }
        }
        return (linked, archived)
    }

    private func checkIn(on day: Date) -> CheckIn? {
        let start = day.startOfDay
        let end = start.adding(days: 1)
        let descriptor = FetchDescriptor<CheckIn>(
            predicate: #Predicate { $0.deletedAt == nil && $0.date >= start && $0.date < end }
        )
        return (try? context.fetch(descriptor))?.first
    }

    // MARK: Menstrual flow → cycle entries

    private func ingestFlow(_ days: [Date]) -> Int {
        var wrote = 0
        for rawDay in days {
            let day = rawDay.startOfDay
            let end = day.adding(days: 1)
            let descriptor = FetchDescriptor<CycleEntry>(
                predicate: #Predicate { $0.deletedAt == nil && $0.date >= day && $0.date < end }
            )
            guard (try? context.fetch(descriptor))?.first == nil else { continue }
            context.insert(CycleEntry(date: day, type: .periodStart, source: .healthKit, ownerID: ownerID()))
            wrote += 1
        }
        return wrote
    }

    // MARK: Helpers

    private func insertSampleIfAbsent(typeID: String, day: Date, value: Double, unit: String) -> Bool {
        let start = day.startOfDay
        let descriptor = FetchDescriptor<HealthSample>(
            predicate: #Predicate { $0.deletedAt == nil && $0.typeID == typeID && $0.day == start }
        )
        guard (try? context.fetch(descriptor))?.first == nil else { return false }
        context.insert(HealthSample(typeID: typeID, day: start, value: value, unit: unit, ownerID: ownerID()))
        return true
    }

    /// Fallback label for an unmapped HealthKit identifier, e.g.
    /// "HKCategoryTypeIdentifierBreastPain" → "Breast pain".
    private static func humanize(_ hkIdentifier: String) -> String {
        let stripped = hkIdentifier
            .replacingOccurrences(of: "HKCategoryTypeIdentifier", with: "")
            .replacingOccurrences(of: "HKQuantityTypeIdentifier", with: "")
        var result = ""
        for (i, ch) in stripped.enumerated() {
            if i > 0, ch.isUppercase { result += " " }
            result.append(ch)
        }
        return result.prefix(1).uppercased() + result.dropFirst().lowercased()
    }
}
