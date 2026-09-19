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
    /// Whether the device can authenticate at all: an enrolled biometric OR a passcode set.
    func canAuthenticate() -> Bool
    /// Run the Face ID / Touch ID prompt, falling back to the device passcode.
    func authenticate(reason: String) async -> Bool
}

/// Real implementation over `LAContext` using `.deviceOwnerAuthentication` — that is
/// biometrics with an automatic device-passcode fallback.
struct SystemBiometricAuthenticator: BiometricAuthenticating {
    func availableBiometry() -> BiometryKind {
        let ctx = LAContext()
        _ = ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: nil) // populates biometryType
        switch ctx.biometryType {
        case .faceID: return .faceID
        case .touchID: return .touchID
        default: return .none
        }
    }

    func canAuthenticate() -> Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil)
    }

    func authenticate(reason: String) async -> Bool {
        let ctx = LAContext()
        return await withCheckedContinuation { continuation in
            ctx.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) { success, _ in
                continuation.resume(returning: success)
            }
        }
    }
}

/// Gates the whole app behind Face ID / Touch ID (device passcode as the fallback).
/// Off by default; she turns it on in Settings. When on, the app starts locked on a
/// cold launch and re-locks whenever it goes to the background, so it never opens
/// without her authenticating.
@MainActor
@Observable
final class AppLockService {
    private let auth: BiometricAuthenticating
    private let defaults: UserDefaults
    private let enabledKey = "keel.appLockEnabled"

    private(set) var isEnabled: Bool
    /// True while the unlock gate should be shown. Only ever true when `isEnabled`.
    private(set) var isLocked: Bool

    init(auth: BiometricAuthenticating = SystemBiometricAuthenticator(), defaults: UserDefaults = .standard) {
        self.auth = auth
        self.defaults = defaults
        let enabled = defaults.bool(forKey: enabledKey)
        self.isEnabled = enabled
        self.isLocked = enabled // locked on a cold launch when the feature is on
    }

    var biometry: BiometryKind { auth.availableBiometry() }
    /// Whether the device is capable of the lock at all (a biometric or a passcode).
    var isAvailable: Bool { auth.canAuthenticate() }

    /// Turn the lock on. Confirms she can authenticate first, so she both consents and
    /// isn't locked out. Returns whether it was enabled.
    @discardableResult
    func enable() async -> Bool {
        guard auth.canAuthenticate() else { return false }
        let ok = await auth.authenticate(reason: "Turn on the app lock for Keel")
        if ok {
            setEnabled(true)
            isLocked = false
        }
        return ok
    }

    /// Turn the lock off. She is already inside the app (authenticated to get here), so
    /// no re-auth is required.
    func disable() {
        setEnabled(false)
        isLocked = false
    }

    /// Lock now if the feature is on (call when the app goes to the background).
    func lockIfEnabled() {
        if isEnabled { isLocked = true }
    }

    /// Attempt to unlock from the gate. Returns whether it succeeded.
    @discardableResult
    func unlock() async -> Bool {
        guard isLocked else { return true }
        let ok = await auth.authenticate(reason: "Unlock Keel")
        if ok { isLocked = false }
        return ok
    }

    private func setEnabled(_ on: Bool) {
        isEnabled = on
        defaults.set(on, forKey: enabledKey)
    }

    #if DEBUG
    /// Force the locked state on (in-memory, not persisted) so the gate can be
    /// screenshotted on the simulator, where real biometric auth can't complete.
    func debugForceLocked() { isEnabled = true; isLocked = true }
    #endif
}
