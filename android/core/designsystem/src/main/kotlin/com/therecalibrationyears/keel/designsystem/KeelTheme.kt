package com.therecalibrationyears.keel.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour roles for the whole app. Port of iOS `KeelTheme`
 * (Keel/DesignSystem/KeelTheme.swift): every screen reads colours from the provided
 * [LocalKeelTheme] rather than hard-coding hexes, so themes and dark mode are just a
 * different [KeelTheme]. Raw colours exist only in this file and in [ThemeCatalog].
 */
@Immutable
data class KeelTheme(
    val background: Color,       // page background (off-white)
    val heading: Color,          // Literata headlines (charcoal)
    val text: Color,             // primary body text (charcoal, never pure black)
    val muted: Color,            // secondary/labels
    val accent: Color,           // rosewood: primary actions, active states
    val sage: Color,             // soft sage: success states ONLY (never identity)
    val plum: Color,             // mist blue: secondary / links
    val card: Color,             // card surface
    val inputBackground: Color,
    val border: Color,           // hairline borders
    val track: Color,            // slider/progress track, empty dots (warm sand)
    val attention: Color,        // amber: warnings and errors. NEVER red (brand rule)
    val sand: Color,             // warm sand, full strength
    val onFill: Color,           // text/icons on a filled accent, sage, attention or scale colour
    val scrim: Color,            // dim layer behind a custom dialog
    val toastBackground: Color,
    val toastText: Color,
) {
    /** Mist blue secondary, named for the brand role (alias of [plum]). */
    val secondary: Color get() = plum

    val sageTint: Color get() = sage.copy(alpha = 0.12f)
    val sageBorder: Color get() = sage.copy(alpha = 0.30f)
    val accentTint: Color get() = accent.copy(alpha = 0.10f)
    val accentBorder: Color get() = accent.copy(alpha = 0.30f)
    val attentionTint: Color get() = attention.copy(alpha = 0.12f)

    /** Between rosewood and amber (SwiftUI `mix(with:by:)`, perceptual). */
    val copper: Color get() = accent.mixOklab(attention, 0.5f)

    /** Sage eased toward the page. */
    val sageSoft: Color get() = sage.mixOklab(background, 0.3f)

    companion object {
        // Brand palette (guidelines v1.0, section 3).
        val Rosewood = Color(0xFF8C4A45)
        val MistBlue = Color(0xFF426070)
        val WarmSand = Color(0xFFC4A882)
        val SoftSage = Color(0xFF7A9A7E)
        val OffWhite = Color(0xFFF7F5F0)
        val Charcoal = Color(0xFF444444)
        val Amber = Color(0xFFA9762F)

        val Light = KeelTheme(
            background = OffWhite,
            heading = Charcoal,
            text = Charcoal,
            muted = Color(0xFF827A70),
            accent = Rosewood,
            sage = SoftSage,
            plum = MistBlue,
            card = Color(0xFFFFFFFF),
            inputBackground = Color(0xFFEFEBE3),
            border = Color(0xFF444444).copy(alpha = 0.12f),
            track = Color(0xFFE6DFD2),
            attention = Amber,
            sand = WarmSand,
            onFill = Color(0xFFFFFFFF),
            scrim = Color(0xFF24211E).copy(alpha = 0.4f),
            toastBackground = Charcoal,
            toastText = OffWhite,
        )

        /** Brand-aligned warm dark base, identity colours lightened for contrast. */
        val Dark = KeelTheme(
            background = Color(0xFF24211E),
            heading = Color(0xFFF1ECE4),
            text = Color(0xFFE9E3DA),
            muted = Color(0xFFA79E93),
            accent = Color(0xFFBD7469),
            sage = Color(0xFF8FAE92),
            plum = Color(0xFF6E93A8),
            card = Color(0xFF322D28),
            inputBackground = Color(0xFF3A342E),
            border = Color(0xFFFFFFFF).copy(alpha = 0.10f),
            track = Color(0xFF443E37),
            attention = Color(0xFFC99348),
            sand = WarmSand,
            onFill = Color(0xFFFFFFFF),
            scrim = Color(0xFF000000).copy(alpha = 0.5f),
            toastBackground = Color(0xFFF1ECE4),
            toastText = Color(0xFF24211E),
        )

        /**
         * The active theme for a theme id and light/dark mode. As on iOS, a theme overrides
         * the accent family (accent, sage, plum) on top of the light or dark base, and some
         * themes (Midnight) force dark.
         */
        fun resolve(themeId: String, isDark: Boolean): KeelTheme {
            val option = ThemeCatalog.option(themeId)
            val base = if (isDark || option.forcesDark) Dark else Light
            return base.copy(accent = option.accent, sage = option.sage, plum = option.plum)
        }
    }
}

