import Foundation
import HealthKit

/// A plain, storage-ready snapshot of what Apple Health gave us, so the ingestor
/// (and the test harness) can consume one shape without touching HealthKit types.
struct HealthSnapshot {
    /// Hours asleep, keyed by the day the sleep ended.
    var sleepByDay: [Date: Double] = [:]
    /// Daily amounts that map onto Keel's activity log, keyed by `activityID`
    /// ("steps", "exercise", "meditation") then day.
    var activityAmounts: [String: [Date: Double]] = [:]
    /// Vitals and workload with no natural Keel home, stored as `HealthSample`.
    var vitals: [VitalSeries] = []
    /// Symptom occurrences from Health's own Symptoms category.
    var symptoms: [SymptomOccurrence] = []
    /// Days Apple Health recorded menstrual flow (any level but "none").
    /// Menstrual-flow days from Apple Health, keyed to the heaviness she recorded.
    var menstrualFlow: [Date: FlowLevel] = [:]

    struct VitalSeries {
        let typeID: String
        let unit: String
        let byDay: [Date: Double]
    }
    struct SymptomOccurrence {
        let day: Date
        /// Raw HealthKit identifier, e.g. "HKCategoryTypeIdentifierHotFlashes".
        let hkIdentifier: String
        /// 1 mild … 3 severe.
        let severity: Int
    }
}

/// Reads a broad, perimenopause-relevant slice of Apple Health so Keel can learn
/// with less manual logging: sleep, activity, vitals, body temperatures,
/// menstrual flow, and Health's own symptoms (hot flushes, night sweats, mood
/// changes, and the rest). Read-only. Real reads need the HealthKit entitlement
/// on a signed device; on the unsigned Simulator authorization fails and every
/// query returns empty, so the ingestion logic is exercised with synthetic data.
@MainActor
@Observable
final class HealthKitService {
    private let store = HKHealthStore()
    private let calendar = Calendar.current

    private(set) var isAuthorized = false

    var isAvailable: Bool { HKHealthStore.isHealthDataAvailable() }

    // MARK: Import configuration

    /// Quantity types that map onto Keel's activity log (summed per day).
    private static let activityQuantities: [(activityID: String, id: HKQuantityTypeIdentifier, unit: HKUnit)] = [
        ("steps", .stepCount, .count()),
        ("exercise", .appleExerciseTime, .minute()),
    ]

    /// Vitals / workload stored as `HealthSample`. `average` averages the day's
    /// samples (a heart rate); otherwise they are summed (energy, flights).
    private static let vitalQuantities: [(typeID: String, id: HKQuantityTypeIdentifier, unit: HKUnit, unitLabel: String, average: Bool)] = [
        ("heartRate", .heartRate, .count().unitDivided(by: .minute()), "bpm", true),
        ("restingHeartRate", .restingHeartRate, .count().unitDivided(by: .minute()), "bpm", true),
        ("hrv", .heartRateVariabilitySDNN, .secondUnit(with: .milli), "ms", true),
        ("respiratoryRate", .respiratoryRate, .count().unitDivided(by: .minute()), "brpm", true),
        ("oxygenSaturation", .oxygenSaturation, .percent(), "%", true),
        ("bodyMass", .bodyMass, .gramUnit(with: .kilo), "kg", true),
        ("bodyTemperature", .bodyTemperature, .degreeCelsius(), "°C", true),
        ("wristTemperature", .appleSleepingWristTemperature, .degreeCelsius(), "°C", true),
        ("basalBodyTemperature", .basalBodyTemperature, .degreeCelsius(), "°C", true),
        ("activeEnergy", .activeEnergyBurned, .kilocalorie(), "kcal", false),
        ("flights", .flightsClimbed, .count(), "count", false),
        ("distance", .distanceWalkingRunning, .meterUnit(with: .kilo), "km", false),
    ]

