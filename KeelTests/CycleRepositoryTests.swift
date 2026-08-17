import XCTest
import SwiftData
@testable import Keel

/// Period logging and the gentle cycle-phase estimate. The estimate is a heuristic
/// (perimenopausal cycles are irregular), so these tests pin the boundaries, not
/// clinical accuracy.
@MainActor
final class CycleRepositoryTests: XCTestCase {

    private var context: ModelContext!
    private var repo: CycleRepository!

    override func setUpWithError() throws {
        context = TestStore.makeContext()
        repo = CycleRepository(context: context, ownerID: TestStore.ownerID)
    }

    override func tearDownWithError() throws { context = nil; repo = nil }

    func testTogglePeriodDayOnThenOff() {
        let day = Date.now
        XCTAssertFalse(repo.isPeriodDay(day))
        repo.togglePeriodDay(day)
        XCTAssertTrue(repo.isPeriodDay(day))
        repo.togglePeriodDay(day) // toggling again removes it
        XCTAssertFalse(repo.isPeriodDay(day))
    }

    func testTogglePeriodDayIsPerDay() {
        let today = Date.now
        repo.togglePeriodDay(today)
        XCTAssertTrue(repo.isPeriodDay(today))
        XCTAssertFalse(repo.isPeriodDay(today.adding(days: 1)))
    }

    func testCycleStartReturnsMostRecentStartOnOrBefore() {
        let base = Date.now.startOfDay
        repo.togglePeriodDay(base.adding(days: -20))
        repo.togglePeriodDay(base.adding(days: -5))
        let last = repo.cycleStart(before: base)
        XCTAssertEqual(last?.startOfDay, base.adding(days: -5).startOfDay)
    }

    func testCycleStartNilWhenNone() {
        XCTAssertNil(repo.cycleStart(before: .now))
    }

    func testEstimatedPhaseUnknownWithoutHistory() {
        XCTAssertEqual(repo.estimatedPhase(on: .now), .unknown)
    }

    func testEstimatedPhaseBoundaries() {
        let start = Date.now.startOfDay.adding(days: -20) // a period start 20 days ago
        repo.togglePeriodDay(start)
        // day index = date.days(since: start)
        XCTAssertEqual(repo.estimatedPhase(on: start), .menstrual)              // day 0
        XCTAssertEqual(repo.estimatedPhase(on: start.adding(days: 4)), .menstrual)   // day 4
        XCTAssertEqual(repo.estimatedPhase(on: start.adding(days: 5)), .follicular)  // day 5
        XCTAssertEqual(repo.estimatedPhase(on: start.adding(days: 12)), .follicular) // day 12
        XCTAssertEqual(repo.estimatedPhase(on: start.adding(days: 13)), .ovulation)  // day 13
        XCTAssertEqual(repo.estimatedPhase(on: start.adding(days: 15)), .ovulation)  // day 15
        XCTAssertEqual(repo.estimatedPhase(on: start.adding(days: 16)), .luteal)     // day 16
        XCTAssertEqual(repo.estimatedPhase(on: start.adding(days: 39)), .luteal)     // day 39
        XCTAssertEqual(repo.estimatedPhase(on: start.adding(days: 40)), .unknown)    // stale
    }

    func testEntriesWindowIsInclusive() {
        let base = Date.now.startOfDay
        repo.togglePeriodDay(base)
        repo.togglePeriodDay(base.adding(days: 3))
        let inRange = repo.entries(from: base, to: base.adding(days: 3))
        XCTAssertEqual(inRange.count, 2)
        let narrow = repo.entries(from: base, to: base.adding(days: 1))
        XCTAssertEqual(narrow.count, 1)
    }
}
