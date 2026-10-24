package com.therecalibrationyears.keel.designsystem

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Typography for Keel, port of iOS `KeelFont` (Keel/DesignSystem/KeelFont.swift): Literata
 * (serif, variable weight axis) for titles and headings, Poppins (sans, static Light /
 * Regular / Medium) for everything else. Sizes are in sp so they follow the system font
 * size, the Android counterpart of Dynamic Type.
 */
@OptIn(ExperimentalTextApi::class)
object KeelFont {
    private fun literata(weight: FontWeight) = Font(
        R.font.literata, weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

    /** Literata at every weight the app uses (the variable font interpolates the axis). */
    val SerifFamily = FontFamily(
        literata(FontWeight.Normal), literata(FontWeight.Medium),
        literata(FontWeight.SemiBold), literata(FontWeight.Bold),
    )

    /** Poppins: medium is used for any semibold or bold weight, as on iOS. */
    val SansFamily = FontFamily(
        Font(R.font.poppins_light, FontWeight.Light),
        Font(R.font.poppins_regular, FontWeight.Normal),
        Font(R.font.poppins_medium, FontWeight.Medium),
        Font(R.font.poppins_medium, FontWeight.SemiBold),
        Font(R.font.poppins_medium, FontWeight.Bold),
    )

    /**
     * Each face's natural line height on iOS (hhea ascender + descender + line gap). Android
     * leaves out the line gap, so lines would sit tighter than on iOS without this. In `em`,
     * so it follows the size when a title shrinks to fit.
     */
    private val serifLineHeight = 1.485.em
    private val sansLineHeight = 1.5.em
    private val lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    fun serif(size: TextUnit, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
        fontFamily = SerifFamily, fontWeight = weight, fontSize = size,
        lineHeight = serifLineHeight, lineHeightStyle = lineHeightStyle,
    )

    /**
     * Poppins is never slanted: Keel bundles no Poppins italic, and iOS draws an italic request
     * for it upright (it slants only Literata), so Android doesn't fake one either.
     */
    fun sans(size: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = SansFamily, fontWeight = weight, fontSize = size,
        lineHeight = sansLineHeight, lineHeightStyle = lineHeightStyle,
        fontSynthesis = FontSynthesis.None,
    )

    // Type scale (same sizes as iOS).
    val displayLarge = serif(56.sp)
    val h1 = serif(34.sp)
    val h2 = serif(28.sp)
    val h3 = serif(22.sp, FontWeight.Medium)
    val bodyLarge = sans(17.sp)
    val body = sans(15.sp)
    val caption = sans(13.sp)
    val button = sans(17.sp, FontWeight.SemiBold)
    val eyebrow = sans(11.sp, FontWeight.SemiBold)
}

/**
 * Extra space between lines, as SwiftUI `.lineSpacing(extra)`: the natural line height plus
 * [extra] (in sp, so it grows with the font-size setting like the text does).
 */
fun TextStyle.lineSpacing(extra: Float): TextStyle {
    val natural = if (lineHeight.isEm) fontSize.value * lineHeight.value else fontSize.value * 1.5f
    return copy(lineHeight = (natural + extra).sp)
}
