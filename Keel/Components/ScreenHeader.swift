import SwiftUI

/// Back-arrow + Cormorant title header used on pushed feature screens (the new
/// design replaces the native nav bar with this).
struct ScreenHeader: View {
    @Environment(\.keelTheme) private var theme
    let title: String
    /// One consistent screen-title size across the app (matches onboarding).
    var titleSize: CGFloat = 28
    var subtitle: String?
    /// Keep a long title on one line, shrinking it to fit rather than wrapping.
    /// Off by default so titles still grow with Dynamic Type.
    var fitsOneLine: Bool = false
    let onBack: () -> Void

    var body: some View {
        HStack(alignment: subtitle == nil ? .center : .top, spacing: 14) {
            Button(action: onBack) {
                Image(systemName: "arrow.left")
                    .font(.system(size: 22, weight: .regular))
                    .foregroundStyle(theme.muted)
                    .frame(width: 32, height: 32)
            }
            .accessibilityLabel("Back")

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(KeelFont.serif(titleSize, weight: .semibold))
                    .foregroundStyle(theme.heading)
                    .lineLimit(fitsOneLine ? 1 : nil)
                    .minimumScaleFactor(fitsOneLine ? 0.7 : 1)
                    .fixedSize(horizontal: false, vertical: !fitsOneLine)
                if let subtitle {
                    Text(subtitle)
                        .font(KeelFont.caption)
                        .foregroundStyle(theme.muted)
                }
            }
            Spacer(minLength: 0)
        }
    }
}

/// Applies the new full-bleed screen styling (cream background + hidden native
/// nav bar) to a pushed feature screen.
extension View {
    func keelFeatureScreen() -> some View {
        self
            .navigationBarBackButtonHidden(true)
            .toolbar(.hidden, for: .navigationBar)
    }
}
