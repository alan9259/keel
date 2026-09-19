import XCTest
@testable import Keel

/// A fake biometric check so the lock logic is testable without a device or user.
private final class FakeAuthenticator: BiometricAuthenticating, @unchecked Sendable {
    var biometry: BiometryKind = .faceID
    var canAuth = true
    var result = true
    private(set) var authCalls = 0

    func availableBiometry() -> BiometryKind { biometry }
    func canAuthenticate() -> Bool { canAuth }
    func authenticate(reason: String) async -> Bool { authCalls += 1; return result }
}

@MainActor
final class AppLockServiceTests: XCTestCase {
    private func freshDefaults() -> UserDefaults {
        UserDefaults(suiteName: "test.applock.\(UUID().uuidString)")!
    }

    func testDisabledAndUnlockedByDefault() {
        let svc = AppLockService(auth: FakeAuthenticator(), defaults: freshDefaults())
        XCTAssertFalse(svc.isEnabled)
        XCTAssertFalse(svc.isLocked)
    }

    func testEnableRequiresSuccessfulAuth() async {
        let fake = FakeAuthenticator(); fake.result = true
        let svc = AppLockService(auth: fake, defaults: freshDefaults())
        let ok = await svc.enable()
        XCTAssertTrue(ok)
        XCTAssertTrue(svc.isEnabled)
        XCTAssertFalse(svc.isLocked) // unlocked immediately after enabling
    }

    func testEnableFailsWhenAuthCancelled() async {
        let fake = FakeAuthenticator(); fake.result = false
        let svc = AppLockService(auth: fake, defaults: freshDefaults())
        let ok = await svc.enable()
        XCTAssertFalse(ok)
        XCTAssertFalse(svc.isEnabled)
    }

    func testEnableDoesNotPromptWhenDeviceCannotAuthenticate() async {
        let fake = FakeAuthenticator(); fake.canAuth = false
        let svc = AppLockService(auth: fake, defaults: freshDefaults())
        let ok = await svc.enable()
        XCTAssertFalse(ok)
        XCTAssertFalse(svc.isEnabled)
        XCTAssertEqual(fake.authCalls, 0)
    }

    func testColdLaunchStartsLockedWhenEnabled() async {
        let defaults = freshDefaults()
        _ = await AppLockService(auth: FakeAuthenticator(), defaults: defaults).enable()
        // A new instance models the next cold launch reading persisted state.
        let relaunched = AppLockService(auth: FakeAuthenticator(), defaults: defaults)
        XCTAssertTrue(relaunched.isEnabled)
        XCTAssertTrue(relaunched.isLocked)
    }

    func testLockIfEnabledOnlyLocksWhenEnabled() async {
        let off = AppLockService(auth: FakeAuthenticator(), defaults: freshDefaults())
        off.lockIfEnabled()
        XCTAssertFalse(off.isLocked)

        let on = AppLockService(auth: FakeAuthenticator(), defaults: freshDefaults())
        _ = await on.enable()
        on.lockIfEnabled()
        XCTAssertTrue(on.isLocked)
    }

    func testUnlockSuccessClearsLockFailureKeepsIt() async {
        let fake = FakeAuthenticator()
        let svc = AppLockService(auth: fake, defaults: freshDefaults())
        _ = await svc.enable()
        svc.lockIfEnabled()
        XCTAssertTrue(svc.isLocked)

        fake.result = false
        let failed = await svc.unlock()
        XCTAssertFalse(failed)
        XCTAssertTrue(svc.isLocked)

        fake.result = true
        let unlocked = await svc.unlock()
        XCTAssertTrue(unlocked)
        XCTAssertFalse(svc.isLocked)
    }

    func testDisablePersistsOff() async {
        let defaults = freshDefaults()
        let svc = AppLockService(auth: FakeAuthenticator(), defaults: defaults)
        _ = await svc.enable()
        svc.lockIfEnabled()
        svc.disable()
        XCTAssertFalse(svc.isEnabled)
        XCTAssertFalse(svc.isLocked)
        // Next cold launch is not locked.
        let relaunched = AppLockService(auth: FakeAuthenticator(), defaults: defaults)
        XCTAssertFalse(relaunched.isLocked)
    }

    func testBiometryLabels() {
        XCTAssertEqual(BiometryKind.faceID.settingLabel, "Face ID")
        XCTAssertEqual(BiometryKind.touchID.settingLabel, "Touch ID")
        XCTAssertEqual(BiometryKind.none.settingLabel, "passcode")
    }
}
