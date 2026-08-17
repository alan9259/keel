import XCTest
@testable import Keel

/// The cycle arithmetic behind the timeline, history and next-period estimate.
/// A fixed UTC calendar keeps day maths independent of the machine's timezone.
final class CycleStatsTests: XCTestCase {

    private let cal = TestStore.utcCalendar
    private let base = Date(timeIntervalSince1970: 1_600_000_000)

    private func d(_ offset: Int) -> Date {
        cal.date(byAdding: .day, value: offset, to: cal.startOfDay(for: base))!
    }
    private func stats(_ startOffsets: [Int]) -> CycleStats {
        CycleStats(starts: startOffsets.map(d), calendar: cal)
    }

    func testPeriodStartsCollapseConsecutiveDays() {
        // Days 0,1,2 are one period; 28,29 the next → starts at 0 and 28.
        let starts = CycleStats.periodStarts(fromDays: [0, 1, 2, 28, 29].map(d), calendar: cal)
        XCTAssertEqual(starts, [d(0), d(28)])
    }

    func testCycleLengths() {
        XCTAssertEqual(stats([0, 28, 56]).cycleLengths, [28, 28])
        XCTAssertEqual(stats([0, 24, 55]).cycleLengths, [24, 31])
    }

    func testTypicalRange() {
        XCTAssertEqual(stats([0, 24, 55, 79]).typicalRange, 24...31) // lengths 24, 31, 24
        XCTAssertNil(stats([0]).typicalRange)                        // no full cycle yet
    }

    func testCycleDay() {
        let s = stats([0, 28])   // last start at day 28
        XCTAssertEqual(s.cycleDay(on: d(28)), 1)
        XCTAssertEqual(s.cycleDay(on: d(45)), 18)
        XCTAssertNil(s.cycleDay(on: d(27)))      // before the last start
    }

    func testEstimateWhenRecentCyclesAreConsistent() {
        // lengths 27, 28, 29 → spread 2 ≤ 7 → estimate from the last start (day 84).
        let s = stats([0, 27, 55, 84])
        XCTAssertTrue(s.canEstimate)
        let window = s.estimatedWindow()
        XCTAssertEqual(window?.lowerBound, d(84 + 27))
        XCTAssertEqual(window?.upperBound, d(84 + 29))
    }

    func testNoEstimateWhenTooVariable() {
        // lengths 22, 40, 25 → spread 18 > 7 → too variable to forecast.
        let s = stats([0, 22, 62, 87])
        XCTAssertFalse(s.canEstimate)
        XCTAssertNil(s.estimatedWindow())
        // But the history/typical range is still honestly available.
        XCTAssertEqual(s.typicalRange, 22...40)
    }

    func testNoEstimateWithFewerThanThreeCycles() {
        XCTAssertFalse(stats([0, 28]).canEstimate)       // 1 cycle length
        XCTAssertFalse(stats([0, 28, 56]).canEstimate)   // 2 cycle lengths
        XCTAssertFalse(stats([]).canEstimate)            // nothing logged
    }
}