    /// Health's own Symptoms category. These merge into Keel's symptom catalog.
    private static let symptomIdentifiers: [HKCategoryTypeIdentifier] = [
        .hotFlashes, .nightSweats, .moodChanges, .fatigue, .headache, .sleepChanges,
        .vaginalDryness, .memoryLapse, .rapidPoundingOrFlutteringHeartbeat, .dizziness,
        .bloating, .nausea, .constipation, .heartburn, .appetiteChanges, .drySkin,
        .hairLoss, .lowerBackPain, .generalizedBodyAche, .chills, .breastPain, .pelvicPain,
    ]

    private var readTypes: Set<HKObjectType> {
        var types = Set<HKObjectType>()
        if let sleep = HKObjectType.categoryType(forIdentifier: .sleepAnalysis) { types.insert(sleep) }
        if let mindful = HKObjectType.categoryType(forIdentifier: .mindfulSession) { types.insert(mindful) }
        if let flow = HKObjectType.categoryType(forIdentifier: .menstrualFlow) { types.insert(flow) }
        for (_, id, _) in Self.activityQuantities {
            if let t = HKObjectType.quantityType(forIdentifier: id) { types.insert(t) }
        }
        for entry in Self.vitalQuantities {
            if let t = HKObjectType.quantityType(forIdentifier: entry.id) { types.insert(t) }
        }
        for id in Self.symptomIdentifiers {
            if let t = HKObjectType.categoryType(forIdentifier: id) { types.insert(t) }
        }
        return types
    }

    // MARK: Authorization

    /// Request read access to the whole set. Returns whether the prompt completed
    /// without error. Safe to call again: if access is granted HealthKit doesn't
    /// re-prompt, so this doubles as a silent "am I still connected" check.
    @discardableResult
    func requestAuthorization() async -> Bool {
        guard isAvailable else { return false }
        do {
            try await store.requestAuthorization(toShare: [], read: readTypes)
            isAuthorized = true
            return true
        } catch {
            return false
        }
    }

    // MARK: Snapshot

    /// Gather everything we import over the last `lastDays` into one plain struct.
    func snapshot(lastDays: Int) async -> HealthSnapshot {
        guard isAvailable else { return HealthSnapshot() }
        var snapshot = HealthSnapshot()

        snapshot.sleepByDay = await sleepHoursByDay(lastDays: lastDays)

        for (activityID, id, unit) in Self.activityQuantities {
            let byDay = await quantityByDay(id, unit: unit, lastDays: lastDays, average: false)
            if !byDay.isEmpty { snapshot.activityAmounts[activityID] = byDay }
        }
        let mindful = await mindfulMinutesByDay(lastDays: lastDays)
        if !mindful.isEmpty { snapshot.activityAmounts["meditation"] = mindful }

        for entry in Self.vitalQuantities {
            var byDay = await quantityByDay(entry.id, unit: entry.unit, lastDays: lastDays, average: entry.average)
            if entry.typeID == "oxygenSaturation" { byDay = byDay.mapValues { $0 * 100 } } // fraction → %
            if !byDay.isEmpty {
                snapshot.vitals.append(.init(typeID: entry.typeID, unit: entry.unitLabel, byDay: byDay))
            }
        }

        snapshot.symptoms = await symptomOccurrences(lastDays: lastDays)
        snapshot.menstrualFlow = await menstrualFlow(lastDays: lastDays)
        return snapshot
    }

    // MARK: Sleep

