import SwiftUI

/// The App Lock screen (More > Account > App lock). Turn the lock on (set a PIN),
/// choose whether Face ID / Touch ID unlocks it, and change the PIN.
struct AppLockSettingsView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var showPINSetup = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                ScreenHeader(title: "App lock", titleSize: 28,
                             subtitle: "Keep your data private on this device") { dismiss() }

                group {
                    toggleRow(lockSymbol, "App lock", lockSubtitle,
                              Binding(get: { env.lock.isEnabled }, set: setEnabled))
                }

                if env.lock.isEnabled {
                    group {
                        if env.lock.biometricsAvailable {
                            toggleRow(env.lock.biometry.symbol, "Use \(env.lock.biometry.settingLabel)",
                                      "Unlock with \(env.lock.biometry.label) instead of typing your PIN",
                                      Binding(get: { env.lock.biometricUnlockEnabled },
                                              set: { env.lock.setBiometricUnlock($0); Haptics.selection() }))
                            Divider().background(theme.border)
                        }
                        Button { showPINSetup = true } label: { linkRow("number", "Change PIN") }
                            .buttonStyle(.plain)
                    }
                }

                Text(footnote)
                    .font(KeelFont.caption).foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true).padding(.horizontal, 4)
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
        .sheet(isPresented: $showPINSetup) {
            PINSetupView(onComplete: { showPINSetup = false }, onCancel: { showPINSetup = false })
        }
    }

    private var lockSymbol: String {
        env.lock.biometricsAvailable ? env.lock.biometry.symbol : "lock.fill"
    }
    private var lockSubtitle: String {
        env.lock.biometricsAvailable
            ? "Unlock with \(env.lock.biometry.label) or a PIN"
            : "Unlock with a 4-digit PIN"
    }
    private var footnote: String {
        let method = env.lock.biometricsAvailable ? env.lock.biometry.label : "biometrics"
        return "Keel asks to unlock when you open it, and after you've been away for a while. Your PIN is the backup if \(method) isn't used. Keep it somewhere safe: resetting a forgotten PIN means closing your account."
    }

    private func setEnabled(_ on: Bool) {
        Haptics.selection()
        if on { showPINSetup = true } else { env.lock.disable() }
    }

    // MARK: Rows (local styling, matching the settings screens)

    private func group<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(spacing: 0) { content() }
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
    }

    private func toggleRow(_ symbol: String, _ title: String, _ subtitle: String, _ isOn: Binding<Bool>) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).font(.system(size: 16)).foregroundStyle(theme.accent).frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(KeelFont.body).foregroundStyle(theme.text)
                Text(subtitle).font(KeelFont.caption).foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 8)
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
}
