import SwiftUI

/// Decides between onboarding and the main app.
struct RootView: View {
    @Environment(AppEnvironment.self) private var env

    var body: some View {
        Group {
            // A returning user (identity + onboarding restored from the Keychain,
            // e.g. after a reinstall) skips straight past onboarding.
            if (env.auth.hasCompletedOnboarding || debugForcedOnboarded) && !debugForceOnboarding {
                MainView()
            } else {
                OnboardingFlow {
                    env.auth.markOnboarded()
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
}
