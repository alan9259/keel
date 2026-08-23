import XCTest
@testable import Keel

/// Merging symptom days across her check-ins and Apple Health's own logs: same day,
/// two sources → counted once; vasomotor union; and the `symptom.*` typeID bridge.
final class SymptomTallyTests: XCTestCase {

    private let cal = TestStore.utcCalendar
    private let base = Date(timeIntervalSince1970: 1_600_000_000)
    private func day(_ offset: Int) -> Date {
        cal.startOfDay(for: cal.date(byAdding: .day, value: offset, to: base)!)
    }

    func testSameDayFromTwoSourcesCountsOnce() {
        var t = SymptomTally()
        t.add(name: "Hot flushes", day: day(0)) // from a check-in
        t.add(name: "Hot flushes", day: day(0)) // same day from Apple Health
        t.add(name: "Hot flushes", day: day(1))
        XCTAssertEqual(t.daysByName["Hot flushes"]?.count, 2)
    }

    func testRankedOrdersByDaysThenName() {
        var t = SymptomTally()
        for i in 0..<3 { t.add(name: "Headache", day: day(i)) }
        t.add(name: "Bloating", day: day(0))
        t.add(name: "Nausea", day: day(0)) // tie with Bloating at 1 → alphabetical
        let ranked = t.ranked()
        XCTAssertEqual(ranked.map(\.name), ["Headache", "Bloating", "Nausea"])
        XCTAssertEqual(ranked.first?.days, 3)
    }

    func testVasomotorUnionCountsAMixedDayOnce() {
        var t = SymptomTally()
        t.add(name: "Hot flushes", day: day(0))
        t.add(name: "Night sweats", day: day(0)) // same day, both vasomotor → 1 day
        t.add(name: "Night sweats", day: day(1))
        t.add(name: "Headache", day: day(2))     // not vasomotor
        XCTAssertEqual(t.vasomotorDays, 2)
    }

    func testHealthTypeIDBridgeRoundTrips() {
        XCTAssertEqual(SymptomTally.healthTypeID(forName: "Hot flushes"), "symptom.hot_flushes")
        XCTAssertEqual(SymptomTally.name(fromHealthTypeID: "symptom.hot_flushes"), "Hot flushes")
        XCTAssertEqual(SymptomTally.name(fromHealthTypeID: "symptom.night_sweats"), "Night sweats")
        XCTAssertEqual(SymptomTally.name(fromHealthTypeID: "symptom.fatigue_or_low_energy"), "Fatigue or low energy")
        XCTAssertNil(SymptomTally.name(fromHealthTypeID: "restingHeartRate"))
    }
}
