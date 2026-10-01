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
            now: .now, lastSyncedAt: nil, force: false, connected: true, minInterval: minInterval))
    }

    func testThrottledWithinWindow() {
        let now = Date.now
        XCTAssertFalse(AppEnvironment.shouldRunHealthSync(
            now: now, lastSyncedAt: now.addingTimeInterval(-60), force: false, connected: true, minInterval: minInterval))
    }

    func testRunsAgainAfterWindowElapses() {
        let now = Date.now
        XCTAssertTrue(AppEnvironment.shouldRunHealthSync(
            now: now, lastSyncedAt: now.addingTimeInterval(-minInterval - 1), force: false, connected: true, minInterval: minInterval))
    }

    /// An explicit "Sync now" / connect always bypasses the window.
    func testForceBypassesThrottle() {
        let now = Date.now
        XCTAssertTrue(AppEnvironment.shouldRunHealthSync(
            now: now, lastSyncedAt: now.addingTimeInterval(-60), force: true, connected: true, minInterval: minInterval))
    }

    /// Regression: because the window is only stamped after auth succeeds, a failed
    /// sync leaves `lastSyncedAt == nil`, so the next attempt is still due (not blocked
    /// for 30 minutes). This models that state.
    func testFailedSyncLeavesNextAttemptDue() {
        XCTAssertTrue(AppEnvironment.shouldRunHealthSync(
            now: .now, lastSyncedAt: nil, force: false, connected: true, minInterval: minInterval))
    }

    // MARK: Not-connected gate (launch-prompt regression)

    /// Regression: a foreground/launch sync for a user who hasn't connected must not
    /// run. Otherwise it calls `requestAuthorization`, which prompts at app open (even
    /// during onboarding) instead of on the explicit "Connect with Apple Health" tap.
    func testNotConnectedNeverSyncs() {
        XCTAssertFalse(AppEnvironment.shouldRunHealthSync(
            now: .now, lastSyncedAt: nil, force: false, connected: false, minInterval: minInterval))
    }

    /// Even a forced sync stays off until she connects: nothing should pre-empt the
    /// prompt before the explicit Connect action (which sets `connected` first, then
    /// forces a sync).
    func testNotConnectedBlocksEvenForce() {
        XCTAssertFalse(AppEnvironment.shouldRunHealthSync(
            now: .now, lastSyncedAt: nil, force: true, connected: false, minInterval: minInterval))
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

    /// Regression: the shared connect path (used by both onboarding and Settings)
    /// persists the connected flag, so the menu doesn't still show "Connect" after
    /// onboarding. Tests run on the Simulator, where the shared path treats her as
    /// connected for the demo, so this asserts the persistence the two screens rely on.
    func testConnectAppleHealthPersistsTheFlag() async {
        let env = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
        env.users.upsertProfile(firstName: "Mischa", email: nil, appleUserID: nil)

        let connected = await env.connectAppleHealth()

        XCTAssertTrue(connected)
        XCTAssertEqual(env.users.currentProfile()?.healthKitAuthorized, true)
    }
}
