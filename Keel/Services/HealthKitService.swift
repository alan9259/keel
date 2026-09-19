import Foundation
import HealthKit
import OSLog

/// A plain, storage-ready snapshot of what Apple Health gave us, so the ingestor
/// (and the test harness) can consume one shape without touching HealthKit types.
struct HealthSnapshot {
    /// Hours asleep, keyed by the day the sleep ended.
    var sleepByDay: [Date: Double] = [:]
    /// Daily amounts that map onto Keel's activity log, keyed by `activityID`
    /// ("steps", "exercise") then day.
    var activityAmounts: [String: [Date: Double]] = [:]
    /// Vitals and workload with no natural Keel home, stored as `HealthSample`.
    var vitals: [VitalSeries] = []

    struct VitalSeries {
        let typeID: String
        let unit: String
        let byDay: [Date: Double]
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
    private static let log = Logger(subsystem: "com.keel", category: "health")

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
        ("bloodPressureSystolic", .bloodPressureSystolic, .millimeterOfMercury(), "mmHg", true),
        ("bloodPressureDiastolic", .bloodPressureDiastolic, .millimeterOfMercury(), "mmHg", true),
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

    private var readTypes: Set<HKObjectType> {
        // Symptoms, menstrual flow and mindful minutes are deliberately NOT read: Keel no
        // longer imports them from Apple Health (she logs symptoms and cycle in Keel
        // directly). Only sleep, activity and vitals are imported. See HealthIngestor.
        var types = Set<HKObjectType>()
        if let sleep = HKObjectType.categoryType(forIdentifier: .sleepAnalysis) { types.insert(sleep) }
        for (_, id, _) in Self.activityQuantities {
            if let t = HKObjectType.quantityType(forIdentifier: id) { types.insert(t) }
        }
        for entry in Self.vitalQuantities {
            if let t = HKObjectType.quantityType(forIdentifier: entry.id) { types.insert(t) }
        }
        return types
    }

    // MARK: Authorization

    /// Request read access to the whole set. Returns whether the prompt completed
    /// without error. Safe to call again: if access is granted HealthKit doesn't
    /// re-prompt, so this doubles as a silent "am I still connected" check.
    @discardableResult
    func requestAuthorization() async -> Bool {
        guard isAvailable else {
            Self.log.error("HealthKit unavailable on this device (isHealthDataAvailable == false).")
            return false
        }
        do {
            try await store.requestAuthorization(toShare: [], read: readTypes)
            isAuthorized = true
            return true
        } catch {
            // The prompt didn't complete. On a real device the usual cause is the
            // HealthKit capability not being present in the signed build (App ID /
            // provisioning profile), which reads as "Missing com.apple.developer.
            // healthkit entitlement". Log the exact reason so a tester's device
            // console reveals it, instead of failing silently.
            Self.log.error("HealthKit authorization request failed: \(error.localizedDescription, privacy: .public) — \(String(describing: error), privacy: .public)")
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

        for entry in Self.vitalQuantities {
            var byDay = await quantityByDay(entry.id, unit: entry.unit, lastDays: lastDays, average: entry.average)
            if entry.typeID == "oxygenSaturation" { byDay = byDay.mapValues { $0 * 100 } } // fraction → %
            if !byDay.isEmpty {
                snapshot.vitals.append(.init(typeID: entry.typeID, unit: entry.unitLabel, byDay: byDay))
            }
        }

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

    // MARK: Helpers

    private func floor(_ lastDays: Int) -> Date {
        calendar.date(byAdding: .day, value: -max(lastDays, 1), to: Date().startOfDay) ?? Date()
    }
}
