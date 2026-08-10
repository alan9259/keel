import SwiftUI
import UIKit

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
/// nav bar) to a pushed feature screen. Because hiding the nav bar also disables
/// UIKit's interactive pop gesture, we re-enable the left-edge swipe-to-go-back
/// so it works app-wide even without the native back button.
extension View {
    func keelFeatureScreen() -> some View {
        self
            .navigationBarBackButtonHidden(true)
            .toolbar(.hidden, for: .navigationBar)
            .background(InteractivePopEnabler())
    }
}

/// Re-enables the system's left-edge swipe-to-go-back on screens that hide the
/// navigation bar. SwiftUI disables `interactivePopGestureRecognizer` when the
/// bar is hidden; we reach the enclosing `UINavigationController` and point its
/// pop gesture at one shared, app-lifetime delegate that lets the swipe fire
/// whenever there is a screen to return to (never on the root, so navigation
/// can't get wedged).
///
/// The delegate is a singleton on purpose: `UIGestureRecognizer.delegate` is a
/// weak reference, so a per-screen delegate could be deallocated mid-transition
/// (e.g. swiping back twice quickly), leaving the recognizer with a nil delegate
/// and falling back to its disabled-when-bar-hidden default. A stable singleton
/// never has that gap; each screen just re-points the recognizer at it and
/// refreshes the (shared) navigation controller reference.
private final class InteractivePopDelegate: NSObject, UIGestureRecognizerDelegate {
    static let shared = InteractivePopDelegate()
    weak var navigationController: UINavigationController?

    func gestureRecognizerShouldBegin(_ gesture: UIGestureRecognizer) -> Bool {
        (navigationController?.viewControllers.count ?? 0) > 1
    }
}

private struct InteractivePopEnabler: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController { UIViewController() }

    func updateUIViewController(_ vc: UIViewController, context: Context) {
        // Defer so the view is in the hierarchy and `navigationController` resolves.
        DispatchQueue.main.async {
            guard let nav = vc.navigationController else { return }
            InteractivePopDelegate.shared.navigationController = nav
            nav.interactivePopGestureRecognizer?.delegate = InteractivePopDelegate.shared
            nav.interactivePopGestureRecognizer?.isEnabled = true
        }
    }
}
