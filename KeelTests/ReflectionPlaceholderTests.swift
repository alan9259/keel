import XCTest
@testable import Keel

/// The "not enough to reflect on yet" note shown on the Patterns screen. It's a
/// live, non-persisted note (so it can't repeat across days in "Looking back"),
/// and it must be accurate about how many days she's logged — never "a handful"
/// when it's one (a reported bug).
final class ReflectionPlaceholderTests: XCTestCase {

    func testZeroDaysInvitesHerToStart() {
        let text = DailySummaryService.placeholderReflection(loggedDays: 0)
        XCTAssertTrue(text.localizedCaseInsensitiveContains("check in"))
    }

    func testOneDayIsNotAHandful() {
        let text = DailySummaryService.placeholderReflection(loggedDays: 1)
        XCTAssertTrue(text.contains("first day"))
        XCTAssertFalse(text.localizedCaseInsensitiveContains("handful"))
        XCTAssertFalse(text.localizedCaseInsensitiveContains("few days"))
    }

    func testAFewDays() {
        let text = DailySummaryService.placeholderReflection(loggedDays: 3)
        XCTAssertTrue(text.localizedCaseInsensitiveContains("a few days"))
    }

    func testEnoughDaysButNoPattern() {
        let text = DailySummaryService.placeholderReflection(loggedDays: 12)
        XCTAssertTrue(text.localizedCaseInsensitiveContains("nothing stands out"))
    }

    func testEachStateIsDistinct() {
        let texts = [0, 1, 3, 12].map { DailySummaryService.placeholderReflection(loggedDays: $0) }
        XCTAssertEqual(Set(texts).count, 4, "each day-count band should read differently")
    }
}
