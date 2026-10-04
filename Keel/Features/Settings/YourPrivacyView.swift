import SwiftUI

/// The privacy summary shown in the app (Settings > Your privacy, and About). The
/// wording is the owner's, supplied for V1 (4 Oct 2026); keep it verbatim, as legal
/// text not to be reworded. The layout follows the production policy draft (3 Oct
/// 2026): last updated under the title, "The short version", then the at-a-glance
/// table with a header row. The full policy lives on the web (`KeelLinks.privacyPolicy`).
enum PrivacySummary {
    static let title = "Your privacy"

    static let shortVersion: [String] = [
        "Your Keel records are stored locally on your iPhone. Keel has no account, no cloud sync and no server holding your records. The Keel team cannot access them.",
        "You control whether to share a GP Visit Summary or export a backup. Your records may also be included in your iPhone's own backups, depending on your settings.",
        "Keel currently contains no advertising, tracking or third-party analytics. Speech recognition and supported Apple AI features run on your iPhone without sending your recordings or records to Apple, the Keel team or another service.",
        "If you email us for technical support, we receive the information you choose to include in that email.",
        "Keel supports preparation for healthcare conversations. It does not diagnose, prescribe or replace your doctor. Nothing in Keel is medical advice.",
    ]

    struct Row: Identifiable {
        let what: String
        let kept: String
        let access: String
        let retention: String
        var id: String { what }
    }

    static let atAGlance: [Row] = [
        Row(what: "Check-ins, notes, symptoms, periods, medications and treatments",
            kept: "Locally on your iPhone",
            access: "Accessible through your device; the Keel team cannot access them",
            retention: "Until deleted. Use Settings > Delete all my data"),
        Row(what: "Optional profile details",
            kept: "Locally on your iPhone",
            access: "Accessible through your device; may be included in exports you choose",
            retention: "Edit your profile or use Delete all my data"),
        Row(what: "Imported Apple Health readings",
            kept: "Locally within Keel",
            access: "Accessible through your device; the Keel team cannot access them",
            retention: "Remain after disconnection. Delete through Delete all my data"),
        Row(what: "Text created through voice input",
            kept: "Locally as part of your Keel record",
            access: "Accessible through your device; the Keel team cannot access it",
            retention: "Delete the entry or use Delete all my data"),
        Row(what: "Device backups",
            kept: "In your chosen iCloud or computer backup",
            access: "Governed by your backup arrangements; the Keel team cannot access them",
            retention: "Manage separately through your backup settings or software"),
        Row(what: "Exported summaries and Keel backups",
            kept: "Wherever you save or send them",
            access: "Anyone with access to those files",
            retention: "Delete each copy separately. Keel cannot recall them"),
        Row(what: "Support correspondence",
            kept: "Our email service",
            access: "Authorised Keel team members and the service provider",
            retention: "Up to two years, unless longer retention is legally required. Contact us to request deletion"),
    ]

    /// The table's column headings: "Your information" (as in the draft), then the
    /// owner's three columns.
    static let columns = ["Your information", "Where it is kept", "Access", "Retention and deletion"]

    static let lastUpdated = "Last updated: 5 October 2026"
}

struct YourPrivacyView: View {
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var showNoMailApp = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                ScreenHeader(title: PrivacySummary.title, titleSize: 28,
                             subtitle: PrivacySummary.lastUpdated) { dismiss() }

