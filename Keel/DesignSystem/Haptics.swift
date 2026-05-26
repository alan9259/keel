import UIKit

/// Thin wrapper over UIKit feedback generators. Used on selections, button taps,
/// and completion/error moments per the spec's haptic guidance.
///
/// Haptics do nothing in the Simulator, and every call there logs a harmless
/// "hapticpatternlibrary.plist couldn't be opened" error because the Simulator
/// lacks Core Haptics' system pattern library. We skip them on the Simulator to
/// keep the console clean; on device they behave as before.
enum Haptics {
    #if targetEnvironment(simulator)
    private static let isEnabled = false
    #else
    private static let isEnabled = true
    #endif

    /// Mirrors her "Haptic feedback" setting. `SettingsStore` keeps this in sync,
    /// so turning the setting off genuinely silences haptics.
    static var userEnabled = true

    private static var active: Bool { isEnabled && userEnabled }

    static func tap() {
        guard active else { return }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    static func light() {
        guard active else { return }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    static func selection() {
        guard active else { return }
        UISelectionFeedbackGenerator().selectionChanged()
    }

    static func success() {
        guard active else { return }
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    static func error() {
        guard active else { return }
        UINotificationFeedbackGenerator().notificationOccurred(.error)
    }
}
