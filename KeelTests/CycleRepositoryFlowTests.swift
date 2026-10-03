import XCTest
import SwiftData
@testable import Keel

/// Flow-level logging on the cycle repository, and the same source rule as sleep:
/// Apple Health owns its own period days and never overwrites one she typed.
@MainActor
final class CycleRepositoryFlowTests: XCTestCase {

    private var context: ModelContext!
    private var repo: CycleRepository!

    override func setUpWithError() throws {
        context = TestStore.makeContext()
        repo = CycleRepository(context: context, ownerID: TestStore.ownerID)
    }

    override func tearDownWithError() throws { context = nil; repo = nil }

    func testSetFlowUpsertsAndClears() {
        let day = Date.now
        XCTAssertNil(repo.flow(on: day))
        repo.setFlow(.heavy, on: day)
        XCTAssertEqual(repo.flow(on: day), .heavy)
        repo.setFlow(.light, on: day)                 // change the level
        XCTAssertEqual(repo.flow(on: day), .light)
        repo.setFlow(nil, on: day)                    // clear it
        XCTAssertNil(repo.flow(on: day))
        XCTAssertFalse(repo.isPeriodDay(day))
    }

    func testTogglePeriodDayLogsUnspecified() {
        let day = Date.now
        repo.togglePeriodDay(day)
        XCTAssertEqual(repo.flow(on: day), .unspecified)
        repo.togglePeriodDay(day)
        XCTAssertNil(repo.flow(on: day))
    }

    func testStatsFromLoggedStarts() {
        let base = Date.now.startOfDay
        repo.setFlow(.medium, on: base.adding(days: -56))
        repo.setFlow(.medium, on: base.adding(days: -28))
        repo.setFlow(.medium, on: base)
        let stats = repo.stats(lookbackDays: 400, now: base)
        XCTAssertEqual(stats.cycleLengths, [28, 28])
    }

    func testSpottingDayDoesNotReanchorThePhase() {
        let base = Date.now.startOfDay
        repo.setFlow(.medium, on: base.adding(days: -28)) // a real period start 28 days ago
        // A stray spotting day mid-cycle must not be mistaken for a new cycle start.
        repo.setFlow(.spotting, on: base.adding(days: -3))
        // Anchored to the real start (day 28 → luteal), not the spotting day (which
        // would read menstrual if it counted).
        XCTAssertEqual(repo.cycleStart(before: base), base.adding(days: -28))
        XCTAssertEqual(repo.estimatedPhase(on: base), .luteal)
    }

    func testCatchingUpTheLatestPeriodReanchorsThePhase() {
        let base = Date.now.startOfDay
        repo.setFlow(.medium, on: base.adding(days: -40)) // old start; day 40 → unknown/overdue
        XCTAssertEqual(repo.estimatedPhase(on: base), .unknown)
        // She backfills the period she actually had 3 days ago → phase re-anchors.
        repo.setFlow(.medium, on: base.adding(days: -3))
        XCTAssertEqual(repo.cycleStart(before: base), base.adding(days: -3))
        XCTAssertEqual(repo.estimatedPhase(on: base), .menstrual) // day 3
    }

    /// Apple Health periods import into the health store, never into her own cycle
    /// entries: a day she typed keeps her value, and a Health-only day is not a
    /// `CycleEntry` (so estimates, which read `CycleEntry`, never see it).
    func testHealthFlowImportsApartFromHerCycleEntries() {
        let base = Date.now.startOfDay
        let manualDay = base.adding(days: -1)
        repo.setFlow(.heavy, on: manualDay) // she logged this by hand

        let symptoms = SymptomRepository(context: context, ownerID: TestStore.ownerID)
        let ingestor = HealthIngestor(context: context, ownerID: TestStore.ownerID, symptoms: symptoms)
        var snapshot = HealthSnapshot()
        snapshot.menstrualFlow = [manualDay: .light, base.adding(days: -3): .medium]
        _ = ingestor.ingest(snapshot)

        XCTAssertEqual(repo.flow(on: manualDay), .heavy)          // her value stands
        XCTAssertNil(repo.flow(on: base.adding(days: -3)))        // not written into CycleEntry
        XCTAssertEqual(repo.importedFlow(from: base.adding(days: -5), to: base),
                       [manualDay: .light, base.adding(days: -3): .medium])
    }
}
