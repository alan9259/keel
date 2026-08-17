import XCTest
import SwiftData
@testable import Keel

/// A reflection is keyed to a CHANGE in her patterns, not the calendar. Existing
/// per-day duplicates (same pattern, many days) collapse to one entry when the
/// pattern is unchanged, so "Looking back" doesn't repeat. With no check-ins,
/// `refreshIfNeeded` derives no new finding, so it only exercises the cleanup.
@MainActor
final class ReflectionDedupTests: XCTestCase {

    private var context: ModelContext!
    private var service: DailySummaryService!

    override func setUpWithError() throws {
        context = TestStore.makeContext()
        service = DailySummaryService(context: context, ownerID: TestStore.ownerID)
    }

    override func tearDownWithError() throws { context = nil; service = nil }

    private func insert(day offset: Int, signature: String, text: String) {
        context.insert(DailySummary(
            day: Date.now.startOfDay.adding(days: offset),
            text: text, source: .deterministic, signalsJSON: signature,
            generatedAt: .now, ownerID: "o", syncStatus: .synced))
    }

    func testConsecutiveUnchangedReflectionsCollapseToOne() async {
        insert(day: -3, signature: "[\"A\"]", text: "Pattern A, day 3")
        insert(day: -2, signature: "[\"A\"]", text: "Pattern A, day 2")
        insert(day: -1, signature: "[\"A\"]", text: "Pattern A, day 1")
        try? context.save()

        await service.refreshIfNeeded()

        let kept = service.history()
        XCTAssertEqual(kept.count, 1, "unchanged pattern should leave a single entry")
        XCTAssertEqual(kept.first?.signalsJSON, "[\"A\"]")
        // The earliest of the run is kept (when the pattern began).
        XCTAssertEqual(kept.first?.day, Date.now.startOfDay.adding(days: -3))
    }

    func testDistinctPatternsAreAllKept() async {
        insert(day: -3, signature: "[\"A\"]", text: "A")
        insert(day: -2, signature: "[\"B\"]", text: "B")
        insert(day: -1, signature: "[\"C\"]", text: "C")
        try? context.save()

        await service.refreshIfNeeded()
        XCTAssertEqual(service.history().count, 3)
    }

    func testARecurringPatternSeparatedByAnotherIsKeptTwice() async {
        insert(day: -3, signature: "[\"A\"]", text: "A then")
        insert(day: -2, signature: "[\"B\"]", text: "B")
        insert(day: -1, signature: "[\"A\"]", text: "A again")
        try? context.save()

        await service.refreshIfNeeded()
        // A, B, A: the second A follows B, so it's a genuine re-occurrence, not a repeat.
        XCTAssertEqual(service.history().count, 3)
    }

    func testSignatureIsStableForSameFactsAndDiffersOtherwise() {
        XCTAssertEqual(DailySummaryService.signature(of: ["x", "y"]),
                       DailySummaryService.signature(of: ["x", "y"]))
        XCTAssertNotEqual(DailySummaryService.signature(of: ["x", "y"]),
                          DailySummaryService.signature(of: ["y", "x"]))
    }
}
