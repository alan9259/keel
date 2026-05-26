import SwiftUI

/// Quick mood capture (bottom sheet). Picking a mood hands off to the full
/// check-in at the detail step (energy / diary / symptoms) with that mood
/// already set — the slide *is* the mood step, so it isn't asked again.
struct EntrySheet: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    /// nil = dismissed ("remind me later").
    let onPick: (Mood?) -> Void

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 10), count: 5)

    var body: some View {
        VStack(spacing: 34) {
            VStack(spacing: 8) {
                Text(Greeting.current())
                    .font(KeelFont.serif(28, weight: .semibold)).foregroundStyle(theme.heading)
                Text("How are you feeling right now?")
                    .font(KeelFont.bodyLarge).foregroundStyle(theme.muted)
            }
            .multilineTextAlignment(.center)

            LazyVGrid(columns: columns, spacing: 10) {
                ForEach(Mood.allCases) { m in
                    Button {
                        Haptics.selection()
                        onPick(m)
                    } label: {
                        VStack(spacing: 8) {
                            EmojiGlyph(emoji: env.settings.emoji(for: m), size: 30)
                                .frame(maxWidth: .infinity, minHeight: 34)
                            Text(m.label).font(KeelFont.sans(11)).foregroundStyle(theme.muted)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 16)
                        .background(theme.card)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous)
                            .stroke(theme.border, lineWidth: 2))
                    }
                    .buttonStyle(.plain)
                }
            }

            Button { onPick(nil) } label: {
                Text("Remind me later").font(KeelFont.body).foregroundStyle(theme.muted)
            }
        }
        .padding(.horizontal, 30).padding(.top, 20)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.background)
    }
}
