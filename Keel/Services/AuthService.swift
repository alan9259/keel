import Foundation
import AuthenticationServices

/// Identity for the app. Sign in with Apple is the primary path — its stable
/// `user` identifier becomes `ownerID`, stamped on every record. That same id
/// maps cleanly to a Supabase `auth.uid()` later (Apple OIDC), so migrating
/// keeps every row's ownership intact.
///
/// A local fallback identity exists for the Simulator and a "skip account" path,
/// since Sign in with Apple needs entitlements + a signed build to function.
@MainActor
@Observable
final class AuthService {
    private let ownerKey = "keel.ownerID"
    private let nameKey = "keel.displayName"
    private let appleIDKey = "keel.appleUserID"
    private let onboardedKey = "keel.hasOnboarded"

    private(set) var ownerID: String
    private(set) var displayName: String?
    private(set) var appleUserID: String?
    /// True once she has finished onboarding. Persisted in the Keychain so a
    /// returning user (e.g. after a reinstall, where her identity is restored) is
    /// not made to onboard again.
    private(set) var hasCompletedOnboarding: Bool

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
        hasCompletedOnboarding = Keychain.string(for: onboardedKey) == "1" || defaults.bool(forKey: onboardedKey)
    }

    /// Record that onboarding is done, durably, so it survives a reinstall.
    func markOnboarded() {
        hasCompletedOnboarding = true
        UserDefaults.standard.set(true, forKey: onboardedKey)
        Keychain.set("1", for: onboardedKey)
    }

    /// Configure the Sign in with Apple request (called from `SignInWithAppleButton`).
    func configureRequest(_ request: ASAuthorizationAppleIDRequest) {
        request.requestedScopes = [.fullName, .email]
    }

    /// Handle a successful Sign in with Apple authorization.
    func handleAuthorization(_ authorization: ASAuthorization) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential else { return }
        appleUserID = credential.user
        ownerID = credential.user
        if let given = credential.fullName?.givenName, !given.isEmpty {
            displayName = given
        }
        persist()
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
        [ownerKey, nameKey, appleIDKey, onboardedKey].forEach(defaults.removeObject(forKey:))
        [ownerKey, appleIDKey, onboardedKey].forEach(Keychain.remove)
        ownerID = ""
        displayName = nil
        appleUserID = nil
        hasCompletedOnboarding = false
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
