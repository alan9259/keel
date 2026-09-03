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

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                ScreenHeader(title: "Backup & Restore", subtitle: "Your data, safely yours") { dismiss() }

                localSection
                restoreSection
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
        .onAppear {
            refreshExport()
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
    }

    // MARK: - Local (real export)

    private var localSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Save a backup").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    Image(systemName: "iphone").font(.system(size: 18)).foregroundStyle(theme.text).frame(width: 40, height: 40)
                        .background(theme.track).clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Save to a file").font(KeelFont.body).foregroundStyle(theme.text)
                        Text("Export everything as a .keelbackup file you keep").font(KeelFont.caption).foregroundStyle(theme.muted)
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

            Text("Your data stays on this device. A backup file is yours to keep and store wherever you like.")
                .font(KeelFont.caption).foregroundStyle(theme.muted).padding(.horizontal, 4)
                .fixedSize(horizontal: false, vertical: true)
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
