import SwiftUI

/// Labelled text field (F3F0EB fill, 14pt radius, accent focus border, optional
/// password show/hide).
struct KeelTextField: View {
    @Environment(\.keelTheme) private var theme
    let label: String
    var placeholder: String = ""
    @Binding var text: String
    var isSecure: Bool = false
    var keyboard: UIKeyboardType = .default
    var textContentType: UITextContentType?
    var autocapitalization: TextInputAutocapitalization = .never

    @State private var reveal = false
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(label)
                .font(KeelFont.body)
                .fontWeight(.medium)
                .foregroundStyle(theme.text)

            HStack(spacing: Spacing.sm) {
                Group {
                    if isSecure && !reveal {
                        SecureField(placeholder, text: $text)
                    } else {
                        TextField(placeholder, text: $text)
                    }
                }
                .font(KeelFont.bodyLarge)
                .foregroundStyle(theme.text)
                .keyboardType(keyboard)
                .textContentType(textContentType)
                .textInputAutocapitalization(autocapitalization)
                .autocorrectionDisabled(isSecure)
                .focused($focused)

                if isSecure {
                    Button { reveal.toggle() } label: {
                        Image(systemName: reveal ? "eye" : "eye.slash")
                            .foregroundStyle(theme.muted)
                    }
                    .accessibilityLabel(reveal ? "Hide password" : "Show password")
                }
            }
            .padding(16)
            .background(theme.inputBackground)
            .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.md, style: .continuous)
                    .stroke(focused ? theme.accent : theme.border, lineWidth: focused ? 2 : 1)
            )
            .animation(.easeOut(duration: 0.15), value: focused)
        }
    }
}

/// Multi-line note editor with an overlaid placeholder and a trailing accessory
/// slot (the voice-input mic).
struct KeelTextEditor<Accessory: View>: View {
    @Environment(\.keelTheme) private var theme
    var placeholder: String
    @Binding var text: String
    var minHeight: CGFloat = 110
    @ViewBuilder var accessory: () -> Accessory

    @FocusState private var focused: Bool

    var body: some View {
        ZStack(alignment: .topLeading) {
            if text.isEmpty {
                Text(placeholder)
                    .font(KeelFont.body)
                    .foregroundStyle(theme.muted)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 16)
                    .allowsHitTesting(false)
            }
            TextEditor(text: $text)
                .font(KeelFont.body)
                .foregroundStyle(theme.text)
                .scrollContentBackground(.hidden)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .focused($focused)
            accessory()
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                .padding(10)
        }
        .frame(minHeight: minHeight, alignment: .topLeading)
        .background(theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radius.card, style: .continuous)
                .stroke(focused ? theme.accent : theme.border, lineWidth: focused ? 2 : 1)
        )
        .animation(.easeOut(duration: 0.15), value: focused)
    }
}
