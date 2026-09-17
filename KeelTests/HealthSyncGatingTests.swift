import XCTest
import SwiftData
@testable import Keel

/// The Apple Health connect/sync gating that the two tester reports touch:
/// the throttle decision (`AppEnvironment.shouldRunHealthSync`) and the connected
/// flag persisting through `UserRepository.setHealthKitAuthorized`.
@MainActor
final class HealthSyncGatingTests: XCTestCase {

    private let minInterval: TimeInterval = 30 * 60

    // MARK: Throttle decision

    func testFirstSyncRunsWhenNeverSynced() {
        XCTAssertTrue(AppEnvironment.shouldRunHealthSync(
            now: .now, lastSyncedAt: nil, force: false, minInterval: minInterval))
    }

    func testThrottledWithinWindow() {
        let now = Date.now
        XCTAssertFalse(AppEnvironment.shouldRunHealthSync(
            now: now, lastSyncedAt: now.addingTimeInterval(-60), force: false, minInterval: minInterval))
    }

    func testRunsAgainAfterWindowElapses() {
        let now = Date.now
        XCTAssertTrue(AppEnvironment.shouldRunHealthSync(
            now: now, lastSyncedAt: now.addingTimeInterval(-minInterval - 1), force: false, minInterval: minInterval))
    }

    /// An explicit "Sync now" / connect always bypasses the window.
    func testForceBypassesThrottle() {
        let now = Date.now
        XCTAssertTrue(AppEnvironment.shouldRunHealthSync(
            now: now, lastSyncedAt: now.addingTimeInterval(-60), force: true, minInterval: minInterval))
    }

    /// Regression: because the window is only stamped after auth succeeds, a failed
    /// sync leaves `lastSyncedAt == nil`, so the next attempt is still due (not blocked
    /// for 30 minutes). This models that state.
    func testFailedSyncLeavesNextAttemptDue() {
        XCTAssertTrue(AppEnvironment.shouldRunHealthSync(
            now: .now, lastSyncedAt: nil, force: false, minInterval: minInterval))
    }

    // MARK: Connected flag persistence

    func testSetHealthKitAuthorizedPersists() {
        let ctx = TestStore.makeContext()
        let repo = UserRepository(context: ctx, ownerID: TestStore.ownerID)
        repo.upsertProfile(firstName: "Mischa", email: nil, appleUserID: nil)

        repo.setHealthKitAuthorized(true)
        XCTAssertEqual(repo.currentProfile()?.healthKitAuthorized, true)

        repo.setHealthKitAuthorized(false)
        XCTAssertEqual(repo.currentProfile()?.healthKitAuthorized, false)
    }

    /// Regression: connecting when no profile exists yet must still persist the flag
    /// (previously it silently no-op'd, sending her back to "Connect" on return).
    func testSetHealthKitAuthorizedCreatesProfileWhenMissing() {
        let ctx = TestStore.makeContext()
        let repo = UserRepository(context: ctx, ownerID: TestStore.ownerID)
        XCTAssertNil(repo.currentProfile())

        repo.setHealthKitAuthorized(true)

        XCTAssertNotNil(repo.currentProfile())
        XCTAssertEqual(repo.currentProfile()?.healthKitAuthorized, true)
    }
}
