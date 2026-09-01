import SwiftUI
import UIKit

/// The settings/more hub — sectioned rows that navigate to each feature (or, for
/// feedback, open a mail draft).
struct MoreView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.keelTheme) private var theme
    @Environment(\.openURL) private var openURL

    /// Shown if opening a feedback draft fails (no mail app), so she can copy the address.
    @State private var showNoMailApp = false
    /// Presents the iOS share sheet for "Tell your friends".
    @State private var showShareSheet = false

    private struct Item: Identifiable {
        let title: String
        let symbol: String
        let tint: Color
        var route: MainRoute? = nil
        var action: (() -> Void)? = nil
        var id: String { title }
    }

    private var sections: [(String, [Item])] {
        [
            ("Tracking", [
                // Cycle Tracking and the GP Visit Summary both live on the home screen
                // (button group / card), so they aren't duplicated here.
                Item(title: "Activities", symbol: "figure.walk", tint: theme.sage, route: .activities),
                Item(title: "Reminders", symbol: "bell.fill", tint: theme.accent, route: .reminders),
                Item(title: "Stats", symbol: "chart.bar.fill", tint: theme.plum, route: .reports),
            ]),
            ("Personalisation", [
                Item(title: "Mood Icons", symbol: "face.smiling", tint: theme.accent, route: .moodIcons),
                Item(title: "Themes", symbol: "paintpalette.fill", tint: theme.plum, route: .themes),
                Item(title: "Colour Mode", symbol: "circle.lefthalf.filled", tint: theme.heading, route: .colourMode),
            ]),
            ("Data", [
                Item(title: "Backup & Restore", symbol: "icloud.fill", tint: theme.accent, route: .backup),
                Item(title: "Apple Health", symbol: "heart.fill", tint: Color(hex: 0xE91E63), route: .appleHealth),
            ]),
            ("Feedback", [
                // Open a pre-filled draft in her own mail app (see compose(_:)).
                Item(title: "Share feedback", symbol: "envelope.fill", tint: theme.accent, action: { compose(.feedback) }),
                Item(title: "Request a feature", symbol: "lightbulb.fill", tint: theme.plum, action: { compose(.featureRequest) }),
            ]),
            ("Account", [
                Item(title: "Profile", symbol: "person.crop.circle", tint: theme.accent, route: .profile),
                Item(title: "Settings", symbol: "gearshape.fill", tint: theme.muted, route: .settings),
                Item(title: "Get Support", symbol: "lifepreserver.fill", tint: theme.accent, route: .support),
                Item(title: "Connect with Us", symbol: "bubble.left.and.bubble.right.fill", tint: theme.sage, route: .connect),
                Item(title: "Tell your friends", symbol: "square.and.arrow.up", tint: theme.accent, action: { showShareSheet = true }),
                Item(title: "About Keel", symbol: "info.circle.fill", tint: theme.muted, route: .about),
            ]),
        ]
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                ScreenHeader(title: "More") { dismiss() }

                ForEach(sections, id: \.0) { section in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(section.0.uppercased()).font(KeelFont.eyebrow).tracking(1.2)
                            .foregroundStyle(theme.muted).padding(.leading, 4)
                        VStack(spacing: 0) {
                            ForEach(Array(section.1.enumerated()), id: \.element.id) { idx, item in
                                Group {
                                    if let route = item.route {
                                        NavigationLink(value: route) { row(item) }
                                    } else {
                                        Button { Haptics.light(); item.action?() } label: { row(item) }
                                    }
                                }
                                .buttonStyle(.plain)
                                if idx < section.1.count - 1 { Divider().background(theme.border) }
                            }
                        }
                        .background(theme.card)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous).stroke(theme.border, lineWidth: 1))
                    }
                }

                Text("Keel · Version \(DeviceContext.shortVersion)")
                    .font(KeelFont.caption).foregroundStyle(theme.muted)
                    .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, Spacing.screenH)
            .padding(.vertical, Spacing.md)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
        .alert("No email app set up", isPresented: $showNoMailApp) {
            Button("Copy email address") { UIPasteboard.general.string = FeedbackMail.address }
            Button("OK", role: .cancel) {}
        } message: {
            Text("We couldn't find an email app to open. You can copy our address and write to us from anywhere: \(FeedbackMail.address)")
        }
        .sheet(isPresented: $showShareSheet) {
            ShareSheet(items: ShareInvite.activityItems)
        }
    }

    /// Open a pre-filled feedback / feature-request draft in her default mail app.
    /// If nothing handles it (no mail account), offer the address to copy instead.
    private func compose(_ kind: FeedbackMail.Kind) {
        guard let url = FeedbackMail.url(kind: kind,
                                         version: DeviceContext.appVersion,
                                         os: DeviceContext.osVersion,
                                         device: DeviceContext.deviceModel) else {
            showNoMailApp = true
            return
        }
        openURL(url) { accepted in
            if !accepted { showNoMailApp = true }
        }
    }

    private func row(_ item: Item) -> some View {
        HStack(spacing: 14) {
            Image(systemName: item.symbol).font(.system(size: 16))
                .foregroundStyle(item.tint)
                .frame(width: 34, height: 34)
                .background(item.tint.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            Text(item.title).font(KeelFont.bodyLarge).foregroundStyle(theme.text)
            Spacer()
            Image(systemName: "chevron.right").font(.system(size: 14, weight: .semibold)).foregroundStyle(theme.muted)
        }
        .padding(.horizontal, 16).padding(.vertical, 13)
        .contentShape(Rectangle())
    }
}

/// Wraps `UIActivityViewController` so "Tell your friends" uses the system share sheet.
private struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
