package com.therecalibrationyears.keel.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Colour mode, same raw values as iOS `ColourMode` (stored as `keel.colourMode`). */
enum class ColourMode(val raw: String) {
    LIGHT("light"), DARK("dark"), SYSTEM("system");

    fun isDark(systemDark: Boolean) = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemDark
    }

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.raw == raw } ?: SYSTEM
    }
}

/**
 * Whether Keel is showing a dark page: dark mode, or a theme that is always dark
 * (Midnight Garden). The app shell uses it to style the system bars to match.
 */
fun isDarkAppearance(themeId: String, colourMode: ColourMode, systemDark: Boolean) =
    colourMode.isDark(systemDark) || ThemeCatalog.option(themeId).forcesDark

/**
 * Returns to Home (clears the navigation stack). Provided by the app's navigation host;
 * null everywhere else (sheets, onboarding), so the Home button only appears where it
 * works. Port of iOS `\.goHome`.
 */
val LocalGoHome = compositionLocalOf<(() -> Unit)?> { null }

/** Mirrors the "Haptic feedback" setting. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Applies the active Keel theme. Material 3 is only a base: its colour scheme is mapped
 * from the Keel tokens so any Material defaults stay on-brand (error = amber, never red).
 */
@Composable
fun KeelThemeProvider(
    themeId: String = ThemeCatalog.DEFAULT_ID,
    colourMode: ColourMode = ColourMode.SYSTEM,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val isDark = colourMode.isDark(isSystemInDarkTheme())
    val theme = KeelTheme.resolve(themeId, isDark)
    val dark = isDarkAppearance(themeId, colourMode, isSystemInDarkTheme())
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = theme.accent, onPrimary = theme.onFill,
        secondary = theme.plum, onSecondary = theme.onFill,
        tertiary = theme.sage, onTertiary = theme.onFill,
        background = theme.background, onBackground = theme.text,
        surface = theme.card, onSurface = theme.text,
        surfaceVariant = theme.inputBackground, onSurfaceVariant = theme.muted,
        surfaceContainer = theme.card, surfaceContainerHigh = theme.card,
        surfaceContainerHighest = theme.card, surfaceContainerLow = theme.background,
        outline = theme.border, outlineVariant = theme.border,
        error = theme.attention, onError = theme.onFill,
        scrim = theme.scrim,
    )
    CompositionLocalProvider(
        LocalKeelTheme provides theme,
        LocalHapticsEnabled provides hapticsEnabled,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Haptics helpers, gated by the setting (port of iOS `Haptics`). */
object KeelHaptics {
    @Composable
    fun rememberPerformer(): (KeelHapticKind) -> Unit {
        val haptics = LocalHapticFeedback.current
        val enabled = LocalHapticsEnabled.current
        return { kind ->
            if (enabled) {
                haptics.performHapticFeedback(
                    when (kind) {
                        KeelHapticKind.TAP, KeelHapticKind.LIGHT -> HapticFeedbackType.ContextClick
                        KeelHapticKind.SELECTION -> HapticFeedbackType.SegmentTick
                        KeelHapticKind.SUCCESS -> HapticFeedbackType.Confirm
                        KeelHapticKind.ERROR -> HapticFeedbackType.Reject
                    },
                )
            }
        }
    }
}

enum class KeelHapticKind { TAP, LIGHT, SELECTION, SUCCESS, ERROR }
