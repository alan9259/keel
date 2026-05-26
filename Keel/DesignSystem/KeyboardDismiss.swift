import SwiftUI
import UIKit

/// App-wide "tap outside to dismiss the keyboard". A single tap recogniser on the
/// window ends editing. It doesn't cancel touches (so buttons and menus still fire
/// on the same tap) and ignores taps on the field being edited (so tapping to move
/// the cursor doesn't close the keyboard). Number pads especially have no return
/// key, so this is the only way to dismiss them.
final class KeyboardDismisser: NSObject, UIGestureRecognizerDelegate {
    static let shared = KeyboardDismisser()

    static func install(on window: UIWindow) {
        let already = window.gestureRecognizers?.contains { $0.name == "keel.keyboardDismiss" } ?? false
        guard !already else { return }
        let tap = UITapGestureRecognizer(target: shared, action: #selector(dismissKeyboard))
        tap.name = "keel.keyboardDismiss"
        tap.cancelsTouchesInView = false
        tap.delegate = shared
        window.addGestureRecognizer(tap)
    }

    @objc private func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }

    func gestureRecognizer(_ g: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

    func gestureRecognizer(_ g: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        // Don't steal a tap on the field being edited (she may be repositioning the
        // cursor); dismiss for taps anywhere else.
        var view = touch.view
        while let v = view {
            if (v is UITextField || v is UITextView), v.isFirstResponder { return false }
            view = v.superview
        }
        return true
    }
}

/// Installs the window tap recogniser from SwiftUI. One install on the app's window
/// also covers sheets (on iPhone they present in the same window).
private struct KeyboardDismissInstaller: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        let v = UIView(frame: .zero)
        DispatchQueue.main.async { if let w = v.window { KeyboardDismisser.install(on: w) } }
        return v
    }
    func updateUIView(_ uiView: UIView, context: Context) {
        DispatchQueue.main.async { if let w = uiView.window { KeyboardDismisser.install(on: w) } }
    }
}

extension View {
    /// Enable app-wide tap-outside-to-dismiss for the keyboard. Apply once at the root.
    func dismissesKeyboardOnTapOutside() -> some View {
        background(KeyboardDismissInstaller())
    }
}
