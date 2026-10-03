import XCTest
import SwiftData
@testable import Keel

/// Whether she has onboarded now lives on her profile in the database, not in the
/// Keychain. The Keychain survives deleting the app, so a reinstall used to skip
/// onboarding (and with it the Apple Health and reminders steps). Deleting the app
/// deletes the database, so a reinstall now onboards again.
@MainActor
final class OnboardingStateTests: XCTestCase {

    private let legacyKey = "keel.hasOnboarded"

    private func makeEnv(_ container: ModelContainer) -> AppEnvironment {
        AppEnvironment(container: container, provider: NoopSyncProvider())
    }

    override func tearDown() {
        UserDefaults.standard.removeObject(forKey: legacyKey)
        AuthService().signOut()
    }

    /// Regression (reinstall): an empty database means onboarding, even when the old
    /// Keychain/UserDefaults flag from a previous install is still there.
    func testEmptyDatabaseIsNotOnboardedEvenWithTheLegacyFlag() {
        UserDefaults.standard.set(true, forKey: legacyKey)
        Keychain.set("1", for: legacyKey)

        let env = makeEnv(KeelSchema.makeContainer(inMemory: true))

        XCTAssertFalse(env.hasCompletedOnboarding)
        XCTAssertNil(UserDefaults.standard.object(forKey: legacyKey))   // legacy flag cleared
        XCTAssertNil(Keychain.string(for: legacyKey))
    }

    /// Finishing onboarding is recorded on the profile and read back on the next launch.
    func testFinishingOnboardingPersistsAcrossLaunches() {
        let container = KeelSchema.makeContainer(inMemory: true)
        let env = makeEnv(container)
        env.users.upsertProfile(firstName: "Mischa", email: nil, appleUserID: nil)

        env.markOnboarded()

        XCTAssertTrue(env.hasCompletedOnboarding)
        XCTAssertNotNil(env.users.currentProfile()?.onboardingCompletedAt)
        XCTAssertTrue(makeEnv(container).hasCompletedOnboarding)       // next launch
    }

    /// Quitting partway through (her name saved, flow not finished) means onboarding again.
    func testAProfileAloneIsNotOnboarded() {
        let container = KeelSchema.makeContainer(inMemory: true)
        makeEnv(container).users.upsertProfile(firstName: "Mischa", email: nil, appleUserID: nil)
        XCTAssertFalse(makeEnv(container).hasCompletedOnboarding)
    }

    /// Marking again (e.g. after a restore) keeps the original date.
    func testMarkingAgainKeepsTheFirstDate() {
        let env = makeEnv(KeelSchema.makeContainer(inMemory: true))
        let first = Date(timeIntervalSince1970: 1_700_000_000)
        env.users.markOnboardingCompleted(at: first)
        env.users.markOnboardingCompleted(at: first.addingTimeInterval(86_400))
        XCTAssertEqual(env.users.currentProfile()?.onboardingCompletedAt, first)
    }

    /// Without any profile yet, marking creates one rather than dropping the record.
    func testMarkingWithNoProfileCreatesOne() {
        let env = makeEnv(KeelSchema.makeContainer(inMemory: true))
        env.markOnboarded()
        XCTAssertNotNil(env.users.currentProfile()?.onboardingCompletedAt)
    }

    /// Delete all my data: back to onboarding, now and on the next launch.
    func testDeleteAllMyDataReturnsToOnboarding() {
        let container = KeelSchema.makeContainer(inMemory: true)
        let env = makeEnv(container)
        env.markOnboarded()

        env.eraseAllData()

        XCTAssertFalse(env.hasCompletedOnboarding)
        XCTAssertFalse(makeEnv(container).hasCompletedOnboarding)
    }

    /// Restoring a backup (from inside the app) keeps this phone's onboarded record,
    /// which isn't part of the archive.
    func testRestoreKeepsThisPhonesOnboardedRecord() throws {
        let source = makeEnv(KeelSchema.makeContainer(inMemory: true))
        source.users.upsertProfile(firstName: "Mischa", email: nil, appleUserID: nil)
        let data = try BackupService.exportData(context: source.context)

        let container = KeelSchema.makeContainer(inMemory: true)
        let device = makeEnv(container)
        let onboardedAt = Date(timeIntervalSince1970: 1_700_000_000)
        device.users.markOnboardingCompleted(at: onboardedAt)

        try BackupService.restore(data: data, into: device.context)

        XCTAssertEqual(device.users.currentProfile()?.firstName, "Mischa")
        XCTAssertEqual(device.users.currentProfile()?.onboardingCompletedAt, onboardedAt)
        XCTAssertTrue(makeEnv(container).hasCompletedOnboarding)
    }

    /// An archive with no profile would leave none; the restore screen re-marks her.
    func testRestoreOfAnArchiveWithoutAProfileStaysOnboarded() throws {
        let empty = makeEnv(KeelSchema.makeContainer(inMemory: true))   // kept alive: owns its container
        let data = try BackupService.exportData(context: empty.context)
        let container = KeelSchema.makeContainer(inMemory: true)
        let device = makeEnv(container)
        device.markOnboarded()

        try BackupService.restore(data: data, into: device.context)
        device.markOnboarded()   // what BackupRestoreView does after a restore

        XCTAssertTrue(makeEnv(container).hasCompletedOnboarding)
    }
}
