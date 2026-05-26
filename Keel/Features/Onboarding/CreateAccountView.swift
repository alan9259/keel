import SwiftUI
import AuthenticationServices
import OSLog

struct CreateAccountView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    let onContinue: () -> Void

    @State private var name = ""
    @State private var email = ""
    /// Non-nil shows the sign-in error alert (a real failure, not a cancellation).
    @State private var authErrorMessage: String?

    private static let log = Logger(subsystem: "com.keel", category: "auth")

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Spacer().frame(height: Spacing.xl)

                Text("Let's set you up")
                    .onboardingTitle(.leading)

                Text("Sign in to keep your data across devices, or just use Keel on this one. Your call.")
                    .onboardingSubtitle(.leading).foregroundStyle(theme.muted)
                    .padding(.bottom, Spacing.sm)

                // The real login: a stable identity that carries across devices once
                // the backend is live. Needs the Sign in with Apple capability + a
                // signed build to work (it won't complete in the Simulator).
                SignInWithAppleButton(.signIn) { request in
                    env.auth.configureRequest(request)
                } onCompletion: { result in
                    handleApple(result)
                }
                .signInWithAppleButtonStyle(.black)
                .frame(height: 52)
                .clipShape(RoundedRectangle(cornerRadius: Radius.input, style: .continuous))
                // A "Sign in with Google" button will sit here once its OAuth client
                // is configured (GoogleSignIn SDK).

                dividerOr

                KeelTextField(label: "First Name", placeholder: "What should we call you?",
                              text: $name, textContentType: .givenName, autocapitalization: .words)
                KeelTextField(label: "Email (optional)", placeholder: "you@example.com", text: $email,
                              keyboard: .emailAddress, textContentType: .emailAddress)

                KeelPrimaryButton("Save & continue", action: finish)
                    .padding(.top, Spacing.xs)

                Button(action: skipSignUp) {
                    Text("Continue without an account")
                        .font(KeelFont.body).foregroundStyle(theme.accent)
                }
                .frame(maxWidth: .infinity)
                .padding(.top, Spacing.xs)
                .accessibilityHint("Skip sign-up. Nothing personal is collected.")

                Text("We never sell your data. Ever.")
                    .font(KeelFont.caption).foregroundStyle(theme.muted)
                    .frame(maxWidth: .infinity)
                    .padding(.top, Spacing.xs)
            }
            .padding(.horizontal, Spacing.screenH)
        }
        .frame(maxWidth: .infinity)
        .background(theme.background.ignoresSafeArea())
        .alert("Sign-in didn't complete", isPresented: Binding(
            get: { authErrorMessage != nil },
            set: { if !$0 { authErrorMessage = nil } }
        )) {
            Button("OK", role: .cancel) { authErrorMessage = nil }
        } message: {
            Text(authErrorMessage ?? "")
        }
    }

    private var dividerOr: some View {
        HStack(spacing: 12) {
            Rectangle().fill(theme.border).frame(height: 1)
            Text("or use this device").font(KeelFont.sans(12)).foregroundStyle(theme.muted)
            Rectangle().fill(theme.border).frame(height: 1)
        }
        .padding(.vertical, Spacing.xs)
    }

    // MARK: Actions

    /// A real Apple credential: stamp the stable Apple id as the owner, and keep
    /// the name/email Apple hands back (only on first sign-in).
    private func handleApple(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .success(let authorization):
            env.auth.handleAuthorization(authorization)
            let appleEmail = (authorization.credential as? ASAuthorizationAppleIDCredential)?.email
            env.users.upsertProfile(
                firstName: env.auth.displayName ?? "there",
                email: appleEmail,
                appleUserID: env.auth.appleUserID
            )
            Self.log.info("Sign in with Apple succeeded (appleUserID set: \(env.auth.appleUserID != nil, privacy: .public)).")
            // Advance on the next runloop tick so the step change isn't swallowed
            // by the Apple sheet's dismissal transition (a dropped @State update
            // would leave her stuck on this screen even though sign-in worked).
            DispatchQueue.main.async { onContinue() }
        case .failure(let error):
            // Backing out of the sheet isn't an error worth interrupting her with.
            if let authError = error as? ASAuthorizationError, authError.code == .canceled {
                Self.log.info("Sign in with Apple cancelled by user.")
                return
            }
            // Anything else: surface it (so she isn't stuck with no feedback) and
            // log the exact reason so the cause is diagnosable from the device logs.
            Self.log.error("Sign in with Apple failed: \(error.localizedDescription, privacy: .public) — \(String(describing: error), privacy: .public)")
            authErrorMessage = "We couldn't complete Sign in with Apple. You can try again, or continue without an account below."
        }
    }

    /// The on-device path: save whatever optional details she typed, under a
    /// stable local identity. Blank fields are fine.
    private func finish() {
        let resolved = name.trimmingCharacters(in: .whitespaces)
        env.auth.continueLocally(name: resolved)
        env.users.upsertProfile(
            firstName: resolved.isEmpty ? "there" : resolved,
            email: email.trimmingCharacters(in: .whitespaces).nilIfEmpty,
            appleUserID: env.auth.appleUserID
        )
        onContinue()
    }

    /// She chose not to sign up. We ask for nothing: just establish a stable,
    /// anonymous, Keychain-backed identity so her data still has an owner and
    /// persists. Non-identifying context is recorded inside `upsertProfile`.
    private func skipSignUp() {
        env.auth.continueLocally()
        env.users.upsertProfile(firstName: "there", email: nil, appleUserID: nil)
        onContinue()
    }
}
