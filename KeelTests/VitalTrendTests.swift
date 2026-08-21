import XCTest
@testable import Keel

/// The "Your body lately" baseline: a real average and direction over her own
/// vitals, plus the honest resting-HR-after-short-sleep observation.
final class VitalTrendTests: XCTestCase {

    private let cal = TestStore.utcCalendar
    private let base = Date(timeIntervalSince1970: 1_600_000_000)
    private func d(_ offset: Int) -> Date {
        cal.startOfDay(for: cal.date(byAdding: .day, value: offset, to: base)!)
    }

    private func trend(_ values: [(Int, Double)]) -> VitalTrend {
        VitalTrend(points: values.map { VitalTrend.Point(day: d($0.0), value: $0.1) })
    }

    func testAverageIsRealAndRounded() {
        XCTAssertEqual(trend([(0, 60), (1, 62), (2, 64)]).average, 62)
        XCTAssertNil(trend([]).average)
    }

    func testDirectionUpDownSteady() {
        // recent half clearly higher → up
        let up = trend([(0, 58), (1, 58), (2, 59), (3, 66), (4, 66), (5, 67)])
        XCTAssertEqual(up.direction, .up)
        // recent half clearly lower → down
        let down = trend([(0, 66), (1, 66), (2, 67), (3, 58), (4, 58), (5, 59)])
        XCTAssertEqual(down.direction, .down)
        // flat → steady
        XCTAssertEqual(trend([(0, 60), (1, 61), (2, 60), (3, 61), (4, 60), (5, 61)]).direction, .steady)
    }

    func testDirectionNilWithTooFewDays() {
        XCTAssertNil(trend([(0, 60), (1, 62), (2, 64)]).direction)
    }

    func testValuesAreInDayOrder() {
        // provided out of order; should come back chronological.
        XCTAssertEqual(trend([(2, 64), (0, 60), (1, 62)]).values, [60, 62, 64])
    }

    // MARK: Resting HR vs sleep

    func testRestingHRHigherAfterShortSleep() {
        var rhr: [Date: Double] = [:], sleep: [Date: Double] = [:]
        for i in 0..<8 {
            let day = d(i)
            let short = i.isMultiple(of: 2)
            rhr[day] = short ? 66 : 60
            sleep[day] = short ? 6.0 : 8.0
        }
        XCTAssertEqual(VitalTrend.restingHeartRateHigherAfterShortSleep(
            restingHRByDay: rhr, sleepHoursByDay: sleep, calendar: cal), true)
    }

    func testNoSleepTieWhenRestingHRIsFlat() {
        var rhr: [Date: Double] = [:], sleep: [Date: Double] = [:]
        for i in 0..<8 {
            rhr[d(i)] = 61 // same regardless of sleep
            sleep[d(i)] = i.isMultiple(of: 2) ? 6.0 : 8.0
        }
        XCTAssertEqual(VitalTrend.restingHeartRateHigherAfterShortSleep(
            restingHRByDay: rhr, sleepHoursByDay: sleep, calendar: cal), false)
    }

    func testSleepTieNilWithoutEnoughPairedDays() {
        let rhr: [Date: Double] = [d(0): 66, d(1): 60]
        let sleep: [Date: Double] = [d(0): 6, d(1): 8]
        XCTAssertNil(VitalTrend.restingHeartRateHigherAfterShortSleep(
            restingHRByDay: rhr, sleepHoursByDay: sleep, calendar: cal))
    }
}
