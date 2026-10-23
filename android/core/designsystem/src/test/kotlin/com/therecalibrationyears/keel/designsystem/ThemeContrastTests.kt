package com.therecalibrationyears.keel.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android-only (M8): WCAG contrast of the colours Keel sets text in, in every theme, light
 * and dark. The palette is shared with iOS and is the owner's, so this guards what already
 * reads well (body text, headings, toasts) rather than changing colours. The pairs that fall
 * short today (muted text, accent text in dark mode, the Dusk accent) are listed for the
 * owner in docs/android-port-plan.md ("M8 accessibility").
 */
class ThemeContrastTests {
    private fun ratio(fg: Color, bg: Color): Float {
        val a = fg.compositeOver(bg).luminance() + 0.05f
        val b = bg.luminance() + 0.05f
        return maxOf(a, b) / minOf(a, b)
    }

    private val every: List<Pair<String, KeelTheme>> = ThemeCatalog.all.flatMap { option ->
        listOf(false, true).map { dark -> "${option.id} dark=$dark" to KeelTheme.resolve(option.id, dark) }
    }

    @Test fun bodyTextAndHeadingsReadOnEverySurface() {
        every.forEach { (name, t) ->
            listOf("background" to t.background, "card" to t.card, "input" to t.inputBackground).forEach { (surface, bg) ->
                assertTrue("$name text on $surface", ratio(t.text, bg) >= 4.5f)
                assertTrue("$name heading on $surface", ratio(t.heading, bg) >= 4.5f)
            }
        }
    }

    @Test fun toastsRead() {
        every.forEach { (name, t) -> assertTrue("$name toast", ratio(t.toastText, t.toastBackground) >= 4.5f) }
    }

    @Test fun mutedTextIsAtLeastLargeTextContrast() {
        // 3:1 is WCAG's floor for large text and UI parts; small muted captions want 4.5 (owner).
        every.forEach { (name, t) ->
            assertTrue("$name muted on background", ratio(t.muted, t.background) >= 3f)
            assertTrue("$name muted on card", ratio(t.muted, t.card) >= 3f)
            assertTrue("$name muted on input", ratio(t.muted, t.inputBackground) >= 3f)
        }
    }
}
