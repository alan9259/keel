import XCTest
@testable import Keel

/// The onboarding-vs-app routing decision (`RootView.shouldShowMain`). Covers the
/// normal paths and the regression where finishing onboarding under the
/// `-uitForceOnboarding` screenshot flag failed to enter the app.
final class RootRoutingTests: XCTestCase {

    func testFreshUserSeesOnboarding() {
        XCTAssertFalse(RootView.shouldShowMain(
            completedThisLaunch: false, hasOnboarded: false, forcedOnboarded: false, forceOnboarding: false))
    }

    func testOnboardedUserSeesMainApp() {
        XCTAssertTrue(RootView.shouldShowMain(
            completedThisLaunch: false, hasOnboarded: true, forcedOnboarded: false, forceOnboarding: false))
    }

    func testForcedOnboardedSkipsToMainApp() {
        XCTAssertTrue(RootView.shouldShowMain(
            completedThisLaunch: false, hasOnboarded: false, forcedOnboarded: true, forceOnboarding: false))
    }

    func testForceOnboardingPinsOnboardingForAnOnboardedUser() {
        XCTAssertFalse(RootView.shouldShowMain(
            completedThisLaunch: false, hasOnboarded: true, forcedOnboarded: false, forceOnboarding: true))
    }

    /// Regression: tapping "Start with Keel" must reach the app even when
    /// `-uitForceOnboarding` is set (previously the guard trapped her on onboarding).
    func testCompletingOnboardingUnderForceFlagEntersApp() {
        XCTAssertTrue(RootView.shouldShowMain(
            completedThisLaunch: true, hasOnboarded: true, forcedOnboarded: false, forceOnboarding: true))
    }

    /// And completing it in a normal fresh session enters the app.
    func testCompletingOnboardingFreshEntersApp() {
        XCTAssertTrue(RootView.shouldShowMain(
            completedThisLaunch: true, hasOnboarded: false, forcedOnboarded: false, forceOnboarding: false))
    }
}
