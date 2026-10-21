package com.therecalibrationyears.keel.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

// Port of KeelTests/ThemeColourTests.swift, plus the iOS-resolved values of the derived
// colours (computed with SwiftUI's Color.mix on macOS, 21 Oct 2026) so Android matches.
class ThemeColourTests {

    private fun hex(c: Color) = "%02X%02X%02X".format(
        (c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())

    /** Each channel within 1 step of 255 of the iOS value (rounding differences only). */
    private fun assertNear(expected: String, actual: Color, label: String) {
        val e = expected.chunked(2).map { it.toInt(16) }
        val a = hex(actual).chunked(2).map { it.toInt(16) }
        assertTrue("$label: expected $expected, got ${hex(actual)}",
            e.zip(a).all { (x, y) -> abs(x - y) <= 1 })
    }

    @Test fun copperAndSoftSageMatchIos() {
        val cases = listOf(
            Triple("earthy", false, "9B603D" to "9EB59F"),
            Triple("earthy", true, "AA6E49" to "5F735F"),
        )
        for ((id, dark, values) in cases) {
            val theme = KeelTheme.resolve(id, dark)
            assertNear(values.first, theme.copper, "copper $id dark=$dark")
            assertNear(values.second, theme.sageSoft, "sageSoft $id dark=$dark")
        }
        assertNear("7B7163", KeelTheme.resolve("slate", false).copper, "copper slate")
        assertNear("AAB5BF", KeelTheme.resolve("slate", false).sageSoft, "sageSoft slate")
        assertNear("75693B", KeelTheme.resolve("forest", false).copper, "copper forest")
        assertNear("B68974", KeelTheme.resolve("dusk", false).copper, "copper dusk")
        assertNear("90915F", KeelTheme.resolve("midnight", false).copper, "copper midnight")
        assertNear("576C60", KeelTheme.resolve("midnight", false).sageSoft, "sageSoft midnight")
    }

    @Test fun logoMistWaterIsDeviceMix() {
        assertEquals("CFD4D4", hex(KeelTheme.OffWhite.mixDevice(KeelTheme.MistBlue, 0.22f)))
    }

    /** As on iOS: a theme overrides the accent family, and Midnight forces dark. */
    @Test fun resolveOverridesAccentFamilyAndMidnightForcesDark() {
        val dark = KeelTheme.resolve("earthy", isDark = true)
        assertEquals(KeelTheme.Dark.background, dark.background)
        assertEquals(KeelTheme.Rosewood, dark.accent)
        assertEquals(KeelTheme.Dark.background, KeelTheme.resolve("midnight", isDark = false).background)
        assertEquals(KeelTheme.Light.background, KeelTheme.resolve("unknown-id", isDark = false).background)
        assertEquals("earthy", ThemeCatalog.option("unknown-id").id)
    }

    /** No red anywhere in the palette (brand rule: red competes with rosewood). */
    @Test fun attentionIsAmberNotRed() {
        for (theme in listOf(KeelTheme.Light, KeelTheme.Dark)) {
            assertTrue(theme.attention.red > theme.attention.blue && theme.attention.green > 0.4f)
        }
    }

    /**
     * Guard (port of the iOS source scan): no raw colours outside the design system's
     * palette files. Screens must read colours from LocalKeelTheme.
     */
    @Test fun noRawColoursOutsideTheDesignSystemPalette() {
        val androidRoot = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        val allowed = setOf("KeelTheme.kt", "ColorMix.kt", "KeelMark.kt", "KeelIcons.kt")
        val offenders = androidRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && "/src/main/" in it.path }
            .filter { it.name !in allowed }
            .filter { file ->
                file.readLines().any { line ->
                    val code = line.substringBefore("//")
                    Regex("""Color\(0x|Color\.(Red|Blue|Green|Black|White|Yellow|Magenta|Cyan|Gray)\b""").containsMatchIn(code)
                }
            }
            .map { it.relativeTo(androidRoot).path }
            .toList()
        assertTrue("Raw colours outside the design system: $offenders", offenders.isEmpty())
    }
}
