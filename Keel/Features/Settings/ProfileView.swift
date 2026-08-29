import SwiftUI
import AuthenticationServices
import OSLog

/// Her account and basic details, reachable from More. Two jobs:
///  1. If she skipped account creation in onboarding (a local, on-device identity),
///     offer the same upgrade path as onboarding: Sign in with Apple. Her existing
///     data is re-stamped to the new account, so nothing is orphaned.
///  2. Let her add or change basic details (names, age, mobile, email).
///
/// When she's signed in with Apple we follow Apple's guidance: the Apple identity is
/// shown as a status and never re-collected (Apple hands name/email over only on the
/// first sign-in), and we ask only for editable contact details.
struct ProfileView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var firstName = ""
    @State private var lastName = ""
    @State private var age = ""
    @State private var mobile = ""
    @State private var email = ""
    @State private var periodsNotApplicable = ""
    @State private var justSaved = false
    /// Non-nil shows the sign-in error alert (a real failure, not a cancellation).
    @State private var authErrorMessage: String?

    private static let log = Logger(subsystem: "com.keel", category: "auth")

    private var hasAppleIdentity: Bool { env.auth.hasAppleIdentity }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                ScreenHeader(title: "Profile", titleSize: 28,
                             subtitle: "Your account and details") { dismiss() }
                accountCard
                detailsSection
            }
            .padding(.horizontal, Spacing.screenH).padding(.vertical, Spacing.md)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
        .onAppear(perform: load)
        .alert("Sign-in didn't complete", isPresented: Binding(
            get: { authErrorMessage != nil }, set: { if !$0 { authErrorMessage = nil } }
        )) {
            Button("OK", role: .cancel) { authErrorMessage = nil }
        } message: {
            Text(authErrorMessage ?? "")
        }
    }

    // MARK: Account status / upgrade

    @ViewBuilder
    private var accountCard: some View {
        if hasAppleIdentity {
            card {
                HStack(spacing: 14) {
                    Image(systemName: "apple.logo").font(.system(size: 18)).foregroundStyle(theme.heading)
                        .frame(width: 40, height: 40).background(theme.inputBackground)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Signed in with Apple").font(KeelFont.bodyLarge).foregroundStyle(theme.text)
                        Text("Your data is tied to your account and can sync across your devices.")
                            .font(KeelFont.caption).foregroundStyle(theme.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                }
            }
        } else {
            card {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Create an account").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
                    Text("You're using Keel on this device. Create an account to keep your data safe and sync it across your devices. Everything you've logged so far comes with you.")
                        .font(KeelFont.body).foregroundStyle(theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                    SignInWithAppleButton(.signUp) { request in
                        env.auth.configureRequest(request)
                    } onCompletion: { result in
                        handleApple(result)
                    }
                    .signInWithAppleButtonStyle(.black)
                    .frame(height: 52)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.input, style: .continuous))
                    Text("We never sell your data. Ever.")
                        .font(KeelFont.caption).foregroundStyle(theme.muted)
                }
            }
        }
    }

    // MARK: Editable details

    private var detailsSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Your details").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)

            KeelTextField(label: "First name", placeholder: "What should we call you?",
                          text: $firstName, textContentType: .givenName, autocapitalization: .words)
            KeelTextField(label: "Last name (optional)", placeholder: "",
                          text: $lastName, textContentType: .familyName, autocapitalization: .words)
            KeelTextField(label: "Age (optional)", placeholder: "e.g. 48",
                          text: $age, keyboard: .numberPad)
            KeelTextField(label: "Mobile (optional)", placeholder: "For your records",
                          text: $mobile, keyboard: .phonePad, textContentType: .telephoneNumber)
            KeelTextField(label: emailLabel, placeholder: "you@example.com",
                          text: $email, keyboard: .emailAddress, textContentType: .emailAddress)
            if hasAppleIdentity {
                Text("Apple shares this email with Keel. You can replace it with another contact email.")
                    .font(KeelFont.caption).foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }

            KeelTextField(label: "Periods no longer apply (optional)",
                          placeholder: "e.g. after menopause or a hysterectomy",
                          text: $periodsNotApplicable, autocapitalization: .sentences)
            Text("If you fill this in, your GP visit summary notes it instead of period details.")
                .font(KeelFont.caption).foregroundStyle(theme.muted)
                .fixedSize(horizontal: false, vertical: true)

            KeelPrimaryButton("Save changes", action: save).padding(.top, Spacing.xs)
            if justSaved {
                Label("Changes saved", systemImage: "checkmark.circle.fill")
                    .font(KeelFont.caption).foregroundStyle(theme.sage)
                    .frame(maxWidth: .infinity)
                    .transition(.opacity)
            }
        }
    }

    private var emailLabel: String { hasAppleIdentity ? "Contact email" : "Email (optional)" }

    // MARK: Card chrome

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        content()
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
    }

    // MARK: State

    private func load() {
        guard let profile = env.users.currentProfile() else {
            firstName = env.auth.displayName ?? ""
            return
        }
        // "there" is the anonymous placeholder from a skipped sign-up: show it as blank
        // so she isn't greeted by the placeholder in her own name field.
        firstName = profile.firstName == "there" ? "" : profile.firstName
        lastName = profile.lastName ?? ""
        age = profile.age.map(String.init) ?? ""
        mobile = profile.mobile ?? ""
        email = profile.email ?? ""
        periodsNotApplicable = profile.periodsNotApplicableReason ?? ""
    }

    private func save() {
        let fn = firstName.trimmingCharacters(in: .whitespaces)
        let parsedAge = Int(age.trimmingCharacters(in: .whitespaces))
        // Store year of birth (from age) so the value never drifts; ignore nonsense.
        let birthYear = parsedAge.flatMap { $0 > 0 && $0 < 130 ? UserProfile.birthYear(fromAge: $0) : nil }
        env.users.updateBasicInfo(
            firstName: fn,
            lastName: lastName.trimmingCharacters(in: .whitespaces).nilIfEmpty,
            birthYear: birthYear,
            mobile: mobile.trimmingCharacters(in: .whitespaces).nilIfEmpty,
            email: email.trimmingCharacters(in: .whitespaces).nilIfEmpty)
        env.users.setPeriodsNotApplicableReason(periodsNotApplicable)
        if let name = fn.nilIfEmpty { env.auth.updateName(name) }
        Haptics.success()
        withAnimation { justSaved = true }
        env.requestSync()
        Task { try? await Task.sleep(for: .seconds(2)); withAnimation { justSaved = false } }
    }

    // MARK: Sign in with Apple (upgrade a local identity to a real account)

    private func handleApple(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .success(let authorization):
            let previousOwner = env.auth.ownerID
            env.auth.handleAuthorization(authorization)
            let newOwner = env.auth.ownerID
            // Upgrading a "continue on this device" identity: carry her existing data
            // over to the new account so nothing she has logged is orphaned.
            if previousOwner != newOwner, previousOwner.hasPrefix("local-") {
                let moved = env.reassignOwnership(from: previousOwner, to: newOwner)
                Self.log.info("Upgraded local identity to Apple; re-stamped \(moved, privacy: .public) rows.")
            }
            let appleEmail = (authorization.credential as? ASAuthorizationAppleIDCredential)?.email
            env.users.upsertProfile(
                firstName: env.auth.displayName ?? firstName.nilIfEmpty ?? "there",
                email: appleEmail,
                appleUserID: env.auth.appleUserID)
            load()
            Haptics.success()
            env.requestSync()
        case .failure(let error):
            // Backing out of the sheet isn't an error worth interrupting her with.
            if let authError = error as? ASAuthorizationError, authError.code == .canceled { return }
            Self.log.error("Sign in with Apple failed: \(error.localizedDescription, privacy: .public)")
            authErrorMessage = "We couldn't complete Sign in with Apple. You can try again anytime, or keep using Keel on this device."
        }
    }
}
