import SwiftUI

/// The row of PIN dots: `total` circles, the first `filled` of them solid. Fills as
/// she types.
struct PINDots: View {
    @Environment(\.keelTheme) private var theme
    let filled: Int
    let total: Int

    var body: some View {
        HStack(spacing: 20) {
            ForEach(0..<total, id: \.self) { i in
                Circle()
                    .fill(i < filled ? theme.accent : Color.clear)
                    .frame(width: 16, height: 16)
                    .overlay(Circle().stroke(i < filled ? theme.accent : theme.border, lineWidth: 1.5))
            }
        }
        .animation(.easeOut(duration: 0.12), value: filled)
        .accessibilityLabel("\(filled) of \(total) digits entered")
    }
}

/// A number pad for PIN entry: 1-9, then a blank, 0, and delete. Big tappable keys.
struct PINPad: View {
    @Environment(\.keelTheme) private var theme
    var enabled: Bool = true
    let onDigit: (Int) -> Void
    let onDelete: () -> Void

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 18), count: 3)

    var body: some View {
        LazyVGrid(columns: columns, spacing: 18) {
            ForEach(1...9, id: \.self) { digit in
                key(String(digit)) { onDigit(digit) }
            }
            Color.clear.frame(height: 72) // blank bottom-left
            key("0") { onDigit(0) }
            deleteKey
        }
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.4)
    }

    private func key(_ label: String, action: @escaping () -> Void) -> some View {
        Button {
            Haptics.selection()
            action()
        } label: {
            Text(label)
                .font(KeelFont.serif(28, weight: .regular))
                .foregroundStyle(theme.text)
                .frame(maxWidth: .infinity)
                .frame(height: 72)
                .background(theme.card)
                .clipShape(Circle())
                .overlay(Circle().stroke(theme.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var deleteKey: some View {
        Button {
            Haptics.selection()
            onDelete()
        } label: {
            Image(systemName: "delete.left")
                .font(.system(size: 22))
                .foregroundStyle(theme.muted)
                .frame(maxWidth: .infinity)
                .frame(height: 72)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Delete")
    }
}
