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

/// Keel palette — earthy, muted. Values from KEEL_DESIGN_SPEC.md.
///
/// Defined in code for now so the app is fully self-contained. To add dark mode
/// later, promote these to Asset-Catalog color sets with light/dark variants and
/// swap the references — nothing else needs to change.
enum KeelColor {
    // Primary
    static let cream = Color(hex: 0xFAF7F2)         // background
    static let warmGrey = Color(hex: 0x3C3731)      // body text
    static let heading = Color(hex: 0x5C4F47)       // warm-brown headlines
    static let sage = Color(hex: 0xA8B5A4)
    static let terracotta = Color(hex: 0xC8866B)    // accent
    static let plum = Color(hex: 0x6B5B7B)          // subtle accent

    // Secondary / utility
    static let lightWarmGrey = Color(hex: 0xE8E3DA)
    static let mutedForeground = Color(hex: 0x6B635C)
    static let inputBackground = Color(hex: 0xF3F0EB)
    static let cardBackground = Color.white
    static let border = Color(hex: 0x3C3731, alpha: 0.12)

    // Semantic mood colors
    static let moodGreat = Color(hex: 0x4CAF50)
    static let moodGood = Color(hex: 0x8BC34A)
    static let moodOkay = Color(hex: 0xFFC107)
    static let moodLow = Color(hex: 0xFF9800)
    static let moodDifficult = Color(hex: 0xF44336)

    // Common translucent fills used across cards
    static let sageTint = Color(hex: 0xA8B5A4, alpha: 0.10)
    static let sageBorder = Color(hex: 0xA8B5A4, alpha: 0.30)
    static let terracottaTint = Color(hex: 0xC8866B, alpha: 0.10)
    static let terracottaBorder = Color(hex: 0xC8866B, alpha: 0.30)
    static let warmGreyTint = Color(hex: 0x3C3731, alpha: 0.10)
}
