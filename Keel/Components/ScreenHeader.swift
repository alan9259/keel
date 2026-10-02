import SwiftUI
import UIKit

/// Back-arrow + title + Home header used on pushed feature screens (the new design
/// replaces the native nav bar with this). When she scrolls it out of view, the screen
/// (via `keelFeatureScreen()`) shows a floating header with the same back and Home
/// buttons and the page title, so both stay within reach on long pages.
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

    @State private var scrolledAway = false

    /// The action the home button runs, or nil when it shouldn't be shown.
    private var homeAction: (() -> Void)? {
        guard showsHome, let goHome else { return nil }
        return onHome ?? goHome
    }

    var body: some View {
        row
            // Ask the scroll view itself whether the header is on screen (iOS 18). Measuring
            // the header's position missed changes that came from layout rather than a
            // touch (content loading in and re-anchoring), which left the bar hidden over a
            // scrolled page or showing over a visible header. A header that isn't in a
            // scroll view is never reported, so it never floats.
            .onScrollVisibilityChange(threshold: 0.05) { visible in
                if scrolledAway == visible { scrolledAway = !visible }
            }
            .preference(key: FloatingHeader.Key.self,
                        value: FloatingHeader.State(title: title, scrolledAway: scrolledAway,
                                                    onBack: onBack, onHome: homeAction))
    }

    private var row: some View {
        HStack(alignment: subtitle == nil ? .center : .top, spacing: 14) {
            HeaderIconButton(symbol: "arrow.left", label: "Back", action: onBack)

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
                HeaderIconButton(symbol: "house", label: "Home",
                                 hint: "Returns to the home screen", action: homeAction)
            }
        }
    }
}

/// The back / Home icon button, shared by the inline header and the floating one.
private struct HeaderIconButton: View {
    @Environment(\.keelTheme) private var theme
    let symbol: String
    let label: String
    var hint: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 22, weight: .regular))
                .foregroundStyle(theme.muted)
                .headerHitTarget()
        }
        .accessibilityLabel(label)
        .accessibilityHint(hint ?? "")
    }
}

/// The floating header a feature screen shows once its `ScreenHeader` has scrolled
/// away: back, the page title, and Home, pinned to the top.
enum FloatingHeader {
    struct State {
        let title: String
        let scrolledAway: Bool
        let onBack: () -> Void
        let onHome: (() -> Void)?
    }

    struct Key: PreferenceKey {
        static var defaultValue: State? { nil }
        static func reduce(value: inout State?, nextValue: () -> State?) { value = value ?? nextValue() }
    }

    struct Bar: View {
        @Environment(\.keelTheme) private var theme
        @Environment(\.accessibilityReduceMotion) private var reduceMotion
        let state: State?

        var body: some View {
            let visible = state?.scrolledAway == true
            ZStack(alignment: .top) {
                if visible, let state {
                    HStack(spacing: 14) {
                        HeaderIconButton(symbol: "arrow.left", label: "Back", action: state.onBack)
                        Text(state.title)
                            .font(KeelFont.serif(18, weight: .semibold))
                            .foregroundStyle(theme.heading)
                            .lineLimit(1)
                            .frame(maxWidth: .infinity)
                            .accessibilityAddTraits(.isHeader)
                        if let onHome = state.onHome {
                            HeaderIconButton(symbol: "house", label: "Home",
                                             hint: "Returns to the home screen", action: onHome)
                        } else {
                            Color.clear.frame(width: 32, height: 32) // keeps the title centred
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity)
                    .background(theme.background.ignoresSafeArea(edges: .top))
                    .overlay(alignment: .bottom) { Rectangle().fill(theme.border).frame(height: 1) }
                    .keelCardShadow()
                    .transition(reduceMotion ? .opacity : .move(edge: .top).combined(with: .opacity))
                }
            }
            .animation(.easeOut(duration: 0.2), value: visible)
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
            .overlayPreferenceValue(FloatingHeader.Key.self, alignment: .top) { FloatingHeader.Bar(state: $0) }
            #if DEBUG
            .defaultScrollAnchor(DebugHarness.scrollToBottom ? .bottom : nil)
            #endif
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
