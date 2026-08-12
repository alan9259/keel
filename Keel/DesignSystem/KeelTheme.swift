import SwiftUI

/// Semantic color roles for the whole app. Every view reads colors from the
/// injected `KeelTheme` (via `@Environment(\.keelTheme)`) rather than hardcoding
/// hexes, so switching between light/dark and the theme palettes is just a matter
/// of injecting a different `KeelTheme`, with no view changes.
///
/// `light` and `dark` are the base palettes; `resolve(themeID:isDark:)` picks the
/// right one for the active theme and colour scheme (see `ThemedRoot`).
struct KeelTheme: Equatable, Sendable {
    var background: Color        // page background (off-white)
    var heading: Color           // Literata headlines (charcoal)
    var text: Color              // primary body text (charcoal, never pure black)
    var muted: Color             // secondary/labels
    var accent: Color            // rosewood — primary actions, active states
    var sage: Color              // soft sage — success states ONLY (never identity)
    var plum: Color              // mist blue — secondary / links / TRY-adjacent
    var card: Color              // card surface
    var inputBackground: Color
    var border: Color            // hairline borders
    var track: Color             // slider/progress track, empty dots (warm sand)
    var attention: Color         // amber — warnings & errors. NEVER red (brand rule)
    var toastBackground: Color
    var toastText: Color

    /// Mist blue secondary, named for the brand role (alias of `plum`).
    var secondary: Color { plum }

    // Convenience translucent fills.
    var sageTint: Color { sage.opacity(0.12) }
    var sageBorder: Color { sage.opacity(0.30) }
    var accentTint: Color { accent.opacity(0.10) }
    var accentBorder: Color { accent.opacity(0.30) }
    var attentionTint: Color { attention.opacity(0.12) }

    // Brand palette (guidelines v1.0, section 3).
    static let rosewood = Color(hex: 0x8C4A45)   // brand/primary
    static let mistBlue = Color(hex: 0x426070)   // brand/secondary
    static let warmSand = Color(hex: 0xC4A882)   // accent/sand
    static let softSage = Color(hex: 0x7A9A7E)   // state/positive
    static let offWhite = Color(hex: 0xF7F5F0)   // surface/base
    static let charcoal = Color(hex: 0x444444)   // text/primary
    static let amber    = Color(hex: 0xA9762F)   // state/attention

    static let light = KeelTheme(
        background: offWhite,
        heading: charcoal,
        text: charcoal,
        muted: Color(hex: 0x827A70),
        accent: rosewood,
        sage: softSage,
        plum: mistBlue,
        card: .white,
        inputBackground: Color(hex: 0xEFEBE3),
        border: Color(hex: 0x444444, alpha: 0.12),
        track: Color(hex: 0xE6DFD2),
        attention: amber,
        toastBackground: charcoal,
        toastText: offWhite
    )

    /// Brand-aligned warm dark base (Colour Mode → Dark / System-dark). The
    /// identity colours are lightened just enough to hold contrast on a dark
    /// surface while staying recognisably rosewood, mist and sage.
    static let dark = KeelTheme(
        background: Color(hex: 0x24211E),
        heading: Color(hex: 0xF1ECE4),
        text: Color(hex: 0xE9E3DA),
        muted: Color(hex: 0xA79E93),
        accent: Color(hex: 0xBD7469),   // lightened rosewood
        sage: Color(hex: 0x8FAE92),
        plum: Color(hex: 0x6E93A8),      // lightened mist blue
        card: Color(hex: 0x322D28),
        inputBackground: Color(hex: 0x3A342E),
        border: Color(hex: 0xFFFFFF, alpha: 0.10),
        track: Color(hex: 0x443E37),
        attention: Color(hex: 0xC99348),  // lightened amber
        toastBackground: Color(hex: 0xF1ECE4),
        toastText: Color(hex: 0x24211E)
    )

