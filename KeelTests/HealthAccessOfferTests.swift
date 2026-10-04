import XCTest
import SwiftData
@testable import Keel

/// Apple Health access when she opens the app. iOS would show the permission sheet when
/// she hasn't been asked on this install, or when Keel reads something new (periods).
/// Keel offers, in context, once: "Connect Apple Health?" if she isn't connected,
/// "Update Apple Health access?" if she is. A background sync never raises the sheet,
/// and never disconnects her: what she already shared keeps syncing.
@MainActor
final class HealthAccessOfferTests: XCTestCase {

    private final class Source: HealthDataSource {
        var status: HealthRequestStatus = .shouldRequest
        var authorizationRequests = 0
        var snapshotReads = 0
        func requestAuthorization() async -> Bool { authorizationRequests += 1; return true }
        func snapshot(lastDays: Int) async -> HealthSnapshot {
            snapshotReads += 1
            var s = HealthSnapshot(); s.sleepByDay = [Date.now.startOfDay: 7]
            return s
        }
        func requestStatus() async -> HealthRequestStatus { status }
    }

    private var source: Source!
    private var env: AppEnvironment!

    private func clearOfferDefaults() {
        UserDefaults.standard.removeObject(forKey: "keel.healthOfferDeclined")
        UserDefaults.standard.removeObject(forKey: "keel.healthReconnectAnswered")
    }

