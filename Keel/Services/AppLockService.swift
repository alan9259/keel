import Foundation
import LocalAuthentication

/// Which biometric the device offers, in Keel's own terms so the rest of the app
/// (and its tests) don't depend on LocalAuthentication types.
enum BiometryKind: Equatable {
    case faceID, touchID, none

    /// For sentences ("Unlock with …").
    var label: String {
        switch self {
        case .faceID: "Face ID"
        case .touchID: "Touch ID"
        case .none: "your passcode"
        }
    }
    /// For the Settings toggle title ("Require …").
    var settingLabel: String {
        switch self {
        case .faceID: "Face ID"
        case .touchID: "Touch ID"
        case .none: "passcode"
        }
    }
    var symbol: String {
        switch self {
        case .faceID: "faceid"
        case .touchID: "touchid"
        case .none: "lock.fill"
        }
    }
}

/// The platform biometric check, behind a protocol so `AppLockService` is testable
/// with a fake (LocalAuthentication needs a real device and user interaction).
protocol BiometricAuthenticating {
    func availableBiometry() -> BiometryKind
    /// Whether a biometric is enrolled (Face ID / Touch ID).
    func canAuthenticate() -> Bool
    /// Run the Face ID / Touch ID prompt. Keel's own PIN is the fallback, so this is
    /// biometrics only (no device-passcode screen).
    func authenticate(reason: String) async -> Bool
    /// Whether the device can authenticate the owner (a biometric OR a device passcode),
    /// used for the forgot-PIN recovery.
    func canAuthenticateOwner() -> Bool
    /// Prove device ownership with the device passcode (or biometric) — the recovery path.
    func authenticateOwner(reason: String) async -> Bool
}

/// Real implementation over `LAContext` using `.deviceOwnerAuthenticationWithBiometrics`
/// (biometrics only — the Keel PIN is the fallback we render ourselves).
struct SystemBiometricAuthenticator: BiometricAuthenticating {
    private let policy: LAPolicy = .deviceOwnerAuthenticationWithBiometrics

    func availableBiometry() -> BiometryKind {
        let ctx = LAContext()
        _ = ctx.canEvaluatePolicy(policy, error: nil) // populates biometryType
        switch ctx.biometryType {
        case .faceID: return .faceID
        case .touchID: return .touchID
        default: return .none
        }
    }

    func canAuthenticate() -> Bool {
        LAContext().canEvaluatePolicy(policy, error: nil)
    }

    func authenticate(reason: String) async -> Bool {
        await evaluate(policy, reason: reason)
    }

    func canAuthenticateOwner() -> Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil)
    }

    func authenticateOwner(reason: String) async -> Bool {
        await evaluate(.deviceOwnerAuthentication, reason: reason)
    }

    private func evaluate(_ policy: LAPolicy, reason: String) async -> Bool {
        let ctx = LAContext()
        return await withCheckedContinuation { continuation in
            ctx.evaluatePolicy(policy, localizedReason: reason) { success, _ in
                continuation.resume(returning: success)
            }
        }
    }
}

/// Gates the whole app behind a 4-digit Keel PIN, with Face ID / Touch ID as the
/// quick unlock on top. Off by default; she sets a PIN in Settings to turn it on.
/// When on, the app starts locked on a cold launch and re-locks after the grace
/// window when it goes to the background, so it never opens without authenticating.
/// Repeated wrong PINs trigger a timed lockout.
@MainActor
@Observable
final class AppLockService {
    private let biometrics: BiometricAuthenticating
    private let store: PINSecureStore
    private let defaults: UserDefaults
    private let enabledKey = "keel.appLockEnabled"
    private let biometricsKey = "keel.appLockBiometrics"
    private let attemptsKey = "keel.pinFailedAttempts"
    private let lockoutKey = "keel.pinLockedOutUntil"

