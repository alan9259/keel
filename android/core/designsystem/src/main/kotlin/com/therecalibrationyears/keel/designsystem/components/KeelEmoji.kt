package com.therecalibrationyears.keel.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therecalibrationyears.keel.designsystem.twemojiVectors

/** Strips emoji variation selectors (FE0F / FE0E) so glyphs centre correctly (iOS `glyphOnly`). */
fun String.glyphOnly(): String = filterNot { it == '️' || it == '︎' }

/**
 * A single emoji at a fixed size (port of iOS `EmojiGlyph`; emoji don't scale with the
 * font-size setting, as on iOS). Drawn from Twemoji vector drawables rather than the
 * Twemoji colour font, which Android 9 to 12 render blank (plan decision D14).
 */
@Composable
fun EmojiGlyph(emoji: String, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val vector = twemojiVectors[emoji.glyphOnly()]
    if (vector != null) {
        Image(painterResource(vector), contentDescription = null, modifier = modifier.size(size))
    } else {
        // Never expected: TwemojiVectorsTest checks every iOS mood emoji has a vector.
        val fontScale = LocalDensity.current.fontScale
        Text(emoji.glyphOnly(), modifier = modifier, style = TextStyle(fontSize = (size.value / fontScale).sp))
    }
}
