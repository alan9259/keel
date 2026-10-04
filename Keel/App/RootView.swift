import SwiftUI

/// Decides between onboarding and the main app.
struct RootView: View {
    @Environment(AppEnvironment.self) private var env
    /// She finished onboarding in this session. Kept separate from the durable
    /// `hasCompletedOnboarding` so that finishing the flow always enters the app,
    /// even under `-uitForceOnboarding` (which otherwise pins onboarding on).
    @State private var completedThisLaunch = false

    var body: some View {
        Group {
            if Self.shouldShowMain(completedThisLaunch: completedThisLaunch,
                                   hasOnboarded: env.hasCompletedOnboarding,
                                   forcedOnboarded: debugForcedOnboarded,
                                   forceOnboarding: debugForceOnboarding) {
                MainView()
            } else {
                OnboardingFlow {
                    env.markOnboarded()
                    completedThisLaunch = true
                    // Ask for notification permission now (on the way into the app),
                    // not on the welcome screen, and set up her default reminders.
                    env.completeOnboarding()
                    env.requestSync()
                }
            }
        }
        .dismissesKeyboardOnTapOutside()
        .task {
            env.bootstrap()
            #if DEBUG
            DebugHarness.apply(env: env)
            #endif
            env.requestSync()
        }
    }

    private var debugForcedOnboarded: Bool {
        #if DEBUG
        DebugHarness.forcedOnboarded
        #else
        false
        #endif
    }

    /// -uitForceOnboarding: show onboarding even for an already-onboarded sim (for
    /// screenshotting the flow without erasing the device).
    private var debugForceOnboarding: Bool {
        #if DEBUG
        DebugHarness.forceOnboarding
        #else
        false
        #endif
    }

    /// Pure routing decision: main app vs. onboarding.
    /// - completedThisLaunch: she just finished onboarding in this session; enters the app even under the force flag, while still onboarded.
    /// - hasOnboarded: recorded on her profile in the database; deleting the app clears it.
    /// - forcedOnboarded: `-uitOnboarded` debug flag — skip straight to the app for screenshots.
    /// - forceOnboarding: `-uitForceOnboarding` debug flag — pin onboarding on (until she finishes it).
    nonisolated static func shouldShowMain(completedThisLaunch: Bool,
                                           hasOnboarded: Bool,
                                           forcedOnboarded: Bool,
                                           forceOnboarding: Bool) -> Bool {
        // Finishing onboarding records it (`markOnboarded`) before setting
        // `completedThisLaunch`, so both are true then. If the record is gone again
        // ("Delete all my data" in the same launch), she goes back to onboarding.
        if completedThisLaunch && hasOnboarded { return true }
        if forceOnboarding { return false }
        return hasOnboarded || forcedOnboarded
    }
}
