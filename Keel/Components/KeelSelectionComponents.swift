import SwiftUI

/// Pill tag for symptom selection with a severity level. Tapping cycles the
/// level (0 none, 1 mild/yellow, 2 moderate/red, 3 severe/purple); the fill
/// colour and a matching count of dots show how strongly it was felt.
struct SymptomChip: View {
    @Environment(\.keelTheme) private var theme
    let title: String
    /// 0 = unselected; 1…3 map to `SymptomSeverity`.
    let level: Int
    let action: () -> Void

    private var severity: SymptomSeverity? { SymptomSeverity(rawValue: level) }

    var body: some View {
        Button {
            Haptics.selection()
            action()
        } label: {
            HStack(spacing: 6) {
                Text(title).font(KeelFont.body)
                if let severity {
                    HStack(spacing: 2) {
                        ForEach(0..<severity.rawValue, id: \.self) { _ in
                            Circle().fill(.white.opacity(0.9)).frame(width: 4, height: 4)
                        }
                    }
                }
            }
            .foregroundStyle(severity != nil ? .white : theme.text.opacity(0.85))
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(severity?.color ?? theme.card)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(severity != nil ? Color.clear : theme.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(severity != nil ? [.isSelected, .isButton] : .isButton)
        .accessibilityValue(severity?.label ?? "Not selected")
    }
}

/// Selectable card with a leading radio indicator (pathway selection).
struct RadioCard: View {
    @Environment(\.keelTheme) private var theme
    let title: String
    let description: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button {
            Haptics.selection()
            action()
        } label: {
            HStack(alignment: .top, spacing: Spacing.md) {
                ZStack {
                    Circle()
                        .stroke(isSelected ? theme.accent : theme.border, lineWidth: 2)
                        .frame(width: 24, height: 24)
                    if isSelected {
                        Circle().fill(theme.accent).frame(width: 24, height: 24)
                        Image(systemName: "checkmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(theme.background)
                    }
                }
                VStack(alignment: .leading, spacing: 6) {
                    Text(title)
                        .font(KeelFont.serif(17, weight: .semibold))
                        .foregroundStyle(theme.heading)
                    Text(description)
                        .font(KeelFont.body)
                        .foregroundStyle(theme.text.opacity(0.7))
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .padding(18)
            .background(isSelected ? theme.accent.opacity(0.05) : theme.card)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.card, style: .continuous)
                    .stroke(isSelected ? theme.accent : theme.border, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? [.isSelected, .isButton] : .isButton)
    }
}
