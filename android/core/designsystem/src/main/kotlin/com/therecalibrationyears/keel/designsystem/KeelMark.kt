package com.therecalibrationyears.keel.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp

/**
 * The Keel brand mark, v2 (port of iOS `KeelMark`, Keel/DesignSystem/KeelMark.swift): a
 * solid off-white disc holding mist-tinted water settled level, the upright K drawn through
 * it in the tile colour, on a rosewood tile. Same paths in 100-unit guideline space, mark at
 * 84% of the tile with an 8-unit margin. The brand's own colours, deliberately not the theme.
 */
@Composable
fun KeelMark(
    modifier: Modifier = Modifier,
    tile: Color = KeelTheme.Rosewood,
    disc: Color = KeelTheme.OffWhite,
    water: Color = MistWater,
    /** Tile corner radius; null rounds 22% of the side. */
    cornerRadius: Dp? = null,
) {
    Canvas(modifier.aspectRatio(1f).clearAndSetSemantics { }) {
        drawKeelMark(tile, disc, water, cornerRadius?.toPx())
    }
}

/** Draws the mark filling this scope (square); [cornerRadiusPx] null rounds 22% of the side. */
internal fun DrawScope.drawKeelMark(
    tile: Color = KeelTheme.Rosewood,
    disc: Color = KeelTheme.OffWhite,
    water: Color = MistWater,
    cornerRadiusPx: Float? = null,
) {
    val u = size.width / 100f
    fun p(x: Float, y: Float) = Offset((8 + 0.84f * x) * u, (8 + 0.84f * y) * u)
    val radius = cornerRadiusPx ?: (size.width * 0.22f)
    val tilePath = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
    }
    clipPath(tilePath) {
        drawRect(tile)
        val r = 36 * 0.84f * u
        val c = p(50f, 50f)
        val discPath = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
        drawPath(discPath, disc)
        clipPath(discPath) {
            val w = Path().apply {
                p(6f, 62f).let { moveTo(it.x, it.y) }
                val a1 = p(22f, 55f); val a2 = p(32f, 68f); val a3 = p(50f, 63f)
                cubicTo(a1.x, a1.y, a2.x, a2.y, a3.x, a3.y)
                val b1 = p(66f, 58.5f); val b2 = p(78f, 63f); val b3 = p(96f, 59f)
                cubicTo(b1.x, b1.y, b2.x, b2.y, b3.x, b3.y)
                p(96f, 96f).let { lineTo(it.x, it.y) }
                p(6f, 96f).let { lineTo(it.x, it.y) }
                close()
            }
            drawPath(w, water)
        }
        val k = Path().apply {
            p(38f, 30f).let { moveTo(it.x, it.y) }; p(38f, 70f).let { lineTo(it.x, it.y) }
            p(64f, 30f).let { moveTo(it.x, it.y) }
            val u1 = p(58f, 36f); val u2 = p(53f, 41f); val u3 = p(47f, 47f)
            cubicTo(u1.x, u1.y, u2.x, u2.y, u3.x, u3.y)
            p(47f, 47f).let { moveTo(it.x, it.y) }
            val l1 = p(55f, 53f); val l2 = p(61f, 60f); val l3 = p(64f, 70f)
            cubicTo(l1.x, l1.y, l2.x, l2.y, l3.x, l3.y)
        }
        drawPath(k, tile, style = Stroke(width = 11 * 0.84f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The water: mist blue at 22% over off-white, mixed in device space (#CFD4D4). */
val MistWater: Color = KeelTheme.OffWhite.mixDevice(KeelTheme.MistBlue, 0.22f)

