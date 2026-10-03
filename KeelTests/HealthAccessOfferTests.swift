import XCTest
import SwiftData
@testable import Keel

/// Apple Health access when she opens the app. When she has onboarded but iOS would ask
/// for Health access again (her data is back, for example on a phone restored from a
/// backup, but the Health grant isn't) Keel offers to connect, once. (A plain reinstall
/// deletes the database, onboarded record included, so she onboards again.) It never asks again after Not now, and never when she has already answered
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
        env.markOnboarded()                                        // a returning user
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

    /// Includes a reinstall: the database (and its onboarded record) is gone, so she
    /// onboards again and connects there.
    func testNothingOfferedBeforeOnboardingWhichHasItsOwnConnectStep() async {
        let notOnboarded = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true),
                                          provider: NoopSyncProvider(), health: source)
        source.status = .shouldRequest
        let offer = await notOnboarded.checkHealthAccessOnLaunch()
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

    /// Keel now reads periods too, so iOS asks again for someone already connected.
    /// She had chosen to connect, so she's offered once even past an earlier Not now;
    /// a Not now this time is final.
    func testConnectedUserIsOfferedOnceWhenKeelReadsSomethingNew() async {
        env.declineHealthConnectOffer()                 // an old Not now, before she connected
        env.users.setHealthKitAuthorized(true)
        source.status = .shouldRequest
        let first = await env.checkHealthAccessOnLaunch()
        XCTAssertTrue(first)
        XCTAssertEqual(env.users.currentProfile()?.healthKitAuthorized, false)

        env.declineHealthConnectOffer()                 // Not now again
        let second = await env.checkHealthAccessOnLaunch()
        XCTAssertFalse(second)
    }

    func testDeleteAllMyDataForgetsTheDecline() {
        env.declineHealthConnectOffer()
        env.settings.resetToDefaults()
        XCTAssertFalse(env.settings.healthConnectOfferDeclined)
    }
}