    /// Resolve the active theme from the selected theme id + dark/light mode.
    /// A theme overrides the accent family; Colour Mode picks the light/dark base
    /// (some themes, e.g. Midnight, force dark).
    static func resolve(themeID: String, isDark: Bool) -> KeelTheme {
        let option = ThemeCatalog.option(themeID)
        var base = (isDark || option.forcesDark) ? dark : light
        base.accent = option.accent
        base.sage = option.sage
        base.plum = option.plum
        return base
    }
}

/// A selectable colour theme (accent family) — some free, some purchasable.
struct KeelThemeOption: Identifiable {
    let id: String
    let name: String
    let detail: String
    let price: String?
    let ownedByDefault: Bool
    let forcesDark: Bool
    var tag: String?
    /// Five representative swatches for the theme cards.
    let swatches: [Color]
    let accent: Color
    let sage: Color
    let plum: Color
}

enum ThemeCatalog {
    static let defaultID = "earthy"

    static let all: [KeelThemeOption] = [
        KeelThemeOption(id: "earthy", name: "Keel",
                        detail: "Rosewood, mist blue and soft sage on off-white. The brand palette.",
                        price: nil, ownedByDefault: true, forcesDark: false, tag: nil,
                        swatches: [0xF7F5F0, 0x8C4A45, 0x426070, 0x7A9A7E, 0xC4A882].map { Color(hex: $0) },
                        accent: KeelTheme.rosewood, sage: KeelTheme.softSage, plum: KeelTheme.mistBlue),
        KeelThemeOption(id: "slate", name: "Slate & Stone",
                        detail: "Cool blue-grey tones with warm stone accents.",
                        price: nil, ownedByDefault: true, forcesDark: false, tag: nil,
                        swatches: [0xF1F3F5, 0x4A6785, 0x8A9AAA, 0xC2956E, 0xE5E8EC].map { Color(hex: $0) },
                        accent: Color(hex: 0x4A6785), sage: Color(hex: 0x8A9AAA), plum: Color(hex: 0xC2956E)),
        KeelThemeOption(id: "forest", name: "Forest Floor",
                        detail: "Deep mossy greens, bark browns and mushroom tones.",
                        price: "£1.99", ownedByDefault: false, forcesDark: false, tag: "Popular",
                        swatches: [0xEEF1EC, 0x3D5A40, 0x8A6A44, 0xC8B59A, 0xD4DDD1].map { Color(hex: $0) },
                        accent: Color(hex: 0x3D5A40), sage: Color(hex: 0x8A6A44), plum: Color(hex: 0xC8B59A)),
        KeelThemeOption(id: "dusk", name: "Dusk",
                        detail: "Dusty rose, lavender and golden amber. Soft and dreamy.",
                        price: "£1.99", ownedByDefault: false, forcesDark: false, tag: nil,
                        swatches: [0xFAF4F0, 0xC49AAD, 0xA89CC0, 0xE8C16A, 0xF0E4D8].map { Color(hex: $0) },
                        accent: Color(hex: 0xC49AAD), sage: Color(hex: 0xA89CC0), plum: Color(hex: 0xE8C16A)),
        KeelThemeOption(id: "midnight", name: "Midnight Garden",
                        detail: "Rich dark backgrounds with botanical accent colours.",
                        price: "£2.99", ownedByDefault: false, forcesDark: true, tag: "Dark",
                        swatches: [0x1A1F2E, 0x4A8C6F, 0xC4724A, 0x9B6BB5, 0x2D3448].map { Color(hex: $0) },
                        accent: Color(hex: 0x4A8C6F), sage: Color(hex: 0x6E8F80), plum: Color(hex: 0x9B6BB5)),
    ]

    static func option(_ id: String) -> KeelThemeOption {
        all.first { $0.id == id } ?? all.first { $0.id == defaultID } ?? all[0]
    }
}

private struct KeelThemeKey: EnvironmentKey {
    static let defaultValue = KeelTheme.light
}

extension EnvironmentValues {
    var keelTheme: KeelTheme {
        get { self[KeelThemeKey.self] }
        set { self[KeelThemeKey.self] = newValue }
    }
}
