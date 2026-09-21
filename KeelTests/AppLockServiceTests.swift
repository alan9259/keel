import XCTest
@testable import Keel

/// A fake biometric check so the lock logic is testable without a device or user.
private final class FakeAuthenticator: BiometricAuthenticating, @unchecked Sendable {
    var biometry: BiometryKind = .faceID
    var canAuth = true
    var result = true
    var canOwner = true
    var ownerResult = true

    func availableBiometry() -> BiometryKind { biometry }
    func canAuthenticate() -> Bool { canAuth }
    func authenticate(reason: String) async -> Bool { result }
    func canAuthenticateOwner() -> Bool { canOwner }
    func authenticateOwner(reason: String) async -> Bool { ownerResult }
}

/// In-memory PIN store so the state machine is testable without the Keychain.
private final class InMemoryPINStore: PINSecureStore, @unchecked Sendable {
    private var salt: Data?
    private var hash: Data?
    var hasPIN: Bool { hash != nil }
    func save(salt: Data, hash: Data) { self.salt = salt; self.hash = hash }
    func load() -> (salt: Data, hash: Data)? {
        guard let salt, let hash else { return nil }
        return (salt, hash)
    }
    func clear() { salt = nil; hash = nil }
}

@MainActor
final class AppLockServiceTests: XCTestCase {
    private func freshDefaults() -> UserDefaults {
        UserDefaults(suiteName: "test.applock.\(UUID().uuidString)")!
    }

    private func service(store: InMemoryPINStore = InMemoryPINStore(),
                         fake: FakeAuthenticator = FakeAuthenticator(),
                         defaults: UserDefaults? = nil) -> AppLockService {
        AppLockService(biometrics: fake, store: store, defaults: defaults ?? freshDefaults())
    }

    /// A cold-launched, locked service: a PIN "1234" was set in a previous instance,
    /// sharing the same store + defaults so the flag and hash both persist.
    private func lockedService(fake: FakeAuthenticator = FakeAuthenticator()) -> AppLockService {
        let store = InMemoryPINStore()
        let defaults = freshDefaults()
        _ = AppLockService(biometrics: fake, store: store, defaults: defaults).setPIN("1234")
        return AppLockService(biometrics: fake, store: store, defaults: defaults)
    }

    // MARK: Setup

    func testDisabledAndUnlockedByDefault() {
        let svc = service()
        XCTAssertFalse(svc.isEnabled)
        XCTAssertFalse(svc.isLocked)
    }

    func testSetPINEnablesAndUnlocks() {
        let svc = service()
        XCTAssertTrue(svc.setPIN("1234"))
        XCTAssertTrue(svc.isEnabled)
        XCTAssertFalse(svc.isLocked)
    }

    func testSetPINRejectsWrongLengthOrNonDigits() {
        let svc = service()
        XCTAssertFalse(svc.setPIN("12"))
        XCTAssertFalse(svc.setPIN("12a4"))
        XCTAssertFalse(svc.isEnabled)
    }

    func testColdLaunchStartsLockedWhenPINSet() {
        let svc = lockedService()
        XCTAssertTrue(svc.isEnabled)
        XCTAssertTrue(svc.isLocked)
    }

    /// Regression: a leftover Keychain PIN from a previous install (UserDefaults was
    /// cleared on uninstall, so the enabled flag is gone) must not lock a fresh install,
    /// and the stale PIN is tidied away.
    func testStalePINWithoutEnabledFlagDoesNotLock() {
        let store = InMemoryPINStore()
        store.save(salt: Data([1, 2, 3]), hash: Data([4, 5, 6])) // stale, no enabled flag
        let svc = AppLockService(biometrics: FakeAuthenticator(), store: store, defaults: freshDefaults())
        XCTAssertFalse(svc.isEnabled)
        XCTAssertFalse(svc.isLocked)
        XCTAssertFalse(store.hasPIN)
    }

    // MARK: Verify

    func testVerifyCorrectPINUnlocks() {
        let svc = lockedService()
        XCTAssertTrue(svc.isLocked)
        XCTAssertTrue(svc.verifyPIN("1234"))
        XCTAssertFalse(svc.isLocked)
    }

    func testVerifyWrongPINFailsAndStaysLocked() {
        let svc = lockedService()
        XCTAssertFalse(svc.verifyPIN("0000"))
        XCTAssertTrue(svc.isLocked)
    }

    // MARK: Lockout

    func testLockoutAfterMaxAttempts() {
        let svc = lockedService()
        let start = Date.now
        for _ in 0..<AppLockService.maxAttempts { _ = svc.verifyPIN("0000", now: start) }
        XCTAssertTrue(svc.isLockedOut(now: start))
        XCTAssertFalse(svc.verifyPIN("1234", now: start)) // correct PIN refused while locked out
        let later = start.addingTimeInterval(AppLockService.lockoutDuration + 1)
        XCTAssertFalse(svc.isLockedOut(now: later))
        XCTAssertTrue(svc.verifyPIN("1234", now: later)) // works once the lockout passes
    }

