import SwiftUI

/// Her basic details, reachable from More: names, age, mobile, email, all optional and
/// stored on this phone. There are no accounts in V1 (no sign-in, no sync, no server),
/// so there is nothing to sign in to or upgrade (submission pack A4).
struct ProfileView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var firstName = ""
    @State private var lastName = ""
    @State private var age = ""
    @State private var mobile = ""
    @State private var email = ""
    @State private var justSaved = false

    private var hasAppleIdentity: Bool { env.auth.hasAppleIdentity }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                ScreenHeader(title: "Profile", titleSize: 28,
                             subtitle: "Your details, stored on this phone") { dismiss() }
                detailsSection
            }
            .padding(.horizontal, Spacing.screenH).padding(.vertical, Spacing.md)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
        .onAppear(perform: load)
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
        if let name = fn.nilIfEmpty { env.auth.updateName(name) }
        Haptics.success()
        withAnimation { justSaved = true }
        env.requestSync()
        Task { try? await Task.sleep(for: .seconds(2)); withAnimation { justSaved = false } }
    }
}
