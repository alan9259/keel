import XCTest
@testable import Keel

/// The resting-heart-rate ↔ sleep detector: a real paired comparison of her own
/// Apple Health resting HR against her sleep, surfaced in Patterns + the daily
/// reflection only when the difference is clear. Built on a fixed UTC calendar so
/// the day-keyed lookups never depend on the machine timezone.
final class PatternEngineTests: XCTestCase {

    private let cal = TestStore.utcCalendar
    private let base = Date(timeIntervalSince1970: 1_600_000_000)
    private func day(_ offset: Int) -> Date {
        cal.startOfDay(for: cal.date(byAdding: .day, value: offset, to: base)!)
    }

    /// An engine with only vitals + sleep populated, so the resting-HR detector is
    /// the only one that can fire (empty check-ins/cycles/symptoms silence the others).
    private func engine(restingHR: [Date: Double] = [:], sleep: [Date: Double] = [:],
                        wristTemp: [Date: Double] = [:],
                        symptomDays: [String: Set<Date>] = [:],
                        dietTriggers: [DietTriggerCorrelation.Input] = []) -> PatternEngine {
        PatternEngine(
            checkIns: [],
            sleepByDay: sleep,
            restingHRByDay: restingHR,
            wristTempByDay: wristTemp,
            symptomDaysByName: symptomDays,
            dietTriggers: dietTriggers,
            periodStarts: [],
            today: day(0),
            calendar: cal)
    }

    private func restingFinding(_ engine: PatternEngine) -> PatternFinding? {
        engine.findings().first { $0.kind == .restingHeartRateSleep }
    }

    func testSurfacesWhenRestingHRClearlyHigherAfterShortSleep() {
        var rhr: [Date: Double] = [:], sleep: [Date: Double] = [:]
        // 4 short-sleep nights (resting HR ~68) and 4 good ones (~60): an 8 bpm gap.
        for i in 0..<8 {
            let short = i.isMultiple(of: 2)
            rhr[day(i)] = short ? 68 : 60
            sleep[day(i)] = short ? 6.0 : 8.0
        }
        let finding = restingFinding(engine(restingHR: rhr, sleep: sleep))
        XCTAssertNotNil(finding)
        // Honest, grounded copy: real day count, no invented number, no dashes.
        XCTAssertEqual(finding?.timeframe, "Seen across 8 days with both logged")
        XCTAssertFalse(finding?.fact.isEmpty ?? true)
        XCTAssertFalse((finding?.detail ?? "").contains("—"))
    }

    func testStaysQuietWhenTheGapIsTrivial() {
        var rhr: [Date: Double] = [:], sleep: [Date: Double] = [:]
        // Only ~1 bpm apart: below the 3 bpm bar for surfacing a narrative.
        for i in 0..<8 {
            let short = i.isMultiple(of: 2)
            rhr[day(i)] = short ? 61 : 60
            sleep[day(i)] = short ? 6.0 : 8.0
        }
        XCTAssertNil(restingFinding(engine(restingHR: rhr, sleep: sleep)))
    }

    func testStaysQuietWithoutEnoughPairedDays() {
        // Two short + two good nights is not enough to say anything.
        let rhr: [Date: Double] = [day(0): 68, day(1): 60, day(2): 69, day(3): 61]
        let sleep: [Date: Double] = [day(0): 6, day(1): 8, day(2): 6, day(3): 8]
        XCTAssertNil(restingFinding(engine(restingHR: rhr, sleep: sleep)))
    }

    func testNoVitalsMeansNoFinding() {
        XCTAssertNil(restingFinding(engine(restingHR: [:], sleep: [:])))
    }

    // MARK: Wrist temperature ↔ sleep

    func testSurfacesWhenOvernightTemperatureWarmerAfterShortSleep() {
        var temp: [Date: Double] = [:], sleep: [Date: Double] = [:]
        // 4 short-sleep nights (~35.6°C) and 4 good ones (~35.1°C): a 0.5°C gap.
        for i in 0..<8 {
            let short = i.isMultiple(of: 2)
            temp[day(i)] = short ? 35.6 : 35.1
            sleep[day(i)] = short ? 6.0 : 8.0
        }
        let finding = engine(sleep: sleep, wristTemp: temp)
            .findings().first { $0.kind == .wristTemperatureSleep }
        XCTAssertNotNil(finding)
        XCTAssertFalse((finding?.detail ?? "").contains("—"))
    }

    func testTemperatureStaysQuietWhenGapTiny() {
        var temp: [Date: Double] = [:], sleep: [Date: Double] = [:]
        for i in 0..<8 {
            let short = i.isMultiple(of: 2)
            temp[day(i)] = short ? 35.15 : 35.1 // 0.05°C — below the 0.2°C bar
            sleep[day(i)] = short ? 6.0 : 8.0
        }
        XCTAssertNil(engine(sleep: sleep, wristTemp: temp)
            .findings().first { $0.kind == .wristTemperatureSleep })
    }

    // MARK: Diet trigger ↔ vasomotor symptoms

    func testDietTriggerSurfacesWhenSymptomsClusterOnTriggerDays() {
        let alcohol = DietTriggerCorrelation.Input(
            label: "Alcohol",
            yes: [day(0), day(-1), day(-2), day(-3)],   // 3 of 4 will have hot flushes
            no: [day(-10), day(-11), day(-12), day(-13)]) // none do
        let symptomDays: [String: Set<Date>] = ["Hot flushes": [day(0), day(-1), day(-2)]]
        let finding = engine(symptomDays: symptomDays, dietTriggers: [alcohol])
            .findings().first { $0.kind == .dietTrigger }
        XCTAssertNotNil(finding)
        XCTAssertTrue(finding?.title.contains("Alcohol") ?? false)
        XCTAssertFalse((finding?.detail ?? "").contains("—"))
    }

    // MARK: Recurring symptom folds Apple Health logs

    func testRecurringSymptomCountsAppleHealthOnlyDays() {
        // Three hot-flush days that came only from Apple Health (no check-ins) still
        // surface as her most-logged symptom.
        let symptomDays = ["Hot flushes": Set([day(0), day(-1), day(-2)])]
        let finding = engine(restingHR: [:], sleep: [:], symptomDays: symptomDays)
            .findings().first { $0.kind == .recurringSymptom }
        XCTAssertNotNil(finding)
        XCTAssertTrue(finding?.detail.contains("hot flushes") ?? false)
        XCTAssertTrue(finding?.detail.contains("3") ?? false)
    }
}