    let pinLength = 4
    /// Wrong PINs allowed before a timed lockout kicks in.
    static let maxAttempts = 5
    static let lockoutDuration: TimeInterval = 60
    /// Grace window: returning within this long after leaving doesn't force re-auth.
    static let graceInterval: TimeInterval = 30

    private(set) var isEnabled: Bool
    /// True while the unlock gate should be shown. Only ever true when `isEnabled`.
    private(set) var isLocked: Bool
    /// Her preference to unlock with Face ID / Touch ID (on by default). Only takes
    /// effect where the device actually has a biometric — see `useBiometrics`.
    private(set) var biometricUnlockEnabled: Bool
    private(set) var failedAttempts: Int
    private(set) var lockedOutUntil: Date?
    private var backgroundedAt: Date?

    init(biometrics: BiometricAuthenticating = SystemBiometricAuthenticator(),
         store: PINSecureStore = KeychainPINStore(),
         defaults: UserDefaults = .standard) {
        self.biometrics = biometrics
        self.store = store
        self.defaults = defaults
        // The lock is on only when she explicitly enabled it (a flag that clears on
        // uninstall) AND a PIN hash is present. This stops a fresh install from locking
        // on a stale Keychain PIN left by a previous install (Keychain survives an
        // uninstall, UserDefaults doesn't), so a user who never set a PIN is never locked.
        let flag = defaults.bool(forKey: enabledKey)
        if !flag && store.hasPIN { store.clear() } // tidy up a stale PIN
        let enabled = flag && store.hasPIN
        self.isEnabled = enabled
        self.isLocked = enabled // locked on a cold launch when the lock is on
        self.biometricUnlockEnabled = defaults.object(forKey: biometricsKey) as? Bool ?? true
        self.failedAttempts = defaults.integer(forKey: attemptsKey)
        let until = defaults.double(forKey: lockoutKey)
        self.lockedOutUntil = until > 0 ? Date(timeIntervalSince1970: until) : nil
    }

    /// The biometric on this device, if any (Face ID / Touch ID), for labels + the button.
    var biometry: BiometryKind { biometrics.availableBiometry() }
    /// Whether Face ID / Touch ID can be offered (enrolled). The PIN works regardless.
    var biometricsAvailable: Bool { biometrics.canAuthenticate() }
    /// Whether to actually use Face ID / Touch ID to unlock: her preference AND a
    /// biometric being available. When true the gate leads with it; the PIN is the fallback.
    var useBiometrics: Bool { biometricUnlockEnabled && biometricsAvailable }

    /// Turn Face ID / Touch ID unlock on or off (the PIN stays as the fallback).
    func setBiometricUnlock(_ on: Bool) {
        biometricUnlockEnabled = on
        defaults.set(on, forKey: biometricsKey)
    }

    // MARK: Setup

    /// Create or change the PIN, which turns the lock on. Rejects a PIN of the wrong
    /// length or with non-digits. The PIN is hashed with a random salt before storage.
    @discardableResult
    func setPIN(_ pin: String) -> Bool {
        guard pin.count == pinLength, pin.allSatisfy(\.isNumber) else { return false }
        let salt = PINHasher.makeSalt()
        store.save(salt: salt, hash: PINHasher.hash(pin: pin, salt: salt))
        defaults.set(true, forKey: enabledKey)
        isEnabled = true
        isLocked = false
        resetAttempts()
        return true
    }

    /// Turn the lock off and forget the PIN. She is already inside the app to do this.
    func disable() {
        store.clear()
        defaults.set(false, forKey: enabledKey)
        isEnabled = false
        isLocked = false
        resetAttempts()
    }

    // MARK: Unlock

    func isLockedOut(now: Date = .now) -> Bool {
        guard let lockedOutUntil else { return false }
        return now < lockedOutUntil
    }

    func lockoutRemaining(now: Date = .now) -> TimeInterval {
        guard let lockedOutUntil, now < lockedOutUntil else { return 0 }
        return lockedOutUntil.timeIntervalSince(now)
    }

