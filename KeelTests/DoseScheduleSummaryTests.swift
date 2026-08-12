import XCTest
@testable import Keel

/// Human-readable schedule wording (shown on the Medications screen, reports, and
/// the GP summary). Only the locale-stable parts are asserted here: the weekday
/// phrase and the list joiner. The clock-time portion is formatted with the
/// device locale, so it isn't pinned in tests.
final class DoseScheduleSummaryTests: XCTestCase {

    func testPhraseNamedSets() {
        XCTAssertEqual(DoseSchedule.phrase(for: Set(1...7)), "Every day")
        XCTAssertEqual(DoseSchedule.phrase(for: []), "Every day")          // empty means every day
        XCTAssertEqual(DoseSchedule.phrase(for: [2, 3, 4, 5, 6]), "Weekdays")
        XCTAssertEqual(DoseSchedule.phrase(for: [1, 7]), "Weekends")
    }

    func testListJoiner() {
        XCTAssertEqual(DoseSchedule.list(["a"]), "a")
        XCTAssertEqual(DoseSchedule.list(["a", "b"]), "a and b")
        XCTAssertEqual(DoseSchedule.list(["a", "b", "c"]), "a, b and c")
        XCTAssertEqual(DoseSchedule.list([]), "")
    }

    func testSummaryEveryDayNoTime() {
        let s = DoseSchedule(kind: .weekly, slots: [DoseSlot(weekdays: Set(1...7))])
        XCTAssertEqual(s.summary, "Every day")
    }

    func testSummaryWeekdaysNoTime() {
        let s = DoseSchedule(kind: .weekly, slots: [DoseSlot(weekdays: [2, 3, 4, 5, 6])])
        XCTAssertEqual(s.summary, "Weekdays")
    }

    func testSummaryCyclePrefix() {
        let s = DoseSchedule(kind: .cycle, slots: [DoseSlot(weekdays: Set(1...7))],
                             cycleLength: 28, pauseDays: 7)
        XCTAssertEqual(s.activeDays, 21)
        XCTAssertEqual(s.summary, "21 days on, 7 off")
    }

    func testSummaryAsDirected() {
        let s = DoseSchedule(kind: .asNeeded, slots: [DoseSlot(weekdays: Set(1...7))])
        XCTAssertEqual(s.summary, "As directed")
    }

    func testSummaryWithTimeIncludesPhraseAndAt() {
        let s = DoseSchedule(kind: .weekly, slots: [DoseSlot(weekdays: Set(1...7), hour: 8)])
        // Don't pin the clock string (locale-formatted); do pin the structure.
        XCTAssertTrue(s.summary.hasPrefix("Every day at "), s.summary)
    }

    func testWeekdaysUnionAcrossSlots() {
        let s = DoseSchedule(kind: .weekly, slots: [
            DoseSlot(weekdays: [2], hour: 8),
            DoseSlot(weekdays: [4, 6], hour: 20),
        ])
        XCTAssertEqual(s.weekdays, [2, 4, 6])
    }
}