    override func setUp() async throws {
        clearOfferDefaults()
        source = Source()
        env = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true),
                             provider: NoopSyncProvider(), health: source)
        env.markOnboarded()                                        // a returning user
        env.users.upsertProfile(firstName: "Mischa", email: nil, appleUserID: nil)
    }

    override func tearDown() async throws {
        env.auth.signOut()
        clearOfferDefaults()
    }

    private func waitForSync() async {
        let deadline = Date.now.addingTimeInterval(2)
        while env.isHealthSyncInFlight, Date.now < deadline { try? await Task.sleep(for: .milliseconds(5)) }
    }

    // MARK: The decision

    func testOfferDecision() {
        typealias E = AppEnvironment
        // Not connected: Connect, until she answers it.
        XCTAssertEqual(E.healthOffer(status: .shouldRequest, hasOnboarded: true, connected: false,
                                     connectAnswered: false, reconnectAnswered: false), .connect)
        XCTAssertEqual(E.healthOffer(status: .shouldRequest, hasOnboarded: true, connected: false,
                                     connectAnswered: true, reconnectAnswered: false), .none)
        // Connected: Update, until she answers it; an old Connect answer doesn't matter.
        XCTAssertEqual(E.healthOffer(status: .shouldRequest, hasOnboarded: true, connected: true,
                                     connectAnswered: true, reconnectAnswered: false), .reconnect)
        XCTAssertEqual(E.healthOffer(status: .shouldRequest, hasOnboarded: true, connected: true,
                                     connectAnswered: false, reconnectAnswered: true), .none)
        // Only when iOS would actually ask, and only after onboarding.
        XCTAssertEqual(E.healthOffer(status: .unnecessary, hasOnboarded: true, connected: false,
                                     connectAnswered: false, reconnectAnswered: false), .none)
        XCTAssertEqual(E.healthOffer(status: .unknown, hasOnboarded: true, connected: true,
                                     connectAnswered: false, reconnectAnswered: false), .none)
        XCTAssertEqual(E.healthOffer(status: .shouldRequest, hasOnboarded: false, connected: false,
                                     connectAnswered: false, reconnectAnswered: false), .none)
    }

    // MARK: Not connected

    func testRestoredPhoneNotConnectedIsOfferedConnect() async {
        source.status = .shouldRequest
        let offer = await env.checkHealthAccessOnLaunch()
        XCTAssertEqual(offer, .connect)
    }

    /// Not now is final for this install: no offer on any later launch.
    func testNotNowIsNotAskedAgain() async {
        source.status = .shouldRequest
        env.answerHealthOffer(.connect)
        for _ in 0..<3 {
            let offer = await env.checkHealthAccessOnLaunch()
            XCTAssertEqual(offer, .none)
        }
    }

    /// Regression (review): tapping Connect also counts as her answer, so if Connect can't
    /// complete (Health restricted on the phone) the offer doesn't return every launch.
    func testConnectCountsAsAnAnswerToo() async {
        source.status = .shouldRequest
        let offer1 = await env.checkHealthAccessOnLaunch()
        XCTAssertEqual(offer1, .connect)
        env.answerHealthOffer(.connect)                       // what the Connect button does first
        let offer2 = await env.checkHealthAccessOnLaunch()
        XCTAssertEqual(offer2, .none)
    }

    /// Skip in onboarding's Health step means no Connect offer on launch.
    func testSkippedInOnboardingIsNotOffered() async {
        source.status = .shouldRequest
        env.declineHealthConnectOffer()
        let offer3 = await env.checkHealthAccessOnLaunch()
        XCTAssertEqual(offer3, .none)
    }

    /// She answered the iOS sheet (allowed or refused): iOS won't show it again, and
    /// Keel doesn't offer either.
    func testOnceSheHasAnsweredTheSheetSheIsNotAskedAgain() async {
        source.status = .unnecessary
        let offer4 = await env.checkHealthAccessOnLaunch()
        XCTAssertEqual(offer4, .none)
    }

    /// Includes a reinstall: the database (and its onboarded record) is gone, so she
    /// onboards again and connects there.
    func testNothingOfferedBeforeOnboardingWhichHasItsOwnConnectStep() async {
        let notOnboarded = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true),
                                          provider: NoopSyncProvider(), health: source)
        source.status = .shouldRequest
        let offer5 = await notOnboarded.checkHealthAccessOnLaunch()
        XCTAssertEqual(offer5, .none)
        _ = notOnboarded
    }

    // MARK: Connected, and Keel now reads something new

    /// Keel now reads periods, so iOS would ask a connected user again. She's offered
    /// Update once, even past an earlier Not now on the Connect offer, and stays connected.
    func testConnectedUserIsOfferedUpdateOnceAndStaysConnected() async {
        env.declineHealthConnectOffer()                       // an old Not now, before she connected
        env.users.setHealthKitAuthorized(true)
        source.status = .shouldRequest

        let offer6 = await env.checkHealthAccessOnLaunch()

        XCTAssertEqual(offer6, .reconnect)
        XCTAssertEqual(env.users.currentProfile()?.healthKitAuthorized, true)

        env.answerHealthOffer(.reconnect)                     // Not now
        let offer7 = await env.checkHealthAccessOnLaunch()
        XCTAssertEqual(offer7, .none)
        XCTAssertEqual(env.users.currentProfile()?.healthKitAuthorized, true)
    }

    /// Regression (review): on launch the background sync ran first and cleared the
    /// connected flag before the check, so the Update offer never appeared. The sync now
    /// leaves her connected, so the launch check (which runs after it) still offers.
    func testLaunchSyncBeforeTheCheckDoesNotHideTheUpdateOffer() async {
        env.users.setHealthKitAuthorized(true)
        source.status = .shouldRequest
        env.syncHealthData(force: true)                       // what bootstrap does at launch
        await waitForSync()
        let offer8 = await env.checkHealthAccessOnLaunch()
        XCTAssertEqual(offer8, .reconnect)
    }

    /// Regression (review): with a new type to ask about, the background sync used to stop
    /// all syncing. It now never raises the sheet, keeps her connected, and still reads
    /// what she has already shared (sleep, steps, vitals).
    func testBackgroundSyncKeepsReadingWithoutAsking() async {
        env.users.setHealthKitAuthorized(true)
        source.status = .shouldRequest
        env.syncHealthData(force: true)
        await waitForSync()
        XCTAssertEqual(source.authorizationRequests, 0)
        XCTAssertEqual(source.snapshotReads, 1)
        XCTAssertEqual(env.users.currentProfile()?.healthKitAuthorized, true)
        XCTAssertTrue(env.hasImportedHealthData)
    }

    /// An unknown answer from iOS (a transient error) must not lead to asking either.
    func testUnknownStatusNeverAsks() async {
        env.users.setHealthKitAuthorized(true)
        source.status = .unknown
        env.syncHealthData(force: true)
        await waitForSync()
        XCTAssertEqual(source.authorizationRequests, 0)
    }

    /// Once she has answered for everything Keel reads, the Update answer is forgotten,
    /// so a later new type gets its own single offer.
    func testUpdateAnswerResetsOnceEverythingIsAnswered() async {
        env.users.setHealthKitAuthorized(true)
        env.answerHealthOffer(.reconnect)
        source.status = .unnecessary
        _ = await env.checkHealthAccessOnLaunch()
        XCTAssertFalse(env.settings.healthReconnectOfferAnswered)
        source.status = .shouldRequest                        // a future new read type
        let offer9 = await env.checkHealthAccessOnLaunch()
        XCTAssertEqual(offer9, .reconnect)
    }

    func testDeleteAllMyDataForgetsTheAnswers() {
        env.answerHealthOffer(.connect)
        env.answerHealthOffer(.reconnect)
        env.settings.resetToDefaults()
        XCTAssertFalse(env.settings.healthConnectOfferDeclined)
        XCTAssertFalse(env.settings.healthReconnectOfferAnswered)
    }
}
