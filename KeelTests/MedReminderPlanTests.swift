import XCTest
@testable import Keel

/// The medication-reminder scheduling decision: which dated occurrences get
/// created, honouring skip-if-logged, past-time, and the per-dose horizon budget.
/// This is exactly where the "logged it but still got notified" bug lived, so it
/// gets the most coverage. All deterministic: fixed UTC calendar + injected `now`.
final class MedReminderPlanTests: XCTestCase {

    private var cal: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }()

    private func date(_ y: Int, _ m: Int, _ d: Int, _ hh: Int = 0, _ mm: Int = 0) -> Date {
        cal.date(from: DateComponents(year: y, month: m, day: d, hour: hh, minute: mm))!
    }

    private func weeklyEveryDay(hour: Int) -> (DoseSchedule, DoseSlot) {
        let dose = DoseSlot(weekdays: Set(1...7), hour: hour)
        return (DoseSchedule(kind: .weekly, slots: [dose]), dose)
    }

    // MARK: dayKey / cycleHorizon (pure helpers)

    func testDayKeyFormat() {
        XCTAssertEqual(NotificationService.dayKey(date(2026, 8, 12), calendar: cal), "20260812")
        XCTAssertEqual(NotificationService.dayKey(date(2026, 12, 1), calendar: cal), "20261201")
    }

    func testCycleHorizonBudget() {
        XCTAssertEqual(NotificationService.cycleHorizon(activeMedications: 1), 24)
        XCTAssertEqual(NotificationService.cycleHorizon(activeMedications: 2), 24)
        XCTAssertEqual(NotificationService.cycleHorizon(activeMedications: 3), 16)
        XCTAssertEqual(NotificationService.cycleHorizon(activeMedications: 4), 12)
        XCTAssertEqual(NotificationService.cycleHorizon(activeMedications: 12), 4)
        XCTAssertEqual(NotificationService.cycleHorizon(activeMedications: 100), 4) // floor
        XCTAssertEqual(NotificationService.cycleHorizon(activeMedications: 0), 24)  // no divide-by-zero
    }

    // MARK: scheduling window

    func testTodayScheduledWhenFutureAndUnlogged() {
        let (s, dose) = weeklyEveryDay(hour: 20)
        let occ = NotificationService.plannedOccurrences(
            schedule: s, cycleHorizon: 24, now: date(2026, 8, 12, 9), calendar: cal)
        XCTAssertEqual(occ.count, 24)                         // full horizon
        XCTAssertEqual(occ.first?.dayKey, "20260812")         // today is first
        XCTAssertTrue(occ.allSatisfy { $0.slotID == dose.id.uuidString })
        // Consecutive days, no gaps.
        XCTAssertEqual(Set(occ.map(\.dayKey)).count, 24)
    }

    func testTodaySkippedWhenTimeAlreadyPassed() {
        let (s, _) = weeklyEveryDay(hour: 8)
        let occ = NotificationService.plannedOccurrences(
            schedule: s, cycleHorizon: 24, now: date(2026, 8, 12, 9), calendar: cal) // 9am > 8am dose
        XCTAssertEqual(occ.count, 24)
        XCTAssertEqual(occ.first?.dayKey, "20260813")         // today's 8am is past → starts tomorrow
        XCTAssertFalse(occ.contains { $0.dayKey == "20260812" })
    }

    // MARK: skip-if-logged (the reported bug)

    func testTodaySkippedWhenWholeDayLogged() {
        let (s, _) = weeklyEveryDay(hour: 20)
        let occ = NotificationService.plannedOccurrences(
            schedule: s, cycleHorizon: 24, loggedTodayWholeDay: true,
            now: date(2026, 8, 12, 9), calendar: cal)
        XCTAssertFalse(occ.contains { $0.dayKey == "20260812" }) // today's reminder gone
        XCTAssertEqual(occ.first?.dayKey, "20260813")            // starts tomorrow
        XCTAssertEqual(occ.count, 24)                            // future days still filled
    }

    func testTodaySkippedWhenThatSlotLogged() {
        let (s, dose) = weeklyEveryDay(hour: 20)
        let occ = NotificationService.plannedOccurrences(
            schedule: s, cycleHorizon: 24, loggedTodaySlots: [dose.id.uuidString],
            now: date(2026, 8, 12, 9), calendar: cal)
        XCTAssertFalse(occ.contains { $0.dayKey == "20260812" })
        XCTAssertEqual(occ.count, 24)
    }

    func testUnrelatedSlotLoggedDoesNotSkipToday() {
        let (s, _) = weeklyEveryDay(hour: 20)
        let occ = NotificationService.plannedOccurrences(
            schedule: s, cycleHorizon: 24, loggedTodaySlots: ["some-other-slot"],
            now: date(2026, 8, 12, 9), calendar: cal)
        XCTAssertTrue(occ.contains { $0.dayKey == "20260812" }) // today still scheduled
    }

    // MARK: multi-dose budget split

    func testMultiDoseSplitsHorizonPerSlot() {
        let morning = DoseSlot(weekdays: Set(1...7), hour: 8)
        let evening = DoseSlot(weekdays: Set(1...7), hour: 20)
        let s = DoseSchedule(kind: .weekly, slots: [morning, evening])
        // now before both doses so today counts for each.
        let occ = NotificationService.plannedOccurrences(
            schedule: s, cycleHorizon: 24, now: date(2026, 8, 12, 6), calendar: cal)
        XCTAssertEqual(occ.count, 24)                                   // 12 per dose
        XCTAssertEqual(occ.filter { $0.slotID == morning.id.uuidString }.count, 12)
        XCTAssertEqual(occ.filter { $0.slotID == evening.id.uuidString }.count, 12)
    }

    // MARK: empty cases

    func testNoTimedDoseSchedulesNothing() {
        let s = DoseSchedule(kind: .weekly, slots: [DoseSlot(weekdays: Set(1...7))]) // no hour
        let occ = NotificationService.plannedOccurrences(schedule: s, now: date(2026, 8, 12, 6), calendar: cal)
        XCTAssertTrue(occ.isEmpty)
    }

    // MARK: cycle honours the pause

    func testCycleScheduleSkipsPauseDays() {
        let anchor = date(2026, 8, 1)
        let dose = DoseSlot(hour: 8)
        let s = DoseSchedule(kind: .cycle, slots: [dose], cycleLength: 28, pauseDays: 7, anchor: anchor)
        // From within the active window, before the dose time.
        let occ = NotificationService.plannedOccurrences(
            schedule: s, cycleHorizon: 5, now: date(2026, 8, 20, 6), calendar: cal)
        let days = occ.map(\.dayKey)
        XCTAssertFalse(days.contains("20260822")) // pause day never scheduled
        XCTAssertFalse(days.contains("20260828")) // pause day never scheduled
        XCTAssertTrue(days.contains("20260820"))
        XCTAssertTrue(days.contains("20260821"))
        XCTAssertTrue(days.contains("20260829")) // resumes next cycle
    }
}
