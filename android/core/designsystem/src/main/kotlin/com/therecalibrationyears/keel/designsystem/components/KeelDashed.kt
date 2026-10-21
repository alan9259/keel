package com.therecalibrationyears.keel.designsystem.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * A dashed capsule outline (iOS `Capsule().stroke(style: StrokeStyle(lineWidth: 1, dash: [4, 3]))`),
 * used for the "add" pills: Add hours, Add a number, More, Add your own.
 */
fun Modifier.dashedCapsule(colour: Color): Modifier = drawBehind {
    val w = 1.dp.toPx()
    drawRoundRect(
        colour,
        topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(size.height / 2),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}
