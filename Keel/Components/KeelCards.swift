import SwiftUI

/// White rounded card with a hairline border (flat — the new design uses borders,
/// not shadows).
struct StandardCard<Content: View>: View {
    @Environment(\.keelTheme) private var theme
    var padding: CGFloat = 18
    var cornerRadius: CGFloat = Radius.card
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(theme.border, lineWidth: 1)
            )
    }
}

/// Sage-tinted callout with a left accent border and a leading icon.
struct CalloutCard<Content: View>: View {
    @Environment(\.keelTheme) private var theme
    var systemImage: String = "leaf.fill"
    @ViewBuilder var content: () -> Content

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.md) {
            Image(systemName: systemImage)
                .font(.system(size: 20))
                .foregroundStyle(theme.sage)
            content()
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(Spacing.md)
        .background(theme.sageTint)
        .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
        .overlay(alignment: .leading) {
            Rectangle().fill(theme.sage).frame(width: 4)
        }
        .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
    }
}

/// Warm gradient hero card (terracotta → sage tint).
struct HeroCard<Content: View>: View {
    @Environment(\.keelTheme) private var theme
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .padding(22)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                LinearGradient(
                    colors: [theme.accent.opacity(0.10), theme.sage.opacity(0.10)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.card, style: .continuous)
                    .stroke(theme.accentBorder, lineWidth: 1)
            )
    }
}

/// Small sage-tinted note with a bold lead-in (insights, reminders, encouragement).
struct InfoNoteCard: View {
    @Environment(\.keelTheme) private var theme
    let lead: String
    let message: String

    var body: some View {
        Text("\(Text(lead).font(KeelFont.caption).fontWeight(.semibold))\(Text(" " + message).font(KeelFont.caption))")
            .foregroundStyle(theme.text.opacity(0.7))
            .frame(maxWidth: .infinity, alignment: .leading)
            .lineSpacing(2)
            .padding(Spacing.md)
            .background(theme.sageTint)
            .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.md, style: .continuous)
                    .stroke(theme.sageBorder, lineWidth: 1)
            )
    }
}
