import XCTest
@testable import Keel

/// The Activities period-view maths: date windows for Day/Week/Month, the no-future
/// navigation clamp, and the total/average roll-ups behind the bar charts.
final class ActivityAggregationTests: XCTestCase {

    private var cal: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        c.firstWeekday = 2 // Monday, so weeks are deterministic
        return c
    }
    private func date(_ y: Int, _ m: Int, _ d: Int) -> Date {
        cal.date(from: DateComponents(year: y, month: m, day: d))!
    }

    // MARK: Windows

    func testDayIntervalIsOneDay() {
        let iv = ActivityAggregation.interval(for: .day, containing: date(2026, 9, 17), calendar: cal)
        XCTAssertEqual(ActivityAggregation.days(in: iv, calendar: cal).count, 1)
    }

    func testWeekIntervalIsSevenDays() {
        let iv = ActivityAggregation.interval(for: .week, containing: date(2026, 9, 17), calendar: cal)
        XCTAssertEqual(ActivityAggregation.days(in: iv, calendar: cal).count, 7)
    }

    func testMonthIntervalCoversWholeMonth() {
        let iv = ActivityAggregation.interval(for: .month, containing: date(2026, 9, 17), calendar: cal)
        XCTAssertEqual(ActivityAggregation.days(in: iv, calendar: cal).count, 30) // September
    }

    // MARK: Roll-ups

    func testRollupTotalSumsPresentDays() {
        let days = [date(2026, 9, 15), date(2026, 9, 16), date(2026, 9, 17)]
        let byDay = [date(2026, 9, 15): 1000.0, date(2026, 9, 17): 3000.0] // 16th missing
        let r = ActivityAggregation.rollup(byDay: byDay, days: days, mode: .total, calendar: cal)
        XCTAssertEqual(r.summary, 4000)
        XCTAssertEqual(r.perDay, [1000, 0, 3000])
        XCTAssertEqual(r.daysWithData, 2)
    }

    func testRollupAverageIgnoresMissingDays() {
        let days = [date(2026, 9, 15), date(2026, 9, 16), date(2026, 9, 17)]
        let byDay = [date(2026, 9, 15): 6.0, date(2026, 9, 17): 8.0] // mean of 6 and 8, not /3
        let r = ActivityAggregation.rollup(byDay: byDay, days: days, mode: .average, calendar: cal)
        XCTAssertEqual(r.summary, 7)
    }

    func testRollupEmptyIsNil() {
        let days = [date(2026, 9, 15), date(2026, 9, 16)]
        let r = ActivityAggregation.rollup(byDay: [:], days: days, mode: .total, calendar: cal)
        XCTAssertNil(r.summary)
        XCTAssertEqual(r.perDay, [0, 0])
    }

    // MARK: Navigation clamp

    func testShiftDayNoFuture() {
        let today = date(2026, 9, 17)
        XCTAssertEqual(ActivityAggregation.shift(today, by: 1, period: .day, calendar: cal, notAfter: today), today)
        XCTAssertEqual(ActivityAggregation.shift(today, by: -1, period: .day, calendar: cal, notAfter: today), date(2026, 9, 16))
    }

    func testShiftWeekPreviousAllowedNextBlocked() {
        let today = date(2026, 9, 17)
        XCTAssertEqual(ActivityAggregation.shift(today, by: 1, period: .week, calendar: cal, notAfter: today), today)
        let prev = ActivityAggregation.shift(today, by: -1, period: .week, calendar: cal, notAfter: today)
        XCTAssertEqual(cal.dateComponents([.day], from: prev, to: today).day, 7)
    }

    func testIsCurrent() {
        let today = date(2026, 9, 17)
        XCTAssertTrue(ActivityAggregation.isCurrent(today, period: .month, calendar: cal, now: today))
        XCTAssertFalse(ActivityAggregation.isCurrent(date(2026, 7, 10), period: .month, calendar: cal, now: today))
    }
}
