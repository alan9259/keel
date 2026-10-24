package com.therecalibrationyears.keel.designsystem

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Android-only: text looks as it does on iOS. iOS draws an italic request upright for Poppins
 * (no italic face is bundled) and slanted for Literata, so the welcome line "Find your even
 * keel..." and the pattern cards' timeframes are upright, the "Ohhh... that explains a lot."
 * quote slanted.
 */
class KeelFontTests {
    @Test fun poppinsIsNeverSlanted() {
        listOf(KeelFont.body, KeelFont.bodyLarge, KeelFont.caption, KeelFont.button, KeelFont.eyebrow).forEach {
            assertEquals(FontSynthesis.None, it.copy(fontStyle = FontStyle.Italic).fontSynthesis)
        }
    }

    @Test fun literataStillSlants() {
        assertNotEquals(FontSynthesis.None, KeelFont.h3.copy(fontStyle = FontStyle.Italic).fontSynthesis)
    }
}
