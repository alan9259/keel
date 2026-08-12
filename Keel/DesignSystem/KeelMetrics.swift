import SwiftUI

/// 8pt spacing scale (KEEL_DESIGN_SPEC.md).
enum Spacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 16
    static let lg: CGFloat = 24
    static let xl: CGFloat = 32
    static let xxl: CGFloat = 48
    static let xxxl: CGFloat = 64

    /// Standard horizontal screen padding.
    static let screenH: CGFloat = 24
}

/// Corner radii.
enum Radius {
    static let sm: CGFloat = 8
    static let input: CGFloat = 12
    static let md: CGFloat = 14
    static let card: CGFloat = 18    // cards & primary buttons (new design)
    static let lg: CGFloat = 16
    static let xl: CGFloat = 20
    static let pill: CGFloat = 999
    static let round: CGFloat = 999
}

extension View {
    /// Standard card elevation. (SwiftUI's shadow radius ≈ blur / 2.)
    func keelCardShadow() -> some View {
        shadow(color: Color(hex: 0x3C3731, alpha: 0.08), radius: 6, x: 0, y: 4)
    }

    /// Floating-action-button elevation.
    func keelFabShadow() -> some View {
        shadow(color: Color(hex: 0x8C4A45, alpha: 0.30), radius: 12, x: 0, y: 8)
    }

    /// Cream page background that fills the safe area.
    func keelScreenBackground() -> some View {
        background(KeelColor.cream.ignoresSafeArea())
    }

    /// Minimum 44pt tap target (iOS HIG).
    func keelHitTarget() -> some View {
        frame(minWidth: 44, minHeight: 44)
    }
}
