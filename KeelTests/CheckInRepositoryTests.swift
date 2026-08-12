import XCTest
import SwiftData
@testable import Keel

/// Check-in creation, editing and deletion against an in-memory store, focusing
/// on the symptom-link reconciliation and soft-delete/tombstone behaviour that
/// several screens and the sync layer depend on.
@MainActor
final class CheckInRepositoryTests: XCTestCase {

    private var context: ModelContext!
    private var repo: CheckInRepository!

    override func setUpWithError() throws {
        context = TestStore.makeContext()
        repo = CheckInRepository(context: context, ownerID: TestStore.ownerID)
    }

    override func tearDownWithError() throws { context = nil; repo = nil }

    private func makeSymptom(_ name: String) -> Symptom {
        let s = Symptom(name: name, category: .body, isCustom: true, ownerID: "test-owner")
        context.insert(s)
        try? context.save()
        return s
    }

    func testCreateWithSymptoms() {
        let hot = makeSymptom("Hot flushes")
        let entry = repo.create(mood: .okay, energy: 60, notes: "note",
                                symptoms: [(hot, 2)], date: .now)
        XCTAssertEqual(entry.symptoms.count, 1)
        XCTAssertEqual(entry.symptoms.first?.name, "Hot flushes")
        XCTAssertEqual(repo.all().count, 1)
    }

    func testCreateTrimsBlankNotesToNil() {
        let entry = repo.create(mood: .good, energy: 70, notes: "   ", symptoms: [], date: .now)
        XCTAssertNil(entry.notes)
    }

    func testUpdateReconcilesSymptoms_addKeepRemove() {
        let a = makeSymptom("A"), b = makeSymptom("B"), c = makeSymptom("C")
        let entry = repo.create(mood: .okay, energy: 50, notes: nil, symptoms: [(a, 1), (b, 1)], date: .now)

        // Keep A (new severity), drop B, add C.
        repo.update(entry, mood: .low, energy: 40, notes: "changed", symptoms: [(a, 3), (c, 2)])

        let active = entry.symptoms.map(\.name).sorted()
        XCTAssertEqual(active, ["A", "C"])
        // A's severity was updated.
        let aLink = (entry.symptomLinks ?? []).first { $0.symptom?.id == a.id && !$0.isTombstoned }
        XCTAssertEqual(aLink?.severity, 3)
        // B's link is tombstoned, not hard-deleted (history preserved).
        let bLink = (entry.symptomLinks ?? []).first { $0.symptom?.id == b.id }
        XCTAssertNotNil(bLink)
        XCTAssertTrue(bLink!.isTombstoned)
    }

    func testDeleteSoftDeletesAndTombstonesLinks() {
        let a = makeSymptom("A")
        let entry = repo.create(mood: .okay, energy: 50, notes: nil, symptoms: [(a, 1)], date: .now)
        repo.delete(entry)

        XCTAssertTrue(repo.all().isEmpty)             // gone from views
        XCTAssertNotNil(entry.deletedAt)              // soft-deleted (still syncs)
        XCTAssertTrue((entry.symptomLinks ?? []).allSatisfy(\.isTombstoned))
        // The shared catalog symptom itself is untouched.
        XCTAssertNil(a.deletedAt)
    }

    func testRecentIsNewestFirstAndLimited() {
        for i in 0..<5 {
            repo.create(mood: .okay, energy: 50, notes: nil, symptoms: [],
                        date: Date.now.addingTimeInterval(Double(i) * 3600))
        }
        let recent = repo.recent(limit: 3)
        XCTAssertEqual(recent.count, 3)
        XCTAssertTrue(recent[0].date > recent[1].date) // descending
    }

    func testTrackingDayCountCountsDistinctDays() {
        let start = Date.now.startOfDay
        // Two entries same day + one the next day = 2 distinct days.
        repo.create(mood: .okay, energy: 50, notes: nil, symptoms: [], date: start.addingTimeInterval(3600))
        repo.create(mood: .good, energy: 60, notes: nil, symptoms: [], date: start.addingTimeInterval(7200))
        repo.create(mood: .low, energy: 40, notes: nil, symptoms: [], date: start.adding(days: 1))
        XCTAssertEqual(repo.trackingDayCount(startDate: start), 2)
    }
}
