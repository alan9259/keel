import SwiftUI

/// The settings/more hub — sectioned rows that navigate to each feature.
struct MoreView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.keelTheme) private var theme

    private struct Item: Identifiable {
        let title: String
        let symbol: String
        let tint: Color
        let route: MainRoute
        var id: String { title }
    }

    private var sections: [(String, [Item])] {
        [
            ("Tracking", [
                Item(title: "Cycle Tracking", symbol: "calendar", tint: theme.accent, route: .cycle),
                Item(title: "Activities", symbol: "figure.walk", tint: theme.sage, route: .activities),
                Item(title: "Reminders", symbol: "bell.fill", tint: theme.accent, route: .reminders),
                Item(title: "Reports", symbol: "chart.bar.fill", tint: theme.plum, route: .reports),
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
            ("Account", [
                Item(title: "Profile", symbol: "person.crop.circle", tint: theme.accent, route: .profile),
                Item(title: "Settings", symbol: "gearshape.fill", tint: theme.muted, route: .settings),
                Item(title: "Get Support", symbol: "lifepreserver.fill", tint: theme.accent, route: .support),
                Item(title: "Connect with Us", symbol: "bubble.left.and.bubble.right.fill", tint: theme.sage, route: .connect),
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
                                NavigationLink(value: item.route) { row(item) }
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
