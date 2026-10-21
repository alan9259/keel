package com.therecalibrationyears.keel.designsystem

import com.therecalibrationyears.keel.designsystem.components.glyphOnly
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every emoji the iOS app shows has a generated Twemoji vector (plan decision D14), so no
 * Android version falls back to its own emoji font. Fails when a mood pack changes until
 * `android/tools/twemoji_to_vectors.py` is re-run.
 */
class TwemojiVectorsTest {
    private val repo = File(checkNotNull(System.getProperty("keel.repoRoot")))
    private val emoji = Regex("[\\x{1F000}-\\x{1FAFF}\\u2600-\\u27BF\\u2B00-\\u2BFF]")

    private val iosEmoji = listOf("Keel/Models/Mood.swift", "Keel/Models/MoodPack.swift")
        .flatMap { emoji.findAll(File(repo, it).readText()).map { m -> m.value }.toList() }
        .toSet()

    @Test fun everyIosMoodEmojiHasAVector() {
        assertEquals(28, iosEmoji.size)
        val missing = iosEmoji.filter { it.glyphOnly() !in twemojiVectors }
        assertTrue("No vector for $missing: re-run android/tools/twemoji_to_vectors.py", missing.isEmpty())
    }

    @Test fun everyVectorFileExists() {
        val drawables = File(repo, "android/core/designsystem/src/main/res/drawable")
        twemojiVectors.keys.forEach { glyph ->
            val file = File(drawables, "twemoji_${glyph.codePointAt(0).toString(16)}.xml")
            assertTrue("$file missing", file.isFile)
            assertTrue("$file has no paths", "android:pathData" in file.readText())
        }
    }

    @Test fun variationSelectorsAreStripped() {
        assertEquals("⛈", "⛈️".glyphOnly())
        assertTrue("⛈️".glyphOnly() in twemojiVectors)
    }
}
