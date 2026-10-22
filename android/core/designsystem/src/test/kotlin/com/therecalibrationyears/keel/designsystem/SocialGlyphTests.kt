package com.therecalibrationyears.keel.designsystem

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Instagram and Facebook glyphs are the iOS asset catalog's, single-sourced by this test:
 * if the iOS SVG changes, copy its path data into [KeelIcons].
 */
class SocialGlyphTests {
    private val repoRoot = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "Keel").isDirectory }

    private fun iosPath(imageset: String, file: String): String {
        val svg = File(repoRoot, "Keel/Resources/Assets.xcassets/$imageset.imageset/$file.svg").readText()
        return Regex("""<path[^>]*\sd="([^"]+)"""").find(svg)!!.groupValues[1]
    }

    private fun kotlinPath(name: String): String {
        val src = File(repoRoot, "android/core/designsystem/src/main/kotlin/com/therecalibrationyears/keel/designsystem/KeelIcons.kt").readText()
        return Regex("""icon\(\s*"$name",\s*"([^"]+)"""").find(src)!!.groupValues[1]
    }

    @Test fun glyphsMatchTheIosAssets() {
        assertEquals(iosPath("SocialInstagram", "instagram"), kotlinPath("instagram"))
        assertEquals(iosPath("SocialFacebook", "facebook"), kotlinPath("facebook"))
        // The dark variants are the same shapes in white.
        assertEquals(iosPath("SocialInstagram", "instagram"), iosPath("SocialInstagram", "instagram-dark"))
        assertEquals(iosPath("SocialFacebook", "facebook"), iosPath("SocialFacebook", "facebook-dark"))
    }

    @Test fun glyphsAreBlackOrWhiteNeverThemed() {
        assertTrue(KeelIcons.socialGlyphTint(darkPage = false) == androidx.compose.ui.graphics.Color.Black)
        assertTrue(KeelIcons.socialGlyphTint(darkPage = true) == androidx.compose.ui.graphics.Color.White)
    }
}
