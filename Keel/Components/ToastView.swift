import SwiftUI

struct ToastData: Equatable {
    let title: String
    var subtitle: String?
}

/// Dark rounded toast anchored above the home indicator.
struct ToastView: View {
    @Environment(\.keelTheme) private var theme
    let data: ToastData

    var body: some View {
        VStack(spacing: 2) {
            Text(data.title)
                .font(KeelFont.sans(14, weight: .semibold))
            if let subtitle = data.subtitle {
                Text(subtitle)
                    .font(KeelFont.sans(12))
                    .opacity(0.75)
            }
        }
        .foregroundStyle(theme.toastText)
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
        .background(theme.toastBackground)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .shadow(color: .black.opacity(0.3), radius: 15, y: 10)
        .padding(.bottom, 34)
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}
