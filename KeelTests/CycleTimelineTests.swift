import XCTest
@testable import Keel

/// The cycle timeline's day window, including the empty state: with no logged period
/// the top pane still shows a recent, tappable week instead of disappearing.
@MainActor
final class CycleTimelineTests: XCTestCase {

    private let cal = TestStore.utcCalendar
    private func day(_ year: Int, _ month: Int, _ d: Int) -> Date {
        cal.date(from: DateComponents(year: year, month: month, day: d))!
    }

    func testEmptyStateWindowIsARecentWeekEndingToday() {
        let today = day(2026, 8, 25)
        let bounds = CycleTrackingView.railBounds(lastStart: nil, estimateEnd: nil, today: today, calendar: cal)
        XCTAssertEqual(bounds.start, cal.date(byAdding: .day, value: -6, to: today))
        XCTAssertEqual(bounds.end, cal.startOfDay(for: today))
        // Seven real, tappable days (today-6 ... today), so the pane is never blank.
        let span = cal.dateComponents([.day], from: bounds.start, to: bounds.end).day! + 1
        XCTAssertEqual(span, 7)
    }

    func testWithDataSpansCycleStartToTheEstimate() {
        let today = day(2026, 8, 25)
        let start = day(2026, 8, 5)
        let estimateEnd = day(2026, 9, 2)
        let bounds = CycleTrackingView.railBounds(lastStart: start, estimateEnd: estimateEnd, today: today, calendar: cal)
        XCTAssertEqual(bounds.start, cal.startOfDay(for: start))
        XCTAssertEqual(bounds.end, cal.date(byAdding: .day, value: 1, to: estimateEnd))
    }

    func testWithDataButNoEstimateEndsJustAfterToday() {
        let today = day(2026, 8, 25)
        let start = day(2026, 8, 20)
        let bounds = CycleTrackingView.railBounds(lastStart: start, estimateEnd: nil, today: today, calendar: cal)
        // max(today, today+3) + 1 = today + 4
        XCTAssertEqual(bounds.end, cal.date(byAdding: .day, value: 4, to: today))
    }
}
