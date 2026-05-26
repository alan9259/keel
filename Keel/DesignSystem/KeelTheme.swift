import SwiftUI

/// Semantic color roles for the whole app. Every view reads colors from the
/// injected `KeelTheme` (via `@Environment(\.keelTheme)`) rather than hardcoding
/// hexes, so switching between light/dark and the theme palettes is just a matter
/// of injecting a different `KeelTheme`, with no view changes.
///
/// `light` and `dark` are the base palettes; `resolve(themeID:isDark:)` picks the
/// right one for the active theme and colour scheme (see `ThemedRoot`).
struct KeelTheme: Equatable, Sendable {
    var background: Color        // page background (cream)
    var heading: Color           // Cormorant headlines
    var text: Color              // primary body text
    var muted: Color             // secondary/labels
    var accent: Color            // terracotta — primary actions
    var sage: Color              // secondary accent
    var plum: Color
    var card: Color              // card surface
    var inputBackground: Color
    var border: Color            // hairline borders
    var track: Color             // slider/progress track, empty dots
    var toastBackground: Color
    var toastText: Color

    // Convenience translucent fills.
    var sageTint: Color { sage.opacity(0.12) }
    var sageBorder: Color { sage.opacity(0.30) }
    var accentTint: Color { accent.opacity(0.10) }
    var accentBorder: Color { accent.opacity(0.30) }

    static let light = KeelTheme(
        background: Color(hex: 0xFAF7F2),
        heading: Color(hex: 0x5C4F47),
        text: Color(hex: 0x3C3731),
        muted: Color(hex: 0x6B635C),
        accent: Color(hex: 0xC8866B),
        sage: Color(hex: 0xA8B5A4),
        plum: Color(hex: 0x6B5B7B),
        card: .white,
        inputBackground: Color(hex: 0xF3F0EB),
        border: Color(hex: 0x3C3731, alpha: 0.12),
        track: Color(hex: 0xE8E3DA),
        toastBackground: Color(hex: 0x2A2420),
        toastText: Color(hex: 0xEDE8E0)
    )

    /// Warm dark base (Colour Mode → Dark / System-dark).
    static let dark = KeelTheme(
        background: Color(hex: 0x211D19),
        heading: Color(hex: 0xEDE3D6),
        text: Color(hex: 0xE8E1D8),
        muted: Color(hex: 0xA79E95),
        accent: Color(hex: 0xC8866B),
        sage: Color(hex: 0xA8B5A4),
        plum: Color(hex: 0x9B8BB0),
        card: Color(hex: 0x2C2621),
        inputBackground: Color(hex: 0x332C26),
        border: Color(hex: 0xFFFFFF, alpha: 0.10),
        track: Color(hex: 0x3A342E),
        toastBackground: Color(hex: 0xEDE8E0),
        toastText: Color(hex: 0x2A2420)
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
        KeelThemeOption(id: "earthy", name: "Earthy Warm",
                        detail: "Warm cream, sage, terracotta and plum. The default.",
                        price: nil, ownedByDefault: true, forcesDark: false, tag: nil,
                        swatches: [0xF5F0E8, 0x6B8F6B, 0xC4724A, 0x7C5C7C, 0xE8DDD0].map { Color(hex: $0) },
                        accent: Color(hex: 0xC8866B), sage: Color(hex: 0xA8B5A4), plum: Color(hex: 0x6B5B7B)),
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
