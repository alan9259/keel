import XCTest
@testable import Keel

/// iOS keeps an app's Keychain items when the app is deleted, so after a reinstall
/// Keel found the earlier install's old Sign in with Apple ID (reported on device).
/// A reinstall now clears them; an app update never does.
final class KeychainCleanupTests: XCTestCase {

    /// Regression: deleted and reinstalled, the Keychain still held the earlier
    /// install's items. No install marker and no profile means a reinstall: clear.
    func testReinstallClearsLeftovers() {
        XCTAssertTrue(AppEnvironment.isReinstallWithLeftovers(installSeen: false, hasProfile: false))
    }

    /// An update from a build without the marker keeps her profile, so nothing is
    /// cleared (her record ID and the legacy onboarded flag still carry over).
    func testAnUpdateIsNeverCleared() {
        XCTAssertFalse(AppEnvironment.isReinstallWithLeftovers(installSeen: false, hasProfile: true))
    }

    /// Any later launch of the same install is never cleared.
    func testLaterLaunchesAreNeverCleared() {
        XCTAssertFalse(AppEnvironment.isReinstallWithLeftovers(installSeen: true, hasProfile: false))
        XCTAssertFalse(AppEnvironment.isReinstallWithLeftovers(installSeen: true, hasProfile: true))
    }

    /// The marker lives in UserDefaults (deleted with the app), and Delete all my data
    /// doesn't reset it: after deleting her data she's on the same install.
    @MainActor
    func testTheMarkerIsSetAndSurvivesDeleteAllMyData() {
        let env = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
        XCTAssertTrue(UserDefaults.standard.bool(forKey: "keel.installSeen"))
        env.eraseAllData()
        XCTAssertTrue(UserDefaults.standard.bool(forKey: "keel.installSeen"))
        AppLockService().disable()
        SettingsStore().resetToDefaults()
    }
}
