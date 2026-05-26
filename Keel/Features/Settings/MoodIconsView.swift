import SwiftUI

struct MoodIconsView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(title: "Mood Icons", titleSize: 28, subtitle: "Choose how your moods look") { dismiss() }

                previewCard

                VStack(spacing: 12) { ForEach(MoodPacks.all) { ownedRow($0) } }
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
    }

    private var previewCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Preview: active set").keelEyebrow()
            HStack {
                ForEach(Mood.allCases) { mood in
                    VStack(spacing: 8) {
                        EmojiGlyph(emoji: env.settings.emoji(for: mood), size: 30)
                            .frame(maxWidth: .infinity)
                        Text(mood.label).font(KeelFont.sans(11)).foregroundStyle(theme.muted)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .padding(18)
        .background(theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
    }

    private func ownedRow(_ pack: MoodPack) -> some View {
        let isActive = pack.id == env.settings.moodPackID
        return Button {
            withAnimation(.easeInOut(duration: 0.2)) { env.settings.moodPackID = pack.id }
            Haptics.selection()
        } label: {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    Text(pack.name).font(KeelFont.bodyLarge).foregroundStyle(theme.text)
                    if isActive { activeBadge }
                    Spacer()
                }
                Text(pack.detail).font(KeelFont.caption).foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 10) { ForEach(pack.emoji.indices, id: \.self) { EmojiGlyph(emoji: pack.emoji[$0], size: 22) } }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous)
                .stroke(isActive ? theme.accent : theme.border, lineWidth: isActive ? 2 : 1))
        }
        .buttonStyle(.plain)
    }

    private var activeBadge: some View {
        Text("Active").font(KeelFont.sans(11)).foregroundStyle(theme.accent)
            .padding(.horizontal, 8).padding(.vertical, 2)
            .background(theme.accent.opacity(0.12)).clipShape(Capsule())
    }
}
