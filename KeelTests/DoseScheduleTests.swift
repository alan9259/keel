import XCTest
@testable import Keel

/// Rules for when a medication is due and which doses fall on a day. These drive
/// the home Medicines log, adherence, and reminder scheduling, so a regression
/// here quietly breaks all three. Uses a fixed UTC Gregorian calendar and fixed
/// dates so nothing depends on the machine's timezone or "today".
final class DoseScheduleTests: XCTestCase {

    private var cal: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }()

    private func date(_ y: Int, _ m: Int, _ d: Int, _ hh: Int = 0, _ mm: Int = 0) -> Date {
        cal.date(from: DateComponents(year: y, month: m, day: d, hour: hh, minute: mm))!
    }

    // MARK: asNeeded

    func testAsNeededAlwaysDueButSchedulesNothing() {
        let s = DoseSchedule(kind: .asNeeded, slots: [DoseSlot(hour: 8)])
        XCTAssertTrue(s.isDue(on: date(2026, 8, 12), calendar: cal))
        // asNeeded never raises reminders, even with a timed dose.
        let occ = NotificationService.plannedOccurrences(schedule: s, now: date(2026, 8, 12, 6), calendar: cal)
        XCTAssertTrue(occ.isEmpty)
    }

    // MARK: weekly

    func testWeeklyEveryDayIsAlwaysDue() {
        let s = DoseSchedule(kind: .weekly, slots: [DoseSlot(weekdays: Set(1...7), hour: 8)])
        for day in 12...18 {
            XCTAssertTrue(s.isDue(on: date(2026, 8, day), calendar: cal), "day \(day)")
        }
    }

    func testWeeklySpecificDaysDueOnlyThose() {
        // 2026-08-12 is a Wednesday (weekday 4). Mon=2, Wed=4, Fri=6.
        let s = DoseSchedule(kind: .weekly, slots: [DoseSlot(weekdays: [2, 4, 6], hour: 8)])
        XCTAssertEqual(cal.component(.weekday, from: date(2026, 8, 12)), 4) // sanity: Wed
        XCTAssertTrue(s.isDue(on: date(2026, 8, 12), calendar: cal))  // Wed
        XCTAssertFalse(s.isDue(on: date(2026, 8, 13), calendar: cal)) // Thu
        XCTAssertTrue(s.isDue(on: date(2026, 8, 14), calendar: cal))  // Fri
        XCTAssertFalse(s.isDue(on: date(2026, 8, 15), calendar: cal)) // Sat
    }

    func testDueSlotsFilterByWeekday() {
        // Morning every day; an extra evening dose only on weekends (Sun=1, Sat=7).
        let morning = DoseSlot(weekdays: Set(1...7), hour: 8)
        let weekendEve = DoseSlot(weekdays: [1, 7], hour: 20)
        let s = DoseSchedule(kind: .weekly, slots: [morning, weekendEve])
        XCTAssertEqual(s.dueSlots(on: date(2026, 8, 12), calendar: cal).count, 1) // Wed: morning only
        XCTAssertEqual(s.dueSlots(on: date(2026, 8, 15), calendar: cal).count, 2) // Sat: both
    }

    // MARK: cycle (e.g. 21 days on, 7 off)

    func testCycleActiveDaysAndPause() {
        let anchor = date(2026, 8, 1)
        let s = DoseSchedule(kind: .cycle, slots: [DoseSlot(hour: 8)],
                             cycleLength: 28, pauseDays: 7, anchor: anchor)
        XCTAssertEqual(s.activeDays, 21)
        XCTAssertTrue(s.isDue(on: anchor, calendar: cal))                       // day 1
        XCTAssertTrue(s.isDue(on: date(2026, 8, 21), calendar: cal))            // day 21 (last active)
        XCTAssertFalse(s.isDue(on: date(2026, 8, 22), calendar: cal))           // day 22 (pause)
        XCTAssertFalse(s.isDue(on: date(2026, 8, 28), calendar: cal))           // day 28 (pause)
        XCTAssertTrue(s.isDue(on: date(2026, 8, 29), calendar: cal))            // day 1 of next cycle
    }

    func testCycleDayWrapsAndHandlesBeforeAnchor() {
        let anchor = date(2026, 8, 10)
        let s = DoseSchedule(kind: .cycle, cycleLength: 28, pauseDays: 7, anchor: anchor)
        XCTAssertEqual(s.cycleDay(for: anchor, calendar: cal), 1)
        XCTAssertEqual(s.cycleDay(for: date(2026, 8, 11), calendar: cal), 2)
        XCTAssertEqual(s.cycleDay(for: date(2026, 9, 7), calendar: cal), 1) // 28 days later wraps to 1
        // A day before the anchor still resolves to a valid 1...cycleLength value.
        let before = s.cycleDay(for: date(2026, 8, 9), calendar: cal)
        XCTAssertNotNil(before)
        XCTAssertEqual(before, 28)
    }

    // MARK: upcomingDueDates (the reminder windowing source)

    func testUpcomingDueDatesWeeklyEveryDayIsConsecutive() {
        let dose = DoseSlot(weekdays: Set(1...7), hour: 8)
        let s = DoseSchedule(kind: .weekly, slots: [dose])
        let days = s.upcomingDueDates(for: dose, from: date(2026, 8, 12), count: 5, calendar: cal)
        XCTAssertEqual(days.map { cal.component(.day, from: $0) }, [12, 13, 14, 15, 16])
    }

    func testUpcomingDueDatesCycleSkipsPause() {
        let anchor = date(2026, 8, 1)
        let dose = DoseSlot(hour: 8)
        let s = DoseSchedule(kind: .cycle, slots: [dose], cycleLength: 28, pauseDays: 7, anchor: anchor)
        // From day 20, the next few due days skip the 7-day pause (22...28).
        let days = s.upcomingDueDates(for: dose, from: date(2026, 8, 20), count: 3, calendar: cal)
        let ds = days.map { cal.component(.day, from: $0) }
        XCTAssertEqual(ds, [20, 21, 29]) // 20, 21 active; then jump over pause to next cycle day 1
    }
}
