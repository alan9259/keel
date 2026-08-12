import SwiftUI
import SwiftData

struct SettingsView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @AppStorage("keel.hasOnboarded") private var hasOnboarded = false

    @State private var showCloseDialog = false
    @State private var typed = ""

    // TODO: point this at the real published privacy policy before launch.
    private let privacyPolicyURL = URL(string: "https://therecalibrationyears.com/privacy")

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                ScreenHeader(title: "Settings", titleSize: 28) { dismiss() }

                group("Notifications") {
                    toggleRow("bell.fill", "Push notifications", "Reminders and alerts",
                              Binding(get: { env.settings.pushNotifications }, set: { env.setPushNotificationsEnabled($0) }))
                    Divider().background(theme.border)
                    toggleRow("iphone.radiowaves.left.and.right", "Haptic feedback", "Vibration on interactions",
                              Binding(get: { env.settings.haptics }, set: { env.settings.haptics = $0 }))
                }

                group("Privacy") {
                    if let url = privacyPolicyURL {
                        Button { openURL(url) } label: { linkRow("globe", "Privacy policy") }
                            .buttonStyle(.plain)
                    }
                }

                accountGroup
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
        .overlay { if showCloseDialog { closeDialog } }
    }

    private func group<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title.uppercased()).font(KeelFont.eyebrow).tracking(1).foregroundStyle(theme.muted).padding(.leading, 4)
            VStack(spacing: 0) { content() }
                .background(theme.card)
                .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
        }
    }

    private func toggleRow(_ symbol: String, _ title: String, _ subtitle: String, _ isOn: Binding<Bool>) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).font(.system(size: 16)).foregroundStyle(theme.accent).frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(KeelFont.body).foregroundStyle(theme.text)
                Text(subtitle).font(KeelFont.caption).foregroundStyle(theme.muted)
            }
            Spacer()
            Toggle("", isOn: isOn).labelsHidden().tint(theme.accent)
        }.padding(14)
    }

    private func linkRow(_ symbol: String, _ title: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).font(.system(size: 16)).foregroundStyle(theme.accent).frame(width: 24)
            Text(title).font(KeelFont.body).foregroundStyle(theme.text)
            Spacer()
            Image(systemName: "chevron.right").font(.system(size: 14, weight: .semibold)).foregroundStyle(theme.muted)
        }.padding(14)
    }

    private var accountGroup: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("ACCOUNT").font(KeelFont.eyebrow).tracking(1).foregroundStyle(theme.muted).padding(.leading, 4)
            Button { showCloseDialog = true; typed = "" } label: {
                HStack(spacing: 14) {
                    Image(systemName: "person.crop.circle.badge.xmark").font(.system(size: 17))
                        .foregroundStyle(Color(hex: 0xA9762F)).frame(width: 40, height: 40)
                        .background(Color(hex: 0xA9762F).opacity(0.1)).clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Close account").font(KeelFont.body).foregroundStyle(Color(hex: 0xA9762F))
                        Text("Permanently delete your account and all data").font(KeelFont.caption).foregroundStyle(theme.muted)
                    }
                    Spacer()
                    Image(systemName: "chevron.right").font(.system(size: 14, weight: .semibold)).foregroundStyle(theme.muted)
                }.padding(14)
            }
            .buttonStyle(.plain)
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(Color(hex: 0xA9762F).opacity(0.18), lineWidth: 1))
        }
    }

    private var closeDialog: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.4).ignoresSafeArea().onTapGesture { showCloseDialog = false }
            VStack(alignment: .leading, spacing: 18) {
                HStack(alignment: .top, spacing: 14) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 20)).foregroundStyle(Color(hex: 0xA9762F))
                        .frame(width: 44, height: 44)
                        .background(Color(hex: 0xA9762F).opacity(0.1)).clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Close your account?").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.text)
                        Text("This permanently deletes your account and all your data. This can't be undone.")
                            .font(KeelFont.caption).foregroundStyle(theme.muted).fixedSize(horizontal: false, vertical: true)
                    }
                }
                VStack(alignment: .leading, spacing: 6) {
                    Text("\(Text("Type "))\(Text("DELETE").font(.system(.footnote, design: .monospaced).weight(.semibold)))\(Text(" to confirm"))")
                        .font(KeelFont.caption).foregroundStyle(theme.muted)
                    TextField("DELETE", text: $typed)
                        .font(.system(.body, design: .monospaced)).foregroundStyle(theme.text)
                        .autocorrectionDisabled().textInputAutocapitalization(.characters)
                        .padding(.horizontal, 14).padding(.vertical, 11)
                        .background(theme.inputBackground).clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                HStack(spacing: 12) {
                    Button { showCloseDialog = false } label: {
                        Text("Cancel").font(KeelFont.body).foregroundStyle(theme.text)
                            .frame(maxWidth: .infinity).padding(.vertical, 13)
                            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(theme.border, lineWidth: 1))
                    }.buttonStyle(.plain)
                    Button(action: closeAccount) {
                        Text("Delete").font(KeelFont.body).foregroundStyle(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 13)
                            .background(Color(hex: 0xA9762F)).clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .disabled(typed != "DELETE").opacity(typed == "DELETE" ? 1 : 0.5)
                }
            }
            .padding(22)
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .padding(16)
        }
    }

    private func closeAccount() {
        guard typed == "DELETE" else { return }
        let ctx = env.context
        try? ctx.delete(model: UserProfile.self)
        try? ctx.delete(model: CheckIn.self)
        try? ctx.delete(model: Symptom.self)
        try? ctx.delete(model: CheckInSymptom.self)
        try? ctx.delete(model: CycleEntry.self)
        try? ctx.delete(model: Medication.self)
        try? ctx.delete(model: MedicationLog.self)
        try? ctx.delete(model: Insight.self)
        try? ctx.delete(model: ChatMessage.self)
        try? ctx.delete(model: ActivityLog.self)
        try? ctx.delete(model: DailySummary.self)
        try? ctx.delete(model: HealthSample.self)
        try? ctx.save()
        // The built-in symptoms are reference data, not personal data. Re-seed them
        // so the next sign-up has its default check-in symptoms (bootstrap only
        // seeds at launch, and closing the account returns to onboarding in place).
        env.symptoms.syncBuiltIns()
        env.auth.signOut()
        hasOnboarded = false
    }
}
