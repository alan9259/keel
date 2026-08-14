import XCTest
@testable import Keel

/// Sleep hours must be the union of the night's sample intervals, never the sum of
/// their durations. Apple Watch writes overlapping stage samples, a source may add
/// a whole-night sample over the top, and a second app can cover the night again;
/// summing those double-counts and produced impossible totals (a reported 13.2 hrs).
final class HealthSleepMergeTests: XCTestCase {

    private let base = Date(timeIntervalSince1970: 1_700_000_000)

    /// A range from `startHour` to `endHour` (hours from a fixed base).
    private func r(_ startHour: Double, _ endHour: Double) -> ClosedRange<Date> {
        base.addingTimeInterval(startHour * 3600)...base.addingTimeInterval(endHour * 3600)
    }

    func testEmptyIsZero() {
        XCTAssertEqual(HealthKitService.mergedHours([]), 0, accuracy: 0.0001)
    }

    func testSingleInterval() {
        XCTAssertEqual(HealthKitService.mergedHours([r(0, 8)]), 8, accuracy: 0.0001)
    }

    func testNonOverlappingIntervalsAddUp() {
        // 1h asleep, a gap, then 6.5h asleep → 7.5h.
        XCTAssertEqual(HealthKitService.mergedHours([r(0, 1), r(1.5, 8)]), 7.5, accuracy: 0.0001)
    }

    func testOverlappingStagesAreNotDoubleCounted() {
        // The bug: a whole-night sample (0..8h = 8h) PLUS granular stage samples
        // that all sit inside it. The union is 8h, not 8+1+2.5+3 = 14.5h.
        let ranges = [r(0, 8), r(0, 1), r(1.5, 4), r(5, 8)]
        XCTAssertEqual(HealthKitService.mergedHours(ranges), 8, accuracy: 0.0001)
    }

    func testTwoSourcesCoveringTheSameNight() {
        // Two apps each recorded the full night → still 8h, not 16h.
        XCTAssertEqual(HealthKitService.mergedHours([r(0, 8), r(0, 8)]), 8, accuracy: 0.0001)
    }

    func testAdjacentIntervalsMerge() {
        XCTAssertEqual(HealthKitService.mergedHours([r(0, 4), r(4, 8)]), 8, accuracy: 0.0001)
    }

    func testUnorderedInputIsHandled() {
        XCTAssertEqual(HealthKitService.mergedHours([r(5, 8), r(0, 1), r(0, 8)]), 8, accuracy: 0.0001)
    }

    func testPartialOverlapUnions() {
        // 0..5h and 4..9h overlap by 1h → union is 0..9h = 9h.
        XCTAssertEqual(HealthKitService.mergedHours([r(0, 5), r(4, 9)]), 9, accuracy: 0.0001)
    }
}
