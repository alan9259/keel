import SwiftUI

extension Color {
    /// Hex initializer, e.g. `Color(hex: 0xC8866B)`.
    init(hex: UInt, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }
}

/// Keel palette — the v1 brand guidelines (section 3). Most views read colours
/// from the injected `\.keelTheme`; these static tokens back the few call sites
/// (onboarding title/eyebrow, mood dots) that predate the theme and are the
/// light-mode brand values.
enum KeelColor {
    // Brand
    static let cream = Color(hex: 0xF7F5F0)         // surface/base (off-white)
    static let warmGrey = Color(hex: 0x444444)      // text/primary (charcoal)
    static let heading = Color(hex: 0x444444)       // headlines (charcoal)
    static let sage = Color(hex: 0x7A9A7E)          // state/positive
    static let terracotta = Color(hex: 0x8C4A45)    // brand/primary (rosewood)
    static let plum = Color(hex: 0x426070)          // brand/secondary (mist blue)

    // Secondary / utility
    static let lightWarmGrey = Color(hex: 0xE6DFD2)
    static let mutedForeground = Color(hex: 0x827A70)
    static let inputBackground = Color(hex: 0xEFEBE3)
    static let cardBackground = Color.white
    static let border = Color(hex: 0x444444, alpha: 0.12)

    // Mood spectrum — warm and muted, green through amber (no red: the brand rule
    // is that red competes with rosewood).
    static let moodGreat = Color(hex: 0x6E9A76)
    static let moodGood = Color(hex: 0x9BB58F)
    static let moodOkay = Color(hex: 0xC4A882)      // warm sand
    static let moodLow = Color(hex: 0xC0894F)       // ochre
    static let moodDifficult = Color(hex: 0xA9762F) // amber

    // Common translucent fills used across cards
    static let sageTint = Color(hex: 0x7A9A7E, alpha: 0.10)
    static let sageBorder = Color(hex: 0x7A9A7E, alpha: 0.30)
    static let terracottaTint = Color(hex: 0x8C4A45, alpha: 0.10)
    static let terracottaBorder = Color(hex: 0x8C4A45, alpha: 0.30)
    static let warmGreyTint = Color(hex: 0x444444, alpha: 0.10)
}
