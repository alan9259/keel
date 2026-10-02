import XCTest
@testable import Keel

/// A5: the Stats sentence and cards count days, not entries, so they agree.
final class StatsDayCountsTests: XCTestCase {
    private let cal = TestStore.utcCalendar
    private func day(_ n: Int) -> Date { cal.date(byAdding: .day, value: n, to: Date(timeIntervalSince1970: 1_700_000_000))! }

    /// Regression: 15 entries on 7 days read "checked in on 7 of 30 days" above a card
    /// saying "15 of 30 days". Both are now 7.
    func testSeveralEntriesOnOneDayCountOnce() {
        var entries: [(day: Date, hasSymptoms: Bool)] = []
        for d in 0..<7 { entries.append((day(d), false)); if d < 8 { entries.append((day(d), false)) } }
        entries.append((day(0), false))
        XCTAssertEqual(entries.count, 15)
        XCTAssertEqual(StatsDayCounts(entries: entries).checkInDays, 7)
    }

    func testNoSymptomsLoggedIsPerDay() {
        let counts = StatsDayCounts(entries: [
            (day(0), false), (day(0), true),   // a symptom that day: not a no-symptom day
            (day(1), false), (day(1), false),  // nothing logged that day: counts once
            (day(2), true),
        ])
        XCTAssertEqual(counts.checkInDays, 3)
        XCTAssertEqual(counts.noSymptomDays, 1)
    }

    func testEmpty() {
        XCTAssertEqual(StatsDayCounts(entries: []), StatsDayCounts(entries: []))
        XCTAssertEqual(StatsDayCounts(entries: []).checkInDays, 0)
    }
}
