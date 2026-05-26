import SwiftUI

/// Press feedback only (color/shape handled by the button views so they can read
/// the theme).
struct PressableStyle: ButtonStyle {
    var scale: CGFloat = 0.97
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.9 : 1)
            .scaleEffect(configuration.isPressed ? scale : 1)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

struct KeelPrimaryButton: View {
    @Environment(\.keelTheme) private var theme
    let title: String
    var systemImage: String?
    var isEnabled: Bool = true
    let action: () -> Void

    init(_ title: String, systemImage: String? = nil, isEnabled: Bool = true, action: @escaping () -> Void) {
        self.title = title
        self.systemImage = systemImage
        self.isEnabled = isEnabled
        self.action = action
    }

    var body: some View {
        Button {
            Haptics.tap()
            action()
        } label: {
            HStack(spacing: Spacing.sm) {
                if let systemImage { Image(systemName: systemImage) }
                Text(title)
            }
            .font(KeelFont.button)
            .foregroundStyle(theme.background)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 17)
            .background(theme.accent)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        }
        .buttonStyle(PressableStyle())
        .disabled(!isEnabled)
        .opacity(isEnabled ? 1 : 0.5)
        .animation(.easeOut(duration: 0.2), value: isEnabled)
    }
}

struct KeelSecondaryButton: View {
    @Environment(\.keelTheme) private var theme
    let title: String
    var systemImage: String?
    let action: () -> Void

    init(_ title: String, systemImage: String? = nil, action: @escaping () -> Void) {
        self.title = title
        self.systemImage = systemImage
        self.action = action
    }

    var body: some View {
        Button {
            Haptics.light()
            action()
        } label: {
            HStack(spacing: Spacing.sm) {
                if let systemImage { Image(systemName: systemImage) }
                Text(title)
            }
            .font(KeelFont.button)
            .foregroundStyle(theme.text)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(
                RoundedRectangle(cornerRadius: Radius.input, style: .continuous)
                    .stroke(theme.text.opacity(0.18), lineWidth: 2)
            )
        }
        .buttonStyle(PressableStyle())
    }
}

/// Muted text link with an optional underlined emphasized suffix.
struct KeelTextLink: View {
    @Environment(\.keelTheme) private var theme
    let text: String
    var emphasized: String?
    let action: () -> Void

    init(_ text: String, emphasized: String? = nil, action: @escaping () -> Void) {
        self.text = text
        self.emphasized = emphasized
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Text("\(Text(text).foregroundColor(theme.muted))\(emphasizedText)")
                .font(KeelFont.body)
        }
        .buttonStyle(.plain)
        .frame(minHeight: 44)
    }

    private var emphasizedText: Text {
        guard let emphasized else { return Text("") }
        return Text(emphasized).foregroundColor(theme.text).underline()
    }
}
