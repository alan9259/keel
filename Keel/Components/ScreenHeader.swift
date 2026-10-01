import SwiftUI
import UIKit

/// Back-arrow + Cormorant title header used on pushed feature screens (the new
/// design replaces the native nav bar with this).
struct ScreenHeader: View {
    @Environment(\.keelTheme) private var theme
    @Environment(\.goHome) private var goHome
    let title: String
    /// One consistent screen-title size across the app (matches onboarding).
    var titleSize: CGFloat = 28
    var subtitle: String?
    /// Keep a long title on one line, shrinking it to fit rather than wrapping.
    /// Off by default so titles still grow with Dynamic Type.
    var fitsOneLine: Bool = false
    /// Show the trailing "home" button that returns to the Dashboard. On by
    /// default; it only appears where a home action exists (pushed screens in
    /// MainView's stack), so it is never a dead button in a sheet or onboarding.
    var showsHome: Bool = true
    /// Replaces the default home action, e.g. to confirm before discarding a draft.
    var onHome: (() -> Void)? = nil
    let onBack: () -> Void

    /// The action the home button runs, or nil when it shouldn't be shown.
    private var homeAction: (() -> Void)? {
        guard showsHome, let goHome else { return nil }
        return onHome ?? goHome
    }

    var body: some View {
        HStack(alignment: subtitle == nil ? .center : .top, spacing: 14) {
            Button(action: onBack) {
                Image(systemName: "arrow.left")
                    .font(.system(size: 22, weight: .regular))
                    .foregroundStyle(theme.muted)
                    .headerHitTarget()
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

            if let homeAction {
                Button(action: homeAction) {
                    Image(systemName: "house")
                        .font(.system(size: 22, weight: .regular))
                        .foregroundStyle(theme.muted)
                        .headerHitTarget()
                }
                .accessibilityLabel("Home")
                .accessibilityHint("Returns to the home screen")
            }
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

private extension View {
    /// A 44pt square tap target (DESIGN_PRINCIPLES: minimum 44pt) that still lays out
    /// at the header's original 32pt, so the glyph and title don't move.
    func headerHitTarget() -> some View {
        frame(width: 44, height: 44)
            .contentShape(Rectangle())
            .padding(-6)
    }
}

/// Action that pops the navigation stack back to the Dashboard (home). `MainView`
/// supplies it (resetting its `NavigationPath`). Nil everywhere else, including
/// sheets presented from a pushed screen (which opt out explicitly), so the home
/// button only appears where it works.
private struct GoHomeKey: EnvironmentKey {
    static let defaultValue: (() -> Void)? = nil
}

extension EnvironmentValues {
    var goHome: (() -> Void)? {
        get { self[GoHomeKey.self] }
        set { self[GoHomeKey.self] = newValue }
    }
}
