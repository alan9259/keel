import SwiftUI

/// Full-screen cover shown while the app is locked. Auto-prompts for Face ID / Touch
/// ID (with the device passcode as fallback) on appear, and offers a manual Unlock
/// button if she cancels. Nothing behind it is visible until she authenticates.
struct LockGate: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme

    @State private var authenticating = false

    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()
            VStack(spacing: Spacing.lg) {
                Image(systemName: env.lock.biometry.symbol)
                    .font(.system(size: 46))
                    .foregroundStyle(theme.accent)
                VStack(spacing: Spacing.sm) {
                    Text("Keel is locked")
                        .font(KeelFont.serif(24, weight: .semibold))
                        .foregroundStyle(theme.heading)
                    Text("Unlock with \(env.lock.biometry.label) to see your check-ins.")
                        .font(KeelFont.body)
                        .foregroundStyle(theme.muted)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                KeelPrimaryButton("Unlock", isEnabled: !authenticating) {
                    Task { await attempt() }
                }
                .padding(.top, Spacing.sm)
            }
            .padding(.horizontal, Spacing.screenH)
            .frame(maxWidth: 360)
        }
        .task { await attempt() }
    }

    private func attempt() async {
        #if DEBUG
        if DebugHarness.forceLocked { return } // keep the gate up for screenshots
        #endif
        guard env.lock.isLocked, !authenticating else { return }
        authenticating = true
        _ = await env.lock.unlock()
        authenticating = false
    }
}
