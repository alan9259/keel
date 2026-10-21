package com.therecalibrationyears.keel.checkin

import android.content.ClipData
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.content.res.ResourcesCompat
import com.therecalibrationyears.keel.data.db.SymptomEntity
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The clip label a dragged symptom carries; the drop target reads its id from the text. */
internal const val SYMPTOM_CLIP_LABEL = "symptom"

/**
 * Starts dragging a symptom chip (iOS `.draggable`) from a long press, with a pill preview
 * like iOS's. Started on the platform directly because the chip's own tap handling claims
 * the touch that Compose's default drag start waits for.
 */
@Composable
internal fun rememberSymptomDrag(): (SymptomEntity) -> Unit {
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val theme = LocalKeelTheme.current
    return remember(view, theme) {
        { symptom ->
            val shadow = PillShadow(
                view, symptom.name,
                textPx = with(density) { 15f * fontScale * this.density },
                padX = with(density) { 14f * this.density }, padY = with(density) { 8f * this.density },
                stroke = with(density) { 1.5f * this.density },
                fill = theme.card.toArgb(), border = theme.accent.toArgb(), text = theme.text.toArgb(),
                typeface = ResourcesCompat.getFont(context, R.font.poppins_regular),
            )
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            view.startDragAndDrop(ClipData.newPlainText(SYMPTOM_CLIP_LABEL, symptom.id), shadow, null, 0)
        }
    }
}

/** The drag preview: the symptom's name in an outlined pill. */
private class PillShadow(
    view: View,
    private val label: String,
    textPx: Float,
    private val padX: Float,
    private val padY: Float,
    private val stroke: Float,
    private val fill: Int,
    private val border: Int,
    text: Int,
    typeface: android.graphics.Typeface?,
) : View.DragShadowBuilder(view) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = textPx; color = text; this.typeface = typeface }
    private val width = textPaint.measureText(label) + padX * 2
    private val height = (textPaint.fontMetrics.let { it.descent - it.ascent }) + padY * 2

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(width.toInt() + stroke.toInt() * 2, height.toInt() + stroke.toInt() * 2)
        outShadowTouchPoint.set(outShadowSize.x / 2, outShadowSize.y / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        val r = RectF(stroke, stroke, width + stroke, height + stroke)
        canvas.drawRoundRect(r, height / 2, height / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        canvas.drawRoundRect(r, height / 2, height / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = border; style = Paint.Style.STROKE; strokeWidth = stroke
        })
        val baseline = r.top + padY - textPaint.fontMetrics.ascent
        canvas.drawText(label, r.left + padX, baseline, textPaint)
    }
}

/**
 * Scrolls the picker while a dragged chip hovers near its top or bottom edge, so a far-off
 * group can be reached (as iOS does while dragging in a scroll view).
 */
internal class DragAutoScroll(private val scroll: ScrollState, private val scope: kotlinx.coroutines.CoroutineScope, private val edgePx: Float) {
    private var top = 0f
    private var bottom = 0f
    private var direction = 0
    private var job: Job? = null

    /** The visible bounds of the scrolling area, in the same coordinates as drag events. */
    fun setBounds(top: Float, bottom: Float) { this.top = top; this.bottom = bottom }

    fun onMoved(y: Float) {
        direction = when {
            y < top + edgePx -> -1
            y > bottom - edgePx -> 1
            else -> 0
        }
        if (direction != 0 && job?.isActive != true) {
            job = scope.launch {
                while (isActive && direction != 0) {
                    scroll.scrollBy(direction * 24f)
                    delay(16)
                }
            }
        }
    }

    fun stop() { direction = 0; job?.cancel() }
}

@Composable
internal fun rememberDragAutoScroll(scroll: ScrollState): DragAutoScroll {
    val scope = rememberCoroutineScope()
    val edge = with(LocalDensity.current) { 72f * density }
    return remember(scroll) { DragAutoScroll(scroll, scope, edge) }
}