    /// Check a typed PIN. Honours the lockout, and records failures toward it. Returns
    /// whether it unlocked.
    @discardableResult
    func verifyPIN(_ pin: String, now: Date = .now) -> Bool {
        guard !isLockedOut(now: now), let cred = store.load() else { return false }
        let candidate = PINHasher.hash(pin: pin, salt: cred.salt)
        if PINHasher.constantTimeEquals(candidate, cred.hash) {
            isLocked = false
            resetAttempts()
            return true
        }
        registerFailure(now: now)
        return false
    }

    /// Whether the forgot-PIN recovery is possible (the device has a passcode or a
    /// biometric to prove ownership).
    var canRecoverWithPasscode: Bool { biometrics.canAuthenticateOwner() }

    /// Forgot-PIN recovery: prove device ownership with the device passcode (or a
    /// biometric). Returns success; the caller then lets her set a new PIN, or turns
    /// the lock off. Her data is untouched.
    @discardableResult
    func authenticateOwner() async -> Bool {
        guard biometrics.canAuthenticateOwner() else { return false }
        return await biometrics.authenticateOwner(reason: "Confirm it's you with your device passcode to reset your PIN")
    }

    /// Try Face ID / Touch ID. Returns whether it unlocked.
    @discardableResult
    func unlockWithBiometrics() async -> Bool {
        guard isLocked, useBiometrics else { return false }
        let ok = await biometrics.authenticate(reason: "Unlock Keel")
        if ok { isLocked = false; resetAttempts() }
        return ok
    }

    // MARK: Lifecycle

    /// Whether a return should re-lock: only once the grace window has elapsed. Pure.
    nonisolated static func shouldLock(now: Date, backgroundedAt: Date?, grace: TimeInterval) -> Bool {
        guard let backgroundedAt else { return false }
        return now.timeIntervalSince(backgroundedAt) >= grace
    }

    func markBackgrounded(now: Date = .now) {
        if isEnabled { backgroundedAt = now }
    }

    func applyForeground(now: Date = .now) {
        guard isEnabled else { backgroundedAt = nil; return }
        if Self.shouldLock(now: now, backgroundedAt: backgroundedAt, grace: Self.graceInterval) {
            isLocked = true
        }
        backgroundedAt = nil
    }

    // MARK: Attempts

    private func registerFailure(now: Date) {
        failedAttempts += 1
        if failedAttempts % Self.maxAttempts == 0 {
            lockedOutUntil = now.addingTimeInterval(Self.lockoutDuration)
        }
        persistAttempts()
    }

    private func resetAttempts() {
        failedAttempts = 0
        lockedOutUntil = nil
        persistAttempts()
    }

    private func persistAttempts() {
        defaults.set(failedAttempts, forKey: attemptsKey)
        defaults.set(lockedOutUntil?.timeIntervalSince1970 ?? 0, forKey: lockoutKey)
    }

    #if DEBUG
    /// Force the locked state on (in-memory) so the gate can be screenshotted on the
    /// simulator, where real biometric auth and the Keychain PIN aren't set up.
    func debugForceLocked() { isEnabled = true; isLocked = true }
    /// Force the enabled-but-unlocked state, to screenshot the App Lock settings screen.
    func debugForceEnabled() { isEnabled = true; isLocked = false }
    #endif
}

#if DEBUG
/// A stand-in authenticator that reports Face ID available, so the App Lock screen's
/// biometric toggle can be screenshotted on the simulator (which has no enrolled
/// biometric). It never actually authenticates.
struct DebugBiometricAuthenticator: BiometricAuthenticating {
    func availableBiometry() -> BiometryKind { .faceID }
    func canAuthenticate() -> Bool { true }
    func authenticate(reason: String) async -> Bool { false }
    func canAuthenticateOwner() -> Bool { true }
    func authenticateOwner(reason: String) async -> Bool { false }
}
#endif
