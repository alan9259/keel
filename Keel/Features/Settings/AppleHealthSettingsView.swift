import SwiftUI

struct AppleHealthSettingsView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var connected = false
    @State private var connecting = false

    /// What Keel reads from Health. Read-only, and matched to the set requested in
    /// `HealthKitService`.
    private let categories: [(id: String, label: String, desc: String)] = [
        ("sleep", "Sleep", "Hours actually asleep each night"),
        ("activity", "Activity & steps", "Steps, exercise minutes and active energy"),
        ("heart", "Heart & vitals", "Heart rate, resting HR, HRV, respiratory rate, blood oxygen"),
        ("temperature", "Body temperature", "Basal, wrist and body temperature"),
        ("weight", "Body weight", "Weight over time"),
        ("cycle", "Cycle & flow", "Period days and menstrual flow"),
        ("symptoms", "Symptoms", "Hot flushes, night sweats, mood changes, fatigue and more"),
        ("mindful", "Mindful minutes", "Meditation and breathwork"),
    ]

    private let why: [(String, String)] = [
        ("Less to log", "Sleep, activity, cycle and symptoms flow in automatically."),
        ("Richer patterns", "More signals means clearer connections over time."),
        ("Read-only", "Keel reads from Health. It never writes anything back."),
        ("Private by design", "Data stays on your device and in your Apple account."),
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                ScreenHeader(title: "Apple Health", titleSize: 28,
                             subtitle: "Let Keel and Health work together") { dismiss() }

                connectionCard

                if connected {
                    permissionsSection
                } else {
                    connectButton
                    whySection
                }
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
        .onAppear {
            connected = env.users.currentProfile()?.healthKitAuthorized ?? false
        }
    }

    private var connectionCard: some View {
        VStack(spacing: 16) {
            HStack(spacing: 14) {
                healthIcon
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text("Apple Health").font(KeelFont.bodyLarge).foregroundStyle(theme.text)
                        if connected {
                            Text("Connected").font(KeelFont.sans(11)).foregroundStyle(Color(hex: 0x15803D))
                                .padding(.horizontal, 8).padding(.vertical, 2)
                                .background(Color(hex: 0x16A34A).opacity(0.12)).clipShape(Capsule())
                        }
                    }
                    Text("Keel reads from Health to save you logging.").font(KeelFont.caption).foregroundStyle(theme.muted)
                }
                Spacer(minLength: 0)
            }
            if connected {
                HStack(spacing: 10) {
                    outlineButton("Sync now", tint: theme.sage) { env.syncHealthData(); Haptics.success() }
                    outlineButton("Disconnect", tint: Color(hex: 0xDC2626)) { disconnect() }
                }
            } else if connecting {
                Text("Connecting…").font(KeelFont.button).foregroundStyle(theme.background)
                    .frame(maxWidth: .infinity).padding(.vertical, 13)
                    .background(theme.accent.opacity(0.7)).clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
        }
        .padding(18)
        .background(theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
    }

    private var healthIcon: some View {
        Image(systemName: "heart.fill")
            .font(.system(size: 26))
            .foregroundStyle(.white)
            .frame(width: 54, height: 54)
            .background(LinearGradient(colors: [Color(hex: 0xFF6B6B), Color(hex: 0xE91E63)], startPoint: .topLeading, endPoint: .bottomTrailing))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var permissionsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Text("What Keel reads").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
                Spacer()
                Text("Read-only").font(KeelFont.caption).foregroundStyle(theme.muted)
            }
            VStack(spacing: 0) {
                ForEach(categories, id: \.id) { cat in
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(cat.label).font(KeelFont.body).foregroundStyle(theme.text)
                            Text(cat.desc).font(KeelFont.caption).foregroundStyle(theme.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 8)
                        Image(systemName: "checkmark").font(.system(size: 12, weight: .bold))
                            .foregroundStyle(theme.background)
                            .frame(width: 22, height: 22)
                            .background(theme.sage).clipShape(Circle())
                    }
                    .padding(14)
                    if cat.id != categories.last?.id { Divider().background(theme.border) }
                }
            }
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))

            Text("You choose exactly what to share when you connect, and can change it any time in the Health app under Sharing. Keel only ever reads what you allow, and never writes back.")
                .font(KeelFont.caption).foregroundStyle(theme.muted)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 4).padding(.top, 2)
        }
    }

    private var connectButton: some View {
        KeelPrimaryButton("Connect with Apple Health") { connect() }
    }

    private var whySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Why connect?").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
            ForEach(why, id: \.0) { item in
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "heart.fill").font(.system(size: 14)).foregroundStyle(theme.accent).padding(.top, 2)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(item.0).font(KeelFont.body).foregroundStyle(theme.text)
                        Text(item.1).font(KeelFont.caption).foregroundStyle(theme.muted)
                    }
                    Spacer(minLength: 0)
                }
                .padding(14)
                .background(theme.card)
                .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Radius.md, style: .continuous).stroke(theme.border, lineWidth: 1))
            }
        }
    }

    private func outlineButton(_ title: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(KeelFont.sans(13, weight: .medium)).foregroundStyle(tint)
                .frame(maxWidth: .infinity).padding(.vertical, 11)
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(tint.opacity(0.35), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private func connect() {
        connecting = true
        Task {
            let granted = await env.health.requestAuthorization()
            #if targetEnvironment(simulator)
            // HealthKit is unavailable on the unsigned Simulator, so reflect intent
            // there for the demo. On device we honour the actual result.
            let effective = true
            #else
            let effective = granted
            #endif
            env.users.setHealthKitAuthorized(effective)
            if effective { env.syncHealthData() }
            connecting = false
            connected = effective
            Haptics.success()
        }
    }

    private func disconnect() {
        env.users.setHealthKitAuthorized(false)
        connected = false
        Haptics.light()
    }
}
