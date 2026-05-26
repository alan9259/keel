import SwiftUI
import SwiftData
import UniformTypeIdentifiers

struct BackupRestoreView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @Query(filter: #Predicate<CheckIn> { $0.deletedAt == nil }) private var checkIns: [CheckIn]

    enum RestoreState: Equatable { case idle, confirm, done }
    @State private var restore: RestoreState = .idle
    @State private var pending: KeelBackup?
    @State private var summary: BackupService.Summary?
    @State private var exportURL: URL?
    @State private var showImporter = false
    @State private var errorMessage: String?

    // iCloud backup (real, via CloudKit private database).
    @State private var icloudAvailability: ICloudBackupService.Availability?
    @State private var icloudInfo: ICloudBackupService.Info?
    @State private var icloudBusy = false
    @State private var icloudStatus: String?
    @State private var confirmICloudRestore = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                ScreenHeader(title: "Backup & Restore", subtitle: "Your data, safely yours") { dismiss() }

                icloudSection
                localSection
                restoreSection
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
        .onAppear {
            refreshExport()
            loadICloud()
            #if DEBUG
            applyDebugStage()
            #endif
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json, .data]) { result in
            handleImport(result)
        }
        .alert("Couldn't complete", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "")
        }
        .alert("Restore from iCloud?", isPresented: $confirmICloudRestore) {
            Button("Cancel", role: .cancel) {}
            Button("Restore", role: .destructive) { restoreFromICloud() }
        } message: {
            Text("This replaces all data on this device with your iCloud backup\(icloudInfo.map { " from \($0.date.formatted(date: .abbreviated, time: .shortened))" } ?? "").")
        }
    }

    // MARK: - iCloud

    private var icloudSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("iCloud").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
            VStack(spacing: 0) {
                toggleRow(symbol: "icloud.fill", tint: Color(hex: 0x3B82F6), title: "iCloud Backup",
                          subtitle: icloudSubtitle,
                          isOn: Binding(get: { env.settings.icloudBackup }, set: { setICloudEnabled($0) }))
                if env.settings.icloudBackup {
                    if let availability = icloudAvailability, !availability.isAvailable {
                        Divider().background(theme.border)
                        unavailableRow(availability)
                    } else {
                        Divider().background(theme.border)
                        toggleRow(symbol: nil, tint: theme.muted, title: "Auto backup",
                                  subtitle: "Backs up when you leave the app",
                                  isOn: Binding(get: { env.settings.autoBackup }, set: { env.settings.autoBackup = $0 }))
                        Divider().background(theme.border)
                        icloudActionsRow
                    }
                }
            }
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))

            if let status = icloudStatus {
                Text(status).font(KeelFont.caption).foregroundStyle(theme.muted).padding(.horizontal, 4)
            }
        }
    }

    private var icloudSubtitle: String {
        if !env.settings.icloudBackup { return "Keep a copy in your Apple account" }
        guard let availability = icloudAvailability else { return "Checking iCloud…" }
        switch availability {
        case .unavailable: return "iCloud unavailable"
        case .available:
            if let info = icloudInfo { return "Last backed up \(relative(info.date))" }
            return "No backup yet"
        }
    }

    private func unavailableRow(_ availability: ICloudBackupService.Availability) -> some View {
        let reason: String = { if case .unavailable(let r) = availability { return r } else { return "" } }()
        return HStack(alignment: .top, spacing: 10) {
            Image(systemName: "info.circle").font(.system(size: 14)).foregroundStyle(theme.muted).padding(.top, 1)
            Text(reason).font(KeelFont.caption).foregroundStyle(theme.muted).fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }.padding(14)
    }

    private var icloudActionsRow: some View {
        HStack(spacing: 10) {
            Button { backupNow() } label: {
                HStack(spacing: 6) {
                    if icloudBusy { ProgressView().controlSize(.small) } else { Image(systemName: "arrow.up.to.line") }
                    Text(icloudBusy ? "Backing up…" : "Back up now")
                }
                .font(KeelFont.sans(13, weight: .medium)).foregroundStyle(theme.accent)
                .frame(maxWidth: .infinity).padding(.vertical, 11)
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(theme.accent.opacity(0.35), lineWidth: 1))
            }.buttonStyle(.plain).disabled(icloudBusy)
            Button { confirmICloudRestore = true } label: {
                Text("Restore")
                    .font(KeelFont.sans(13, weight: .medium)).foregroundStyle(theme.sage)
                    .frame(maxWidth: .infinity).padding(.vertical, 11)
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(theme.sage.opacity(0.35), lineWidth: 1))
            }.buttonStyle(.plain).disabled(icloudBusy || icloudInfo == nil)
        }.padding(12)
    }

    // MARK: - Local (real export)

    private var localSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Local Backup").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    Image(systemName: "iphone").font(.system(size: 18)).foregroundStyle(theme.text).frame(width: 40, height: 40)
                        .background(theme.track).clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Save to device").font(KeelFont.body).foregroundStyle(theme.text)
                        Text("Export everything as a .keelbackup file").font(KeelFont.caption).foregroundStyle(theme.muted)
                    }
                    Spacer()
                }.padding(14)
                Divider().background(theme.border)
                if let exportURL {
                    ShareLink(item: exportURL, preview: SharePreview("Keel backup")) {
                        Label("Export data", systemImage: "arrow.down.to.line")
                            .font(KeelFont.body).foregroundStyle(theme.accent)
                            .frame(maxWidth: .infinity, alignment: .leading).padding(14)
                    }
                    .simultaneousGesture(TapGesture().onEnded { refreshExport() })
                } else {
                    Button { refreshExport() } label: {
                        Label("Prepare export", systemImage: "arrow.down.to.line")
                            .font(KeelFont.body).foregroundStyle(theme.accent)
                            .frame(maxWidth: .infinity, alignment: .leading).padding(14)
                    }.buttonStyle(.plain)
                }
            }
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
        }
    }

    // MARK: - Restore (real file import)

    private var restoreSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Restore").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
            switch restore {
            case .idle:
                Button { showImporter = true } label: {
                    HStack(spacing: 14) {
                        Image(systemName: "arrow.up.doc").font(.system(size: 16)).foregroundStyle(theme.accent).frame(width: 40, height: 40)
                            .background(theme.accent.opacity(0.1)).clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Restore from a file").font(KeelFont.body).foregroundStyle(theme.text)
                            Text("Choose a .keelbackup file you exported").font(KeelFont.caption).foregroundStyle(theme.muted)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(theme.muted)
                    }
                    .padding(14)
                }
                .buttonStyle(.plain)
                .background(theme.card)
                .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
            case .confirm:
                confirmCard
            case .done:
                doneBanner
            }
        }
    }

    private var confirmCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "exclamationmark.triangle.fill").font(.system(size: 17)).foregroundStyle(Color(hex: 0xEA580C))
                VStack(alignment: .leading, spacing: 6) {
                    Text("Replace all current data?").font(KeelFont.body).fontWeight(.medium).foregroundStyle(Color(hex: 0xC2410C))
                    Text(confirmDetail).font(KeelFont.caption).foregroundStyle(Color(hex: 0xEA580C)).fixedSize(horizontal: false, vertical: true)
                }
            }
            HStack(spacing: 12) {
                Button { withAnimation { restore = .idle }; pending = nil } label: {
                    Text("Cancel").font(KeelFont.body).foregroundStyle(theme.text)
                        .frame(maxWidth: .infinity).padding(.vertical, 11)
                        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(theme.border, lineWidth: 1))
                }.buttonStyle(.plain)
                Button { performRestore() } label: {
                    Text("Yes, restore").font(KeelFont.body).foregroundStyle(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 11)
                        .background(Color(hex: 0xEA580C)).clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }.buttonStyle(.plain)
            }
        }
        .padding(18)
        .background(Color(hex: 0xEA580C).opacity(0.06))
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(Color(hex: 0xEA580C).opacity(0.2), lineWidth: 1))
    }

    private var doneBanner: some View {
        HStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill").font(.system(size: 20)).foregroundStyle(Color(hex: 0x16A34A))
            VStack(alignment: .leading, spacing: 2) {
                Text("Data restored").font(KeelFont.body).foregroundStyle(Color(hex: 0x15803D))
                if let s = summary {
                    Text("\(s.checkIns) check-ins · \(s.medications) meds · \(s.cycleEntries) cycle entries")
                        .font(KeelFont.caption).foregroundStyle(Color(hex: 0x16A34A))
                }
            }
            Spacer()
            Button { withAnimation { restore = .idle }; summary = nil } label: {
                Text("Done").font(KeelFont.caption).foregroundStyle(Color(hex: 0x15803D))
            }.buttonStyle(.plain)
        }
        .padding(16)
        .background(Color(hex: 0x16A34A).opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var confirmDetail: String {
        guard let p = pending else { return "Your current data will be overwritten." }
        let when = p.exportedAt.formatted(date: .abbreviated, time: .shortened)
        return "This restores \(p.checkIns.count) check-ins, \(p.symptoms.count) symptoms and \(p.medications.count) medications from a backup made \(when). Your current data will be overwritten."
    }

    // MARK: - Actions

    private func refreshExport() {
        exportURL = try? BackupService.exportFile(context: env.context)
    }

    // MARK: iCloud actions

    private func loadICloud() {
        Task {
            let availability = await env.icloudBackup.availability()
            icloudAvailability = availability
            icloudInfo = availability.isAvailable ? (try? await env.icloudBackup.latest()) : nil
        }
    }

    private func setICloudEnabled(_ on: Bool) {
        env.settings.icloudBackup = on
        icloudStatus = nil
        if on { loadICloud() }
    }

    private func backupNow() {
        icloudBusy = true; icloudStatus = nil
        Task {
            do {
                let date = try await env.icloudBackup.backUpNow()
                icloudInfo = try? await env.icloudBackup.latest()
                icloudStatus = "Backed up \(relative(date))."
                Haptics.success()
            } catch {
                errorMessage = error.localizedDescription
            }
            icloudBusy = false
        }
    }

    private func restoreFromICloud() {
        icloudBusy = true
        Task {
            do {
                summary = try await env.icloudBackup.restore()
                refreshExport()
                Haptics.success()
                withAnimation { restore = .done }
            } catch {
                errorMessage = error.localizedDescription
            }
            icloudBusy = false
        }
    }

    private func relative(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .full
        return formatter.localizedString(for: date, relativeTo: .now)
    }

    private func handleImport(_ result: Result<URL, Error>) {
        switch result {
        case .failure(let error):
            errorMessage = error.localizedDescription
        case .success(let url):
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            do {
                let data = try Data(contentsOf: url)
                pending = try BackupService.decode(data)
                withAnimation { restore = .confirm }
            } catch {
                errorMessage = "That file isn't a valid Keel backup.\n\n\(error.localizedDescription)"
            }
        }
    }

    private func performRestore() {
        guard let p = pending else { return }
        do {
            summary = try BackupService.restore(from: p, into: env.context)
            Haptics.success()
            pending = nil
            refreshExport()
            withAnimation { restore = .done }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    // MARK: - Rows

    private func toggleRow(symbol: String?, tint: Color, title: String, subtitle: String, isOn: Binding<Bool>) -> some View {
        HStack(spacing: 12) {
            if let symbol {
                Image(systemName: symbol).font(.system(size: 16)).foregroundStyle(tint).frame(width: 40, height: 40)
                    .background(tint.opacity(0.1)).clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(KeelFont.body).foregroundStyle(theme.text)
                Text(subtitle).font(KeelFont.caption).foregroundStyle(theme.muted)
            }
            Spacer()
            Toggle("", isOn: isOn).labelsHidden().tint(theme.accent)
        }
        .padding(14)
    }

    #if DEBUG
    private func applyDebugStage() {
        switch DebugHarness.backupStage {
        case 1:
            pending = try? BackupService.export(context: env.context)
            restore = .confirm
        case 2:
            summary = BackupService.Summary(checkIns: checkIns.count, symptoms: 3, medications: 2)
            restore = .done
        default: break
        }
    }
    #endif
}
