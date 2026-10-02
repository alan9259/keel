import XCTest
@testable import Keel

/// 3D: a one-line explanation comes before the iOS notification prompt, instead of the
/// prompt firing as soon as she lands on Home after onboarding. Tests use the record step
/// only, so no real iOS prompt is raised inside the test host.
@MainActor
final class ReminderExplainerTests: XCTestCase {

    private func makeEnv() -> AppEnvironment {
        UserDefaults.standard.removeObject(forKey: "keel.notificationExplainerPending")
        return AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
    }

    func testFinishingOnboardingQueuesTheExplanationNotThePrompt() {
        let env = makeEnv()
        XCTAssertFalse(env.settings.notificationExplainerPending)
        env.completeOnboarding()
        XCTAssertTrue(env.settings.notificationExplainerPending)   // Home explains first
    }

    func testNotNowTurnsRemindersOffWithoutAsking() {
        let env = makeEnv()
        env.completeOnboarding()
        env.recordNotificationExplainerAnswer(allow: false)
        XCTAssertFalse(env.settings.notificationExplainerPending)  // never shown again
        XCTAssertFalse(env.settings.pushNotifications)             // she can turn them on in Settings
    }

    func testContinueLeavesRemindersOn() {
        let env = makeEnv()
        env.completeOnboarding()
        env.recordNotificationExplainerAnswer(allow: true)
        XCTAssertFalse(env.settings.notificationExplainerPending)
        XCTAssertTrue(env.settings.pushNotifications)
    }

    override func tearDown() {
        UserDefaults.standard.removeObject(forKey: "keel.notificationExplainerPending")
        UserDefaults.standard.set(true, forKey: "keel.pushNotifications")
    }
}