    /// Hours actually asleep per night, keyed by the day the sleep ended (so a
    /// Mon-night → Tue-morning sleep counts as Tuesday's "sleep last night").
    func sleepHoursByDay(lastDays: Int) async -> [Date: Double] {
        guard isAvailable, let type = HKObjectType.categoryType(forIdentifier: .sleepAnalysis) else { return [:] }
        let predicate = HKQuery.predicateForSamples(withStart: floor(lastDays), end: Date(), options: [])
        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(sampleType: type, predicate: predicate,
                                      limit: HKObjectQueryNoLimit, sortDescriptors: nil) { [calendar] _, samples, _ in
                // Collect the raw asleep and in-bed intervals per night. We must NOT
                // sum sample durations: Apple Watch writes many overlapping stage
                // samples (core/deep/REM), a source may also write a whole-night
                // `asleepUnspecified` over the top, and a second app or manual entry
                // can cover the same night again. Summing those double-counts and
                // produces impossible totals (e.g. 13 hours). Merge overlapping
                // intervals so each minute of the night is counted once.
                var asleep: [Date: [ClosedRange<Date>]] = [:]
                var inBed: [Date: [ClosedRange<Date>]] = [:]
                let inBedValue = HKCategoryValueSleepAnalysis.inBed.rawValue
                for sample in (samples as? [HKCategorySample]) ?? [] {
                    guard sample.endDate > sample.startDate else { continue }
                    let day = calendar.startOfDay(for: sample.endDate)
                    let interval = sample.startDate...sample.endDate
                    if Self.isAsleep(sample.value) { asleep[day, default: []].append(interval) }
                    else if sample.value == inBedValue { inBed[day, default: []].append(interval) }
                }
                // Prefer measured "asleep" time; fall back to "in bed" for days that
                // only have that (e.g. sleep added by hand in the Health app), so
                // those still count instead of vanishing.
                var perDay: [Date: Double] = [:]
                for (day, ranges) in inBed { perDay[day] = Self.mergedHours(ranges) }
                for (day, ranges) in asleep { perDay[day] = Self.mergedHours(ranges) }
                continuation.resume(returning: perDay)
            }
            store.execute(query)
        }
    }

    /// Only the "asleep" categories count as sleep (not "in bed" or "awake").
    nonisolated private static func isAsleep(_ value: Int) -> Bool {
        [HKCategoryValueSleepAnalysis.asleepUnspecified,
         .asleepCore, .asleepDeep, .asleepREM].map(\.rawValue).contains(value)
    }

    /// Total hours covered by the union of the given time ranges: overlapping
    /// samples are counted once, so multi-source or staged sleep isn't summed twice.
    nonisolated static func mergedHours(_ ranges: [ClosedRange<Date>]) -> Double {
        let sorted = ranges.sorted { $0.lowerBound < $1.lowerBound }
        var total: TimeInterval = 0
        var current: (start: Date, end: Date)?
        for range in sorted {
            if var open = current, range.lowerBound <= open.end {
                if range.upperBound > open.end { open.end = range.upperBound }
                current = open
            } else {
                if let open = current { total += open.end.timeIntervalSince(open.start) }
                current = (range.lowerBound, range.upperBound)
            }
        }
        if let open = current { total += open.end.timeIntervalSince(open.start) }
        return total / 3600
    }

    // MARK: Quantity aggregation

    /// Sum (or average) a quantity type per calendar day of its start.
    private func quantityByDay(_ id: HKQuantityTypeIdentifier, unit: HKUnit,
                               lastDays: Int, average: Bool) async -> [Date: Double] {
        guard let type = HKObjectType.quantityType(forIdentifier: id) else { return [:] }
        let predicate = HKQuery.predicateForSamples(withStart: floor(lastDays), end: Date(), options: [])
        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(sampleType: type, predicate: predicate,
                                      limit: HKObjectQueryNoLimit, sortDescriptors: nil) { [calendar] _, samples, _ in
                var totals: [Date: Double] = [:]
                var counts: [Date: Int] = [:]
                for sample in (samples as? [HKQuantitySample]) ?? [] {
                    let day = calendar.startOfDay(for: sample.startDate)
                    totals[day, default: 0] += sample.quantity.doubleValue(for: unit)
                    counts[day, default: 0] += 1
                }
                if average {
                    for (day, total) in totals { totals[day] = total / Double(counts[day] ?? 1) }
                }
                continuation.resume(returning: totals)
            }
            store.execute(query)
        }
    }

    // MARK: Mindfulness

    private func mindfulMinutesByDay(lastDays: Int) async -> [Date: Double] {
        guard let type = HKObjectType.categoryType(forIdentifier: .mindfulSession) else { return [:] }
        let predicate = HKQuery.predicateForSamples(withStart: floor(lastDays), end: Date(), options: [])
        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(sampleType: type, predicate: predicate,
                                      limit: HKObjectQueryNoLimit, sortDescriptors: nil) { [calendar] _, samples, _ in
                var perDay: [Date: Double] = [:]
                for sample in samples ?? [] {
                    let day = calendar.startOfDay(for: sample.startDate)
                    perDay[day, default: 0] += sample.endDate.timeIntervalSince(sample.startDate) / 60
                }
                continuation.resume(returning: perDay)
            }
            store.execute(query)
        }
    }

    // MARK: Symptoms

    private func symptomOccurrences(lastDays: Int) async -> [HealthSnapshot.SymptomOccurrence] {
        var out: [HealthSnapshot.SymptomOccurrence] = []
        for id in Self.symptomIdentifiers {
            out.append(contentsOf: await symptomOccurrences(id, lastDays: lastDays))
        }
        return out
    }

    private func symptomOccurrences(_ id: HKCategoryTypeIdentifier, lastDays: Int) async -> [HealthSnapshot.SymptomOccurrence] {
        guard let type = HKObjectType.categoryType(forIdentifier: id) else { return [] }
        let predicate = HKQuery.predicateForSamples(withStart: floor(lastDays), end: Date(), options: [])
        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(sampleType: type, predicate: predicate,
                                      limit: HKObjectQueryNoLimit, sortDescriptors: nil) { [calendar] _, samples, _ in
                var out: [HealthSnapshot.SymptomOccurrence] = []
                for sample in (samples as? [HKCategorySample]) ?? [] {
                    guard let severity = Self.severity(from: sample.value) else { continue }
                    let day = calendar.startOfDay(for: sample.startDate)
                    out.append(.init(day: day, hkIdentifier: id.rawValue, severity: severity))
                }
                continuation.resume(returning: out)
            }
            store.execute(query)
        }
    }

    /// Map Health's severity scale to Keel's 1–3, dropping explicit "not present".
    nonisolated private static func severity(from value: Int) -> Int? {
        guard let severity = HKCategoryValueSeverity(rawValue: value) else { return 1 }
        switch severity {
        case .notPresent: return nil
        case .mild: return 1
        case .moderate: return 2
        case .severe: return 3
        default: return 1 // unspecified but logged: treat as a mild occurrence
        }
    }

    // MARK: Menstrual flow

    private func menstrualFlow(lastDays: Int) async -> [Date: FlowLevel] {
        guard let type = HKObjectType.categoryType(forIdentifier: .menstrualFlow) else { return [:] }
        let predicate = HKQuery.predicateForSamples(withStart: floor(lastDays), end: Date(), options: [])
        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(sampleType: type, predicate: predicate,
                                      limit: HKObjectQueryNoLimit, sortDescriptors: nil) { [calendar] _, samples, _ in
                var byDay: [Date: FlowLevel] = [:]
                for sample in (samples as? [HKCategorySample]) ?? [] {
                    guard let level = Self.flowLevel(from: sample.value) else { continue } // skips "none"
                    let day = calendar.startOfDay(for: sample.startDate)
                    // If several samples fall on a day, keep the heaviest.
                    if let existing = byDay[day], existing.intensity >= level.intensity { continue }
                    byDay[day] = level
                }
                continuation.resume(returning: byDay)
            }
            store.execute(query)
        }
    }

    /// Map Apple Health's bleeding level to ours; `none` returns nil (not a period day).
    nonisolated private static func flowLevel(from value: Int) -> FlowLevel? {
        switch HKCategoryValueVaginalBleeding(rawValue: value) {
        case .light: return .light
        case .medium: return .medium
        case .heavy: return .heavy
        case .unspecified: return .unspecified
        default: return nil // .none or unknown
        }
    }

    // MARK: Helpers

    private func floor(_ lastDays: Int) -> Date {
        calendar.date(byAdding: .day, value: -max(lastDays, 1), to: Date().startOfDay) ?? Date()
    }
}
