import SwiftUI

/// Small pill segmented control (e.g. My Packs / Store, period tabs).
struct KeelSegmented: View {
    @Environment(\.keelTheme) private var theme
    let options: [String]
    @Binding var selection: Int

    var body: some View {
        HStack(spacing: 4) {
            ForEach(options.indices, id: \.self) { i in
                Button {
                    Haptics.light()
                    selection = i
                } label: {
                    Text(options[i])
                        .font(KeelFont.sans(14, weight: .medium))
                        .foregroundStyle(selection == i ? theme.text : theme.muted)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 9)
                        .background(selection == i ? theme.card : Color.clear)
                        .clipShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(theme.track)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}
