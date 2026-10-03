import XCTest
import SwiftData
@testable import Keel

/// "Delete all my data" (Settings): everything Keel holds on the phone goes, including
/// the three things the old Close account missed (reminders, the app lock PIN, and
/// her preferences).
@MainActor
final class EraseAllDataTests: XCTestCase {

    private func count<T: PersistentModel>(_ type: T.Type, _ env: AppEnvironment) -> Int {
        (try? env.context.fetchCount(FetchDescriptor<T>())) ?? -1
    }

    func testDeletesEverythingAndResetsTheRest() {
        let env = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
        env.users.upsertProfile(firstName: "Mischa", email: "m@example.com", appleUserID: nil)
        env.checkIns.create(mood: .low, energy: 30, notes: "rough", symptoms: [], date: .now)
        _ = env.medications.add(name: "Oestrogel", dosage: "2 pumps", timing: "Morning", method: nil)
        env.context.insert(HealthSample(typeID: "heartRate", day: .now, value: 70, unit: "bpm", ownerID: "o"))
        try? env.context.save()
        XCTAssertTrue(env.lock.setPIN("2468"))
        XCTAssertTrue(env.lock.isEnabled)
        env.settings.notesAlcohol = true
        env.settings.disabledHealthItemIDs = ["steps"]
        env.settings.enabledReminderIDs = ["hydration"]

        env.eraseAllData()

        XCTAssertEqual(count(UserProfile.self, env), 0)
        XCTAssertEqual(count(CheckIn.self, env), 0)
        XCTAssertEqual(count(Medication.self, env), 0)
        XCTAssertEqual(count(HealthSample.self, env), 0)
        XCTAssertGreaterThan(count(Symptom.self, env), 0)          // built-ins re-seeded for the next person

        // Regression: the app lock and its PIN survived "delete everything".
        XCTAssertFalse(env.lock.isEnabled)
        XCTAssertFalse(env.lock.verifyPIN("2468"))

        // Regression: her preferences survived too.
        XCTAssertFalse(env.settings.notesAlcohol)
        XCTAssertTrue(env.settings.disabledHealthItemIDs.isEmpty)
        XCTAssertEqual(env.settings.enabledReminderIDs, ["dailyCheckIn", "medication"])

        XCTAssertTrue(env.auth.ownerID.isEmpty)                     // local identity cleared
        XCTAssertFalse(env.hasCompletedOnboarding)
    }

    override func tearDown() {
        // Leave the shared test host's defaults/keychain as a fresh install would.
        AppLockService().disable()
        SettingsStore().resetToDefaults()
    }
}
