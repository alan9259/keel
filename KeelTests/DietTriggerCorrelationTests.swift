import XCTest
@testable import Keel

/// The yes-vs-no trigger comparison: it surfaces only a clear, well-populated lift,
/// counts days she actually logged (null days are simply absent), and picks the
/// strongest of several triggers.
final class DietTriggerCorrelationTests: XCTestCase {

    private let cal = TestStore.utcCalendar
    private let base = Date(timeIntervalSince1970: 1_600_000_000)
    private func d(_ offset: Int) -> Date { cal.startOfDay(for: cal.date(byAdding: .day, value: offset, to: base)!) }

    func testSurfacesAClearLift() {
        // Alcohol: 3 of 4 yes-days had symptoms; 1 of 5 no-days did.
        let alcohol = DietTriggerCorrelation.Input(
            label: "Alcohol",
            yes: [d(0), d(1), d(2), d(3)],
            no: [d(10), d(11), d(12), d(13), d(14)])
        let symptomDays: Set<Date> = [d(0), d(1), d(2), d(10)]
        let result = DietTriggerCorrelation.strongest([alcohol], symptomDays: symptomDays)
        XCTAssertEqual(result?.label, "Alcohol")
        XCTAssertEqual(result?.yesHit, 3)
        XCTAssertEqual(result?.yesTotal, 4)
        XCTAssertEqual(result?.noHit, 1)
        XCTAssertEqual(result?.noTotal, 5)
        XCTAssertGreaterThan(result?.lift ?? 0, 0.2)
    }

    func testQuietWithTooFewDays() {
        // Only two no-days is not a real comparison.
        let alcohol = DietTriggerCorrelation.Input(label: "Alcohol", yes: [d(0), d(1), d(2)], no: [d(3), d(4)])
        XCTAssertNil(DietTriggerCorrelation.strongest([alcohol], symptomDays: [d(0), d(1), d(2)]))
    }

    func testQuietWhenRatesAreEqual() {
        // Enough days, but symptoms are just as common off the trigger as on it.
        let caffeine = DietTriggerCorrelation.Input(label: "Caffeine", yes: [d(0), d(1), d(2), d(3)], no: [d(4), d(5), d(6), d(7)])
        let symptomDays: Set<Date> = [d(0), d(1), d(4), d(5)] // 2/4 vs 2/4
        XCTAssertNil(DietTriggerCorrelation.strongest([caffeine], symptomDays: symptomDays))
    }

    func testPicksTheStrongestOfSeveral() {
        let alcohol = DietTriggerCorrelation.Input(label: "Alcohol", yes: [d(0), d(1), d(2), d(3)], no: [d(10), d(11), d(12)])
        let caffeine = DietTriggerCorrelation.Input(label: "Caffeine", yes: [d(4), d(5), d(6)], no: [d(13), d(14), d(15)])
        // alcohol 4/4 vs 0/3 (lift 1.0); caffeine 2/3 vs 1/3 (lift ~0.33)
        let symptomDays: Set<Date> = [d(0), d(1), d(2), d(3), d(4), d(5), d(13)]
        XCTAssertEqual(DietTriggerCorrelation.strongest([alcohol, caffeine], symptomDays: symptomDays)?.label, "Alcohol")
    }
}
