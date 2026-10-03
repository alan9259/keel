import XCTest
import SwiftData
@testable import Keel

/// Apple Health access when she opens the app. After a reinstall (iOS drops an app's
/// Health permissions when it's deleted, but Keel's "onboarded" flag survives in the
/// Keychain, so onboarding and its Connect step are skipped) Keel offers to connect,
/// once. It never asks again after Not now, and never when she has already answered
/// the iOS sheet, whether she allowed or refused.
@MainActor
final class HealthAccessOfferTests: XCTestCase {

    private final class Source: HealthDataSource {
        var status: HealthRequestStatus = .shouldRequest
        var authorizationRequests = 0
        func requestAuthorization() async -> Bool { authorizationRequests += 1; return true }
        func snapshot(lastDays: Int) async -> HealthSnapshot { HealthSnapshot() }
        func requestStatus() async -> HealthRequestStatus { status }
    }

    private var source: Source!
    private var env: AppEnvironment!

    override func setUp() async throws {
        UserDefaults.standard.removeObject(forKey: "keel.healthOfferDeclined")
        source = Source()
        env = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true),
                             provider: NoopSyncProvider(), health: source)
        env.auth.markOnboarded()                                   // a returning user
        env.users.upsertProfile(firstName: "Mischa", email: nil, appleUserID: nil)
    }

    override func tearDown() async throws {
        env.auth.signOut()
        UserDefaults.standard.removeObject(forKey: "keel.healthOfferDeclined")
    }

    // MARK: The decision

    func testOffersOnlyWhenIOSWouldAskAndSheHasNotDeclined() {
        XCTAssertTrue(AppEnvironment.shouldOfferHealthConnect(status: .shouldRequest, hasOnboarded: true, declined: false))
        XCTAssertFalse(AppEnvironment.shouldOfferHealthConnect(status: .shouldRequest, hasOnboarded: true, declined: true))
        XCTAssertFalse(AppEnvironment.shouldOfferHealthConnect(status: .unnecessary, hasOnboarded: true, declined: false))
        XCTAssertFalse(AppEnvironment.shouldOfferHealthConnect(status: .unknown, hasOnboarded: true, declined: false))
        XCTAssertFalse(AppEnvironment.shouldOfferHealthConnect(status: .shouldRequest, hasOnboarded: false, declined: false))
    }

    // MARK: On opening the app

    /// The reported bug: reinstalled, access gone, nothing asked. Now it's offered.
    func testAfterAReinstallItOffersToConnect() async {
        source.status = .shouldRequest
        let offer = await env.checkHealthAccessOnLaunch()
        XCTAssertTrue(offer)
    }

    /// Not now is final for this install: no offer on any later launch.
    func testNotNowIsNotAskedAgain() async {
        source.status = .shouldRequest
        env.declineHealthConnectOffer()
        for _ in 0..<3 {
            let offer = await env.checkHealthAccessOnLaunch()
            XCTAssertFalse(offer)
        }
    }

    /// She answered the iOS sheet (allowed or refused): iOS won't show it again, and
    /// Keel doesn't offer either.
    func testOnceSheHasAnsweredTheSheetSheIsNotAskedAgain() async {
        source.status = .unnecessary
        let offer = await env.checkHealthAccessOnLaunch()
        XCTAssertFalse(offer)
    }

    func testNothingOfferedBeforeOnboardingWhichHasItsOwnConnectStep() async {
        env.auth.signOut()
        source.status = .shouldRequest
        let offer = await env.checkHealthAccessOnLaunch()
        XCTAssertFalse(offer)
    }

    // MARK: Access lost while the profile still says connected

    /// Restored onto a new phone (or reinstalled with data restored): the profile says
    /// connected but iOS would ask again. The launch check clears the stale flag.
    func testStaleConnectedFlagIsCleared() async {
        env.users.setHealthKitAuthorized(true)
        source.status = .shouldRequest
        _ = await env.checkHealthAccessOnLaunch()
        XCTAssertEqual(env.users.currentProfile()?.healthKitAuthorized, false)
    }

    /// Regression guard: a background sync must never raise the iOS sheet out of
    /// context. With access lost it stops before asking, and clears the flag.
    func testBackgroundSyncNeverAsksWhenAccessWasLost() async {
        env.users.setHealthKitAuthorized(true)
        source.status = .shouldRequest
        env.syncHealthData(force: true)
        let deadline = Date.now.addingTimeInterval(2)
        while env.isHealthSyncInFlight, Date.now < deadline { try? await Task.sleep(for: .milliseconds(5)) }
        XCTAssertEqual(source.authorizationRequests, 0)
        XCTAssertEqual(env.users.currentProfile()?.healthKitAuthorized, false)
    }

    func testDeleteAllMyDataForgetsTheDecline() {
        env.declineHealthConnectOffer()
        env.settings.resetToDefaults()
        XCTAssertFalse(env.settings.healthConnectOfferDeclined)
    }
}
