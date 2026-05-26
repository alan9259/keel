import SwiftUI

struct ThemesView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    private var active: KeelThemeOption { ThemeCatalog.option(env.settings.themeID) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(title: "Themes", titleSize: 28, subtitle: "Make Keel feel like yours") { dismiss() }

                activeCard

                VStack(spacing: 12) { ForEach(ThemeCatalog.all) { ownedRow($0) } }
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
    }

    private var activeCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Current theme").keelEyebrow()
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text(active.name).font(KeelFont.serif(20, weight: .semibold)).foregroundStyle(theme.heading)
                    Text(active.detail).font(KeelFont.caption).foregroundStyle(theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer()
                swatchRow(active.swatches, size: 18)
            }
        }
        .padding(18)
        .background(theme.accent.opacity(0.06))
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.accentBorder, lineWidth: 1))
    }

    private func ownedRow(_ option: KeelThemeOption) -> some View {
        let isActive = option.id == env.settings.themeID
        return Button {
            withAnimation(.easeInOut(duration: 0.25)) { env.settings.themeID = option.id }
            Haptics.selection()
        } label: {
            HStack(spacing: 14) {
                swatchColumn(option.swatches)
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 8) {
                        Text(option.name).font(KeelFont.bodyLarge).foregroundStyle(theme.text)
                        if isActive { activeBadge }
                    }
                    Text(option.detail).font(KeelFont.caption).foregroundStyle(theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .padding(16)
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous)
                .stroke(isActive ? theme.accent : theme.border, lineWidth: isActive ? 2 : 1))
        }
        .buttonStyle(.plain)
    }

    private func swatchRow(_ colors: [Color], size: CGFloat) -> some View {
        HStack(spacing: 5) { ForEach(colors.indices, id: \.self) { Circle().fill(colors[$0]).frame(width: size, height: size) } }
    }

    private func swatchColumn(_ colors: [Color]) -> some View {
        VStack(spacing: 4) { ForEach(colors.prefix(3).indices, id: \.self) { Circle().fill(colors[$0]).frame(width: 12, height: 12) } }
    }

    private var activeBadge: some View {
        Text("Active").font(KeelFont.sans(11)).foregroundStyle(theme.accent)
            .padding(.horizontal, 8).padding(.vertical, 2)
            .background(theme.accent.opacity(0.12)).clipShape(Capsule())
    }
}
