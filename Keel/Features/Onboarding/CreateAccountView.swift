import SwiftUI

/// Onboarding: what should Keel call her. First name only, optional, stored on this
/// phone. V1 has no accounts (no sign-in, no sync, no server), so there is no Sign in
/// with Apple and no email here (submission pack A4).
struct CreateAccountView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    let onContinue: () -> Void

    @State private var name = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Spacer().frame(height: Spacing.xl)

                Text("Let's set you up")
                    .onboardingTitle(.leading)

                Text("Just your first name, if you'd like. It's stored on this phone, and you can change it any time in Profile.")
                    .onboardingSubtitle(.leading).foregroundStyle(theme.muted)
                    .padding(.bottom, Spacing.sm)

                KeelTextField(label: "First name (optional)", placeholder: "What should we call you?",
                              text: $name, textContentType: .givenName, autocapitalization: .words)
                    .onSubmit(finish)

                KeelPrimaryButton("Continue", action: finish)
                    .padding(.top, Spacing.xs)

                Text("We never sell your data. Ever.")
                    .font(KeelFont.caption).foregroundStyle(theme.muted)
                    .frame(maxWidth: .infinity)
                    .padding(.top, Spacing.xs)
            }
            .padding(.horizontal, Spacing.screenH)
        }
        .clipped() // scrolled content stays out from behind the status bar
        .frame(maxWidth: .infinity)
        .background(theme.background.ignoresSafeArea())
    }

    /// Establish a stable, Keychain-backed local identity (so her records have an owner
    /// and persist) and keep her first name, if she gave one. Nothing else is asked.
    private func finish() {
        let resolved = name.trimmingCharacters(in: .whitespaces)
        env.auth.continueLocally(name: resolved.isEmpty ? nil : resolved)
        env.users.upsertProfile(firstName: resolved.isEmpty ? "there" : resolved,
                                email: nil, appleUserID: env.auth.appleUserID)
        onContinue()
    }
}