    // MARK: Biometrics

    func testBiometricUnlock() async {
        let fake = FakeAuthenticator(); fake.result = true
        let svc = lockedService(fake: fake)
        XCTAssertTrue(svc.isLocked)
        let ok = await svc.unlockWithBiometrics()
        XCTAssertTrue(ok)
        XCTAssertFalse(svc.isLocked)
    }

    func testBiometricUnlockFailsWhenUnavailable() async {
        let fake = FakeAuthenticator(); fake.canAuth = false
        let svc = lockedService(fake: fake)
        let ok = await svc.unlockWithBiometrics()
        XCTAssertFalse(ok)
        XCTAssertTrue(svc.isLocked)
    }

    // MARK: Biometric-unlock toggle

    func testBiometricUnlockDefaultsOnAndReflectsAvailability() {
        let fake = FakeAuthenticator(); fake.canAuth = true
        let svc = service(fake: fake)
        XCTAssertTrue(svc.biometricUnlockEnabled) // default on
        XCTAssertTrue(svc.useBiometrics)          // and a biometric is available
    }

    func testUseBiometricsFalseWhenTurnedOff() {
        let svc = service()
        svc.setBiometricUnlock(false)
        XCTAssertFalse(svc.biometricUnlockEnabled)
        XCTAssertFalse(svc.useBiometrics)
    }

    func testUseBiometricsFalseWhenNoBiometricAvailable() {
        let fake = FakeAuthenticator(); fake.canAuth = false // no Face ID / Touch ID
        let svc = service(fake: fake)
        XCTAssertTrue(svc.biometricUnlockEnabled) // preference is still on...
        XCTAssertFalse(svc.useBiometrics)         // ...but there's nothing to use
    }

    func testBiometricUnlockRefusedWhenPreferenceOff() async {
        let fake = FakeAuthenticator(); fake.result = true
        let svc = lockedService(fake: fake)
        svc.setBiometricUnlock(false)
        let ok = await svc.unlockWithBiometrics()
        XCTAssertFalse(ok)
        XCTAssertTrue(svc.isLocked)
    }

    // MARK: Forgot-PIN recovery (device passcode)

    func testCanRecoverReflectsDeviceOwnerAuth() {
        let fake = FakeAuthenticator(); fake.canOwner = true
        XCTAssertTrue(service(fake: fake).canRecoverWithPasscode)
        let noPass = FakeAuthenticator(); noPass.canOwner = false
        XCTAssertFalse(service(fake: noPass).canRecoverWithPasscode)
    }

    func testAuthenticateOwnerSucceedsWithDevicePasscode() async {
        let fake = FakeAuthenticator(); fake.ownerResult = true
        let svc = lockedService(fake: fake)
        let ok = await svc.authenticateOwner()
        XCTAssertTrue(ok)
    }

    func testAuthenticateOwnerFailsWithoutDeviceAuth() async {
        let fake = FakeAuthenticator(); fake.canOwner = false
        let svc = lockedService(fake: fake)
        let ok = await svc.authenticateOwner()
        XCTAssertFalse(ok)
    }

    // MARK: Disable

    func testDisableClearsPIN() {
        let store = InMemoryPINStore()
        let svc = service(store: store)
        _ = svc.setPIN("1234")
        svc.disable()
        XCTAssertFalse(svc.isEnabled)
        XCTAssertFalse(store.hasPIN)
        XCTAssertFalse(svc.isLocked)
    }

    // MARK: Grace period

    func testShouldLockOnlyAfterGraceElapses() {
        let now = Date.now
        XCTAssertFalse(AppLockService.shouldLock(now: now, backgroundedAt: nil, grace: 30))
        XCTAssertFalse(AppLockService.shouldLock(now: now, backgroundedAt: now.addingTimeInterval(-5), grace: 30))
        XCTAssertTrue(AppLockService.shouldLock(now: now, backgroundedAt: now.addingTimeInterval(-31), grace: 30))
    }

    func testQuickReturnWithinGraceDoesNotLock() {
        let svc = service()
        _ = svc.setPIN("1234")
        let left = Date.now
        svc.markBackgrounded(now: left)
        svc.applyForeground(now: left.addingTimeInterval(10))
        XCTAssertFalse(svc.isLocked)
    }

    func testReturnAfterGraceLocks() {
        let svc = service()
        _ = svc.setPIN("1234")
        let left = Date.now
        svc.markBackgrounded(now: left)
        svc.applyForeground(now: left.addingTimeInterval(AppLockService.graceInterval + 1))
        XCTAssertTrue(svc.isLocked)
    }

    func testBiometryLabels() {
        XCTAssertEqual(BiometryKind.faceID.settingLabel, "Face ID")
        XCTAssertEqual(BiometryKind.touchID.settingLabel, "Touch ID")
        XCTAssertEqual(BiometryKind.none.label, "your passcode")
    }
}
