import SwiftUI
import SwiftData

struct AppleHealthSettingsView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    // At most one live row each: enough to know whether anything has imported, without
    // loading a year of samples. Same rule as `AppEnvironment.hasImportedHealthData`.
    @Query(Self.anyImportedActivity) private var importedActivity: [HealthActivitySample]
    @Query(Self.anyImportedSample) private var samples: [HealthSample]
    private var hasImportedData: Bool { !importedActivity.isEmpty || !samples.isEmpty }

    private static var anyImportedActivity: FetchDescriptor<HealthActivitySample> {
        var d = FetchDescriptor<HealthActivitySample>(predicate: #Predicate { $0.deletedAt == nil })
        d.fetchLimit = 1
        return d
    }
    private static var anyImportedSample: FetchDescriptor<HealthSample> {
        var d = FetchDescriptor<HealthSample>(predicate: #Predicate { $0.deletedAt == nil })
        d.fetchLimit = 1
        return d
    }

    @State private var connected = false
    @State private var connecting = false
    @State private var showRemoveConfirm = false
    @State private var removeStatus: String?

    private let why: [(String, String)] = [
        ("Less to log", "Sleep and activity flow in automatically."),
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
                    if !hasImportedData { noDataNote }
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
            // Refresh on opening this screen, so a first import that came back empty
            // right after connecting fills in without her toggling items by hand.
            if connected { env.syncHealthData(force: true) }
        }
        .alert("Remove imported Apple Health data?", isPresented: $showRemoveConfirm) {
            Button("Cancel", role: .cancel) {}
            Button("Remove", role: .destructive) { removeImported() }
        } message: {
            Text("This deletes the sleep, activity and vitals imported from Apple Health on this device. It does not change Apple Health, and does not touch anything you entered in Keel yourself. Imported data returns on the next sync if you stay connected.")
        }
    }

    private func removeImported() {
        let removed = env.healthIngestor.purgeAllImportedHealthData()
        removeStatus = removed > 0 ? "Removed imported Apple Health data." : "No imported data to remove."
        Haptics.success()
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
                    outlineButton("Sync now", tint: theme.sage) { env.syncHealthData(force: true); Haptics.success() }
                    outlineButton("Disconnect", tint: Color(hex: 0xA9762F)) { disconnect() }
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
                Text("What to sync").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
                Spacer()
                Text("Read-only").font(KeelFont.caption).foregroundStyle(theme.muted)
            }
            VStack(spacing: 0) {
                ForEach(HealthSyncCatalog.all) { item in
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.label).font(KeelFont.body).foregroundStyle(theme.text)
                            Text(item.desc).font(KeelFont.caption).foregroundStyle(theme.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 8)
                        Toggle("", isOn: Binding(
                            get: { isSyncing(item) },
                            set: { setSyncing(item, $0) }
                        )).labelsHidden().tint(theme.accent)
                    }
                    .padding(14)
                    if item.id != HealthSyncCatalog.all.last?.id { Divider().background(theme.border) }
                }
            }
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))

            Text("Switch off anything you would rather Keel didn't import. Turning one off stops new data coming in, and keeps what was already imported until you remove it below. You can also change what Keel can read in the Health app: tap your profile picture, then Apps, then Keel. Keel only ever reads what you allow, and never writes back. Imported data is kept in a separate area on your device and never leaves it.")
                .font(KeelFont.caption).foregroundStyle(theme.muted)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 4).padding(.top, 2)

            Button { showRemoveConfirm = true } label: {
                Text("Remove imported Apple Health data")
                    .font(KeelFont.sans(13, weight: .medium)).foregroundStyle(Color(hex: 0xA9762F))
                    .frame(maxWidth: .infinity).padding(.vertical, 11)
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Color(hex: 0xA9762F).opacity(0.35), lineWidth: 1))
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain).padding(.top, 6)
            if let removeStatus {
                Text(removeStatus).font(KeelFont.caption).foregroundStyle(theme.muted).padding(.horizontal, 4)
            }
        }
    }

    /// Shown when she's connected but nothing has imported — likely read access wasn't
    /// granted in the Health app (iOS never tells us, so we infer it from the empty store).
    private var noDataNote: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "info.circle.fill").font(.system(size: 15)).foregroundStyle(theme.attention).padding(.top, 1)
                Text("Connected, but no Apple Health data has come in yet. If you expected some, open the Health app, tap your profile picture, then Apps, then Keel, and check what Keel is allowed to read.")
                    .font(KeelFont.caption).foregroundStyle(theme.text)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Button {
                if let url = URL(string: "x-apple-health://") { openURL(url) }
            } label: {
                Text("Open the Health app")
                    .font(KeelFont.sans(13, weight: .medium)).foregroundStyle(theme.accent)
                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(theme.accent.opacity(0.4), lineWidth: 1))
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
        }
        .padding(14)
        .background(theme.attention.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.attention.opacity(0.25), lineWidth: 1))
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
                .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func isSyncing(_ item: HealthSyncCatalog.Item) -> Bool {
        !env.settings.disabledHealthItemIDs.contains(item.id)
    }

    /// Turn one item's import on or off. Off stops future syncing but leaves what's
    /// already imported (she can clear it all with "Remove imported Apple Health data").
    private func setSyncing(_ item: HealthSyncCatalog.Item, _ on: Bool) {
        if on {
            env.settings.disabledHealthItemIDs.remove(item.id)
            env.syncHealthData(force: true) // pull it in now
        } else {
            env.settings.disabledHealthItemIDs.insert(item.id)
        }
        Haptics.selection()
    }

    private func connect() {
        connecting = true
        Task {
            connected = await env.connectAppleHealth()
            connecting = false
            Haptics.success()
        }
    }

    private func disconnect() {
        env.users.setHealthKitAuthorized(false)
        connected = false
        Haptics.light()
    }
}
