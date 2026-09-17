import XCTest
import SwiftData
@testable import Keel

/// The check-in quick-chip ordering: her most-logged symptoms lead the row (any
/// symptom she has logged, including custom and sensitive ones), adapting from her
/// first logs, with a fresh account falling back to catalog order.
final class QuickChipRankingTests: XCTestCase {

    private typealias Candidate = SymptomRepository.QuickChipCandidate

    // MARK: Pure ranking

    func testColdStartUsesCatalogOrderAndExcludesUnloggedNonDefaults() {
        let hot = UUID(), sleep = UUID(), custom = UUID()
        let candidates = [
            Candidate(id: sleep, name: "Trouble sleeping", isDefaultChip: true), // catalog rank 2
            Candidate(id: hot, name: "Hot flushes", isDefaultChip: true),        // catalog rank 0
            Candidate(id: custom, name: "Tinnitus", isDefaultChip: false),       // never logged
        ]
        let result = SymptomRepository.rankedQuickChipIDs(candidates: candidates, counts: [:], limit: 10)
        // Catalog order for the defaults; the unlogged custom is not surfaced.
        XCTAssertEqual(result, [hot, sleep])
    }

    func testFrequentCustomSymptomSurfacesToFront() {
        let hot = UUID(), custom = UUID()
        let candidates = [
            Candidate(id: hot, name: "Hot flushes", isDefaultChip: true),
            Candidate(id: custom, name: "Tinnitus", isDefaultChip: false),
        ]
        let result = SymptomRepository.rankedQuickChipIDs(candidates: candidates, counts: [custom: 5], limit: 10)
        XCTAssertEqual(result, [custom, hot]) // her logged custom leads the unlogged default
    }

    func testMostLoggedRanksFirstDespiteCatalogOrder() {
        let a = UUID(), b = UUID()
        let candidates = [
            Candidate(id: a, name: "Hot flushes", isDefaultChip: true),  // catalog rank 0
            Candidate(id: b, name: "Night sweats", isDefaultChip: true), // catalog rank 1
        ]
        let result = SymptomRepository.rankedQuickChipIDs(candidates: candidates, counts: [a: 1, b: 4], limit: 10)
        XCTAssertEqual(result, [b, a]) // more logs wins over the better catalog rank
    }

    func testEqualCountTieBreaksToDefaultChipFirst() {
        let def = UUID(), nonDef = UUID()
        let candidates = [
            Candidate(id: nonDef, name: "Tinnitus", isDefaultChip: false),
            Candidate(id: def, name: "Hot flushes", isDefaultChip: true),
        ]
        let result = SymptomRepository.rankedQuickChipIDs(candidates: candidates, counts: [def: 2, nonDef: 2], limit: 10)
        XCTAssertEqual(result, [def, nonDef])
    }

    func testLimitCapsTheRow() {
        let ids = (0..<5).map { _ in UUID() }
        let candidates = ids.map { Candidate(id: $0, name: "Hot flushes", isDefaultChip: true) }
        let result = SymptomRepository.rankedQuickChipIDs(candidates: candidates, counts: [:], limit: 3)
        XCTAssertEqual(result.count, 3)
    }

    // MARK: End-to-end through the repository

    @MainActor
    func testDefaultChipsPromotesFrequentlyLoggedCustomSymptom() throws {
        let ctx = TestStore.makeContext()
        let repo = SymptomRepository(context: ctx, ownerID: TestStore.ownerID)
        repo.syncBuiltIns()

        let custom = repo.findOrCreateCustom(name: "Tinnitus", category: .body)
        for i in 0..<5 {
            let day = Date.now.addingTimeInterval(Double(-i) * 86_400)
            let ci = CheckIn(date: day, mood: .okay, energy: 50, ownerID: TestStore.ownerID())
            ctx.insert(ci)
            ctx.insert(CheckInSymptom(checkIn: ci, symptom: custom, severity: 2,
                                      source: .manual, ownerID: TestStore.ownerID()))
        }
        try ctx.save()

        // Her most-logged symptom leads, even though it is a custom, non-default chip.
        XCTAssertEqual(repo.defaultChips().first?.name, "Tinnitus")
    }

    @MainActor
    func testDefaultChipsColdStartIsCatalogDefaults() {
        let ctx = TestStore.makeContext()
        let repo = SymptomRepository(context: ctx, ownerID: TestStore.ownerID)
        repo.syncBuiltIns()

        let chips = repo.defaultChips()
        XCTAssertFalse(chips.isEmpty)
        XCTAssertTrue(chips.allSatisfy(\.isDefaultChip)) // no personalisation yet -> the default set
        XCTAssertEqual(chips.first?.name, "Hot flushes") // catalog order leads
    }
}
