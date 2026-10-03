import XCTest
import SwiftData
@testable import Keel

/// Fake Apple Health source: counts calls, returns a canned snapshot, and can hold a
/// read open so a test can act (disconnect, tap Sync now) while a sync is in flight.
@MainActor
private final class FakeHealthSource: HealthDataSource {
    var authorizationRequests = 0
    /// She has answered the sheet before (the usual connected case).
    var status: HealthRequestStatus = .unnecessary
    func requestStatus() async -> HealthRequestStatus { status }
    var snapshotReads = 0
    var snapshotToReturn = HealthSnapshot()
    var holdNextRead = false
    private var held: CheckedContinuation<Void, Never>?

    func requestAuthorization() async -> Bool {
        authorizationRequests += 1
        return true
    }

    func snapshot(lastDays: Int) async -> HealthSnapshot {
        snapshotReads += 1
        if holdNextRead {
            holdNextRead = false
            await withCheckedContinuation { held = $0 }
        }
        return snapshotToReturn
    }

    var isHoldingRead: Bool { held != nil }
    func releaseRead() { held?.resume(); held = nil }
}

/// The real `AppEnvironment.syncHealthData` driven end to end against the fake, covering
/// the review findings: no HealthKit request before Connect, Disconnect mid-sync doesn't
/// import, a forced sync during another is queued (not dropped), and an empty read
/// doesn't use up the throttle window.
@MainActor
final class HealthSyncBehaviourTests: XCTestCase {

    private var fake: FakeHealthSource!
    private var env: AppEnvironment!

    override func setUp() async throws {
        fake = FakeHealthSource()
        env = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true),
                             provider: NoopSyncProvider(), health: fake)
        env.users.upsertProfile(firstName: "Mischa", email: nil, appleUserID: nil)
    }

    private var day: Date { Date.now.startOfDay }
    private func someData() -> HealthSnapshot {
        var s = HealthSnapshot(); s.activityAmounts = ["steps": [day: 8000]]; return s
    }

    private func waitUntil(timeout: TimeInterval = 2, _ condition: () -> Bool) async {
        let deadline = Date.now.addingTimeInterval(timeout)
        while !condition(), Date.now < deadline { try? await Task.sleep(for: .milliseconds(5)) }
    }

    private func waitForIdle() async { await waitUntil { !env.isHealthSyncInFlight } }

    /// Regression (launch prompt): before she connects, even a forced sync never asks
    /// HealthKit for authorization (which is what raises the system prompt).
    func testNotConnectedSyncNeverRequestsAuthorization() async {
        env.syncHealthData()
        env.syncHealthData(force: true)
        await waitForIdle()
        XCTAssertEqual(fake.authorizationRequests, 0)
        XCTAssertEqual(fake.snapshotReads, 0)
    }

    /// Regression: disconnecting while the year-long read is in flight must not import it.
    func testDisconnectDuringReadDoesNotImport() async {
        env.users.setHealthKitAuthorized(true)
        fake.snapshotToReturn = someData()
        fake.holdNextRead = true

        env.syncHealthData(force: true)
        await waitUntil { fake.isHoldingRead }
        env.users.setHealthKitAuthorized(false)   // she taps Disconnect mid-read
        fake.releaseRead()
        await waitForIdle()

        XCTAssertFalse(env.hasImportedHealthData)
    }

    /// Regression: "Sync now" (or switching an item back on) while a sync is running used
    /// to be dropped. It now runs once the current sync finishes.
    func testForcedSyncDuringAnotherIsQueuedNotDropped() async {
        env.users.setHealthKitAuthorized(true)
        fake.snapshotToReturn = someData()
        fake.holdNextRead = true

        env.syncHealthData(force: true)
        await waitUntil { fake.isHoldingRead }
        env.syncHealthData(force: true)            // arrives mid-sync
        fake.releaseRead()
        await waitUntil { fake.snapshotReads == 2 }
        await waitForIdle()

        XCTAssertEqual(fake.snapshotReads, 2)
    }

    /// An unforced (foreground) request during a sync is simply coalesced, not queued.
    func testUnforcedSyncDuringAnotherIsCoalesced() async {
        env.users.setHealthKitAuthorized(true)
        fake.snapshotToReturn = someData()
        fake.holdNextRead = true

        env.syncHealthData(force: true)
        await waitUntil { fake.isHoldingRead }
        env.syncHealthData()
        fake.releaseRead()
        await waitForIdle()
        try? await Task.sleep(for: .milliseconds(50))

        XCTAssertEqual(fake.snapshotReads, 1)
    }

    /// An empty read (access not yet propagated) leaves the next foreground sync due;
    /// a read with data starts the 30-minute window.
    func testEmptyReadDoesNotConsumeThrottleButDataDoes() async {
        env.users.setHealthKitAuthorized(true)

        env.syncHealthData()                       // empty
        await waitForIdle()
        env.syncHealthData()                       // still due
        await waitForIdle()
        XCTAssertEqual(fake.snapshotReads, 2)

        fake.snapshotToReturn = someData()
        env.syncHealthData()                       // returns data, stamps the window
        await waitForIdle()
        env.syncHealthData()                       // throttled
        await waitForIdle()
        XCTAssertEqual(fake.snapshotReads, 3)
        XCTAssertTrue(env.hasImportedHealthData)
    }

    /// Close account resets the throttle and her per-item choices for the next account.
    func testResetHealthSyncStateClearsThrottleAndChoices() async {
        env.users.setHealthKitAuthorized(true)
        env.settings.disabledHealthItemIDs = ["exercise"]
        fake.snapshotToReturn = someData()
        env.syncHealthData()
        await waitForIdle()

        env.resetHealthSyncState()

        XCTAssertTrue(env.settings.disabledHealthItemIDs.isEmpty)
        env.syncHealthData()                       // throttle forgotten, so due again
        await waitForIdle()
        XCTAssertEqual(fake.snapshotReads, 2)
    }

    /// Only live rows count as imported (matches the Apple Health screen's note).
    func testSoftDeletedRowsDontCountAsImported() {
        let sample = HealthSample(typeID: "restingHeartRate", day: day, value: 60, unit: "bpm", ownerID: "o")
        sample.deletedAt = .now
        env.context.insert(sample)
        try? env.context.save()
        XCTAssertFalse(env.hasImportedHealthData)
    }
}

/// GP summary: Home confirms before discarding a draft past the first step.
final class GPSummaryLeaveTests: XCTestCase {
    func testHomeConfirmsOnlyPastTheFirstStep() {
        XCTAssertFalse(GPSummaryFlowModel.homeNeedsConfirmation(at: .period))
        XCTAssertTrue(GPSummaryFlowModel.homeNeedsConfirmation(at: .review))
        XCTAssertTrue(GPSummaryFlowModel.homeNeedsConfirmation(at: .details))
        XCTAssertTrue(GPSummaryFlowModel.homeNeedsConfirmation(at: .preview))
    }
}
