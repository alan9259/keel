import Foundation

/// Identity for the app: a stable, Keychain-backed local `ownerID` stamped on every
/// record, plus her first name. V1 has no accounts and no sign-in (submission pack A4),
/// so nothing here talks to any server.
///
/// Builds before V1 offered Sign in with Apple; a stored Apple user id from those is
/// still read (it is her `ownerID`), so her existing records keep their owner.
@MainActor
@Observable
final class AuthService {
    private let ownerKey = "keel.ownerID"
    private let nameKey = "keel.displayName"
    private let appleIDKey = "keel.appleUserID"
    /// Where builds before 53 kept the onboarded flag. It survived deleting the app, so
    /// a reinstall skipped onboarding; it now lives on the profile and this is cleared.
    private let legacyOnboardedKey = "keel.hasOnboarded"
    /// Whether that legacy flag was set when this launch started (read before clearing),
    /// so `AppEnvironment` can carry an upgrading user's onboarding over to her profile.
    let hadLegacyOnboardedFlag: Bool

    private(set) var ownerID: String
    private(set) var displayName: String?
    private(set) var appleUserID: String?
    var isAuthenticated: Bool { !ownerID.isEmpty }
    /// True once identity came from a real Apple credential (vs. local fallback).
    var hasAppleIdentity: Bool { appleUserID != nil }

    init() {
        let defaults = UserDefaults.standard
        // Keychain first, so the id survives an app reinstall and an anonymous
        // user isn't lost; fall back to (and migrate from) UserDefaults.
        ownerID = Keychain.string(for: ownerKey) ?? defaults.string(forKey: ownerKey) ?? ""
        displayName = defaults.string(forKey: nameKey)
        appleUserID = Keychain.string(for: appleIDKey) ?? defaults.string(forKey: appleIDKey)
        hadLegacyOnboardedFlag = Keychain.string(for: legacyOnboardedKey) == "1"
            || defaults.bool(forKey: legacyOnboardedKey)
        defaults.removeObject(forKey: legacyOnboardedKey)
        Keychain.remove(legacyOnboardedKey)
    }

    /// Establish (or reuse) a stable local identity — Simulator / skip path.
    func continueLocally(name: String? = nil) {
        if ownerID.isEmpty {
            ownerID = "local-" + UUID().uuidString
        }
        if let name, !name.trimmingCharacters(in: .whitespaces).isEmpty {
            displayName = name
        }
        persist()
    }

    func updateName(_ name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        displayName = trimmed
        persist()
    }

    func signOut() {
        let defaults = UserDefaults.standard
        [ownerKey, nameKey, appleIDKey].forEach(defaults.removeObject(forKey:))
        [ownerKey, appleIDKey].forEach(Keychain.remove)
        ownerID = ""
        displayName = nil
        appleUserID = nil
    }

    private func persist() {
        let defaults = UserDefaults.standard
        defaults.set(ownerID, forKey: ownerKey)
        defaults.set(displayName, forKey: nameKey)
        defaults.set(appleUserID, forKey: appleIDKey)
        // Durable copy of the identity so it survives a reinstall.
        Keychain.set(ownerID, for: ownerKey)
        if let appleUserID { Keychain.set(appleUserID, for: appleIDKey) }
    }
}
