import XCTest
import SwiftData
@testable import Keel

/// The tri-state that makes the trigger correlation honest: yes and no each persist a
/// real row (1 and 0), and clearing removes the row so the day reads as "not logged"
/// rather than a clean no. Uses the default calendar throughout (matching the view and
/// ActivityLog's own day normalisation), so the round-trip is timezone-consistent.
@MainActor
final class EatingLogTests: XCTestCase {

    private let id = "eat.alcohol"
    private let day = Date(timeIntervalSince1970: 1_600_000_000)

    private func rows(_ context: ModelContext) -> [ActivityLog] {
        (try? context.fetch(FetchDescriptor<ActivityLog>())) ?? []
    }

    func testYesNoNullRoundTrip() {
        let context = TestStore.makeContext()

        // Starts as "not logged".
        XCTAssertNil(EatingLog.state(for: id, on: day, in: rows(context)))

        // Yes → true, one row.
        EatingLog.set(true, for: id, on: day, ownerID: "o", in: context)
        XCTAssertEqual(EatingLog.state(for: id, on: day, in: rows(context)), true)
        XCTAssertEqual(rows(context).filter { $0.activityID == id }.count, 1)

        // No → false, the SAME row updated to an explicit 0 (not a blank, not a dup).
        EatingLog.set(false, for: id, on: day, ownerID: "o", in: context)
        XCTAssertEqual(EatingLog.state(for: id, on: day, in: rows(context)), false)
        XCTAssertEqual(rows(context).filter { $0.activityID == id }.count, 1)

        // Clear → not logged, row gone.
        EatingLog.set(nil, for: id, on: day, ownerID: "o", in: context)
        XCTAssertNil(EatingLog.state(for: id, on: day, in: rows(context)))
        XCTAssertEqual(rows(context).filter { $0.activityID == id }.count, 0)
    }

    func testNoWithNoPriorRowStillPersistsAZero() {
        // The important edge: an explicit "no" with no existing row must create a
        // 0-row, so it counts as answered (the old toggle skipped 0 and lost it).
        let context = TestStore.makeContext()
        EatingLog.set(false, for: id, on: day, ownerID: "o", in: context)
        XCTAssertEqual(EatingLog.state(for: id, on: day, in: rows(context)), false)
        XCTAssertEqual(rows(context).filter { $0.activityID == id }.count, 1)
    }
}