/** A selectable colour theme (accent family). Port of iOS `KeelThemeOption`. */
@Immutable
data class KeelThemeOption(
    val id: String,
    val name: String,
    val detail: String,
    val price: String?,
    val ownedByDefault: Boolean,
    val forcesDark: Boolean,
    val tag: String?,
    /** Five representative swatches for the theme cards. */
    val swatches: List<Color>,
    val accent: Color,
    val sage: Color,
    val plum: Color,
)

/** Port of iOS `ThemeCatalog`: same ids, names, copy and colours. */
object ThemeCatalog {
    const val DEFAULT_ID = "earthy"

    private fun swatches(vararg hex: Long) = hex.map { Color(0xFF000000 or it) }

    val all: List<KeelThemeOption> = listOf(
        KeelThemeOption(
            id = "earthy", name = "Keel",
            detail = "Rosewood, mist blue and soft sage on off-white. The brand palette.",
            price = null, ownedByDefault = true, forcesDark = false, tag = null,
            swatches = swatches(0xF7F5F0, 0x8C4A45, 0x426070, 0x7A9A7E, 0xC4A882),
            accent = KeelTheme.Rosewood, sage = KeelTheme.SoftSage, plum = KeelTheme.MistBlue,
        ),
        KeelThemeOption(
            id = "slate", name = "Slate & Stone",
            detail = "Cool blue-grey tones with warm stone accents.",
            price = null, ownedByDefault = true, forcesDark = false, tag = null,
            swatches = swatches(0xF1F3F5, 0x4A6785, 0x8A9AAA, 0xC2956E, 0xE5E8EC),
            accent = Color(0xFF4A6785), sage = Color(0xFF8A9AAA), plum = Color(0xFFC2956E),
        ),
        KeelThemeOption(
            id = "forest", name = "Forest Floor",
            detail = "Deep mossy greens, bark browns and mushroom tones.",
            price = "£1.99", ownedByDefault = false, forcesDark = false, tag = "Popular",
            swatches = swatches(0xEEF1EC, 0x3D5A40, 0x8A6A44, 0xC8B59A, 0xD4DDD1),
            accent = Color(0xFF3D5A40), sage = Color(0xFF8A6A44), plum = Color(0xFFC8B59A),
        ),
        KeelThemeOption(
            id = "dusk", name = "Dusk",
            detail = "Dusty rose, lavender and golden amber. Soft and dreamy.",
            price = "£1.99", ownedByDefault = false, forcesDark = false, tag = null,
            swatches = swatches(0xFAF4F0, 0xC49AAD, 0xA89CC0, 0xE8C16A, 0xF0E4D8),
            accent = Color(0xFFC49AAD), sage = Color(0xFFA89CC0), plum = Color(0xFFE8C16A),
        ),
        KeelThemeOption(
            id = "midnight", name = "Midnight Garden",
            detail = "Rich dark backgrounds with botanical accent colours.",
            price = "£2.99", ownedByDefault = false, forcesDark = true, tag = "Dark",
            swatches = swatches(0x1A1F2E, 0x4A8C6F, 0xC4724A, 0x9B6BB5, 0x2D3448),
            accent = Color(0xFF4A8C6F), sage = Color(0xFF6E8F80), plum = Color(0xFF9B6BB5),
        ),
    )

    fun option(id: String): KeelThemeOption =
        all.firstOrNull { it.id == id } ?: all.firstOrNull { it.id == DEFAULT_ID } ?: all.first()
}

/** The active [KeelTheme]; screens read colours only from here. */
val LocalKeelTheme = staticCompositionLocalOf { KeelTheme.Light }