                VStack(alignment: .leading, spacing: 12) {
                    sectionTitle("The short version")
                    ForEach(PrivacySummary.shortVersion, id: \.self) { paragraph in
                        Text(paragraph)
                            .font(KeelFont.body).foregroundStyle(theme.text.opacity(0.85)).lineSpacing(3)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }

                VStack(alignment: .leading, spacing: 12) {
                    sectionTitle("Your information at a glance")
                    glanceTable
                }

                links
            }
            .padding(.horizontal, Spacing.screenH).padding(.vertical, Spacing.md)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
        .alert("No email app set up", isPresented: $showNoMailApp) {
            Button("Copy email address") { UIPasteboard.general.string = FeedbackMail.address }
            Button("OK", role: .cancel) {}
        } message: {
            Text("We couldn't find an email app to open. You can copy our address and write to us from anywhere: \(FeedbackMail.address)")
        }
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text).font(KeelFont.serif(20, weight: .semibold)).foregroundStyle(theme.heading)
            .accessibilityAddTraits(.isHeader)
    }

    // MARK: At a glance (table, as in the policy draft)

    private let columnWidths: [CGFloat] = [170, 150, 190, 200]

    /// The draft's table: a header row and one row per kind of information. Four
    /// columns are wider than a phone, so the table scrolls sideways.
    private var glanceTable: some View {
        ScrollView(.horizontal, showsIndicators: true) {
            Grid(alignment: .topLeading, horizontalSpacing: 0, verticalSpacing: 0) {
                GridRow {
                    ForEach(Array(PrivacySummary.columns.enumerated()), id: \.offset) { index, heading in
                        cell(heading, column: index, header: true)
                    }
                }
                .background(theme.accentTint)
                .accessibilityHidden(true)   // each row below reads with its headings
                ForEach(PrivacySummary.atAGlance) { row in
                    Divider().background(theme.border)
                    GridRow {
                        cell(row.what, column: 0, header: false, emphasised: true)
                        cell(row.kept, column: 1, header: false)
                        cell(row.access, column: 2, header: false)
                        cell(row.retention, column: 3, header: false)
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(accessibilityText(row))
                }
            }
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
            .padding(.bottom, 8)   // room for the scroll indicator
        }
    }

    private func cell(_ text: String, column: Int, header: Bool, emphasised: Bool = false) -> some View {
        Text(text)
            .font(header ? KeelFont.sans(13, weight: .semibold)
                  : emphasised ? KeelFont.sans(14, weight: .medium) : KeelFont.sans(14))
            .foregroundStyle(header ? theme.heading : theme.text.opacity(emphasised ? 1 : 0.85))
            .fixedSize(horizontal: false, vertical: true)
            .padding(12)
            .frame(width: columnWidths[column], alignment: .topLeading)
            .frame(maxHeight: .infinity, alignment: .topLeading)
            .overlay(alignment: .trailing) {
                if column < columnWidths.count - 1 { Rectangle().fill(theme.border).frame(width: 1) }
            }
    }

    private func accessibilityText(_ row: PrivacySummary.Row) -> String {
        let c = PrivacySummary.columns
        return "\(row.what). \(c[1]): \(row.kept). \(c[2]): \(row.access). \(c[3]): \(row.retention)."
    }

    // MARK: Links

    private var links: some View {
        VStack(spacing: 0) {
            Button { openURL(KeelLinks.privacyPolicy) } label: {
                linkRow("doc.text", "Read the full privacy policy", detail: nil)
            }
            .buttonStyle(.plain)
            Divider().background(theme.border)
            Button(action: emailSupport) {
                linkRow("envelope", "Support", detail: FeedbackMail.address)
            }
            .buttonStyle(.plain)
        }
        .background(theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
    }

    private func linkRow(_ symbol: String, _ title: String, detail: String?) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).font(.system(size: 16)).foregroundStyle(theme.accent).frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(KeelFont.body).foregroundStyle(theme.text)
                if let detail {
                    Text(detail).font(KeelFont.caption).foregroundStyle(theme.muted)
                }
            }
            Spacer()
            Image(systemName: "arrow.up.right").font(.system(size: 14, weight: .semibold)).foregroundStyle(theme.muted)
        }
        .padding(14)
        // The whole row is the tap target.
        .contentShape(Rectangle())
    }

    /// A support email in her default mail app; if she has none set up, the address
    /// to copy (same pattern as Share feedback in More).
    private func emailSupport() {
        guard let url = FeedbackMail.url(kind: .support, version: DeviceContext.appVersion,
                                         os: DeviceContext.osVersion, device: DeviceContext.deviceModel) else {
            showNoMailApp = true
            return
        }
        openURL(url) { accepted in
            if !accepted { showNoMailApp = true }
        }
    }
}
