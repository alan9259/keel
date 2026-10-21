package com.therecalibrationyears.keel.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therecalibrationyears.keel.designsystem.KeelDateFormats
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.domain.VitalTrend
import com.therecalibrationyears.keel.domain.parity.formatOneDecimal
import java.time.ZoneId

/** A point on the chart, in pixels. */
data class ChartPoint(val x: Float, val y: Float)

/** One cubic piece of the curve: two control points and where it ends. */
data class CurveSegment(val control1: ChartPoint, val control2: ChartPoint, val end: ChartPoint)

/** The smooth line through a chart's points (port of iOS `VitalLineChart.smoothPath`), pure. */
object VitalChartPath {
    /**
     * A Catmull-Rom curve as cubic Béziers, so the line passes through every point with no
     * sharp zig-zags. Two points are a straight segment; none is nothing.
     */
    fun segments(points: List<ChartPoint>): List<CurveSegment> {
        if (points.size < 2) return emptyList()
        if (points.size == 2) return listOf(CurveSegment(points[0], points[1], points[1]))
        return (0 until points.size - 1).map { i ->
            val p0 = points[maxOf(i - 1, 0)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[minOf(i + 2, points.size - 1)]
            CurveSegment(
                ChartPoint(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6),
                ChartPoint(p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6),
                p2,
            )
        }
    }
}

/**
 * A soft line chart for one imported vital over recent days (port of iOS `VitalLineChart`): a
 * smooth curve with a gentle area fill, the latest reading marked, and quiet min, max and date
 * labels. Only her own data; hidden from TalkBack, as the row around it says what it shows.
 */
@Composable
fun VitalLineChart(points: List<VitalTrend.Point>, color: Color, modifier: Modifier = Modifier, showsScale: Boolean = true) {
    val theme = LocalKeelTheme.current
    val context = LocalContext.current
    val formats = remember { KeelDateFormats(context, zone = ZoneId.systemDefault()) }
    val sorted = points.sortedBy { it.day }
    val values = sorted.map { it.value }
    val lo = values.minOrNull() ?: 0.0
    val hi = values.maxOrNull() ?: 1.0
    val chartHeight = 92.dp
    val inset = 12.dp
    val label = KeelFont.sans(10.sp).copy(fontFeatureSettings = "tnum")

    Row(modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showsScale) {
            Column(Modifier.width(26.dp).height(chartHeight).padding(vertical = inset), horizontalAlignment = Alignment.End) {
                Text(axisLabel(hi), style = label.copy(textAlign = TextAlign.End), color = theme.muted.copy(alpha = 0.75f))
                Spacer(Modifier.weight(1f))
                Text(axisLabel(lo), style = label.copy(textAlign = TextAlign.End), color = theme.muted.copy(alpha = 0.75f))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
                if (values.size < 2) return@Canvas
                val range = hi - lo
                val v = inset.toPx()
                val h = size.height - v * 2
                val pts = values.indices.map { i ->
                    val norm = if (range == 0.0) 0.5 else (values[i] - lo) / range
                    ChartPoint(size.width * i / (values.size - 1), (v + h * (1 - norm)).toFloat())
                }
                val curve = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    VitalChartPath.segments(pts).forEach { s -> cubicTo(s.control1.x, s.control1.y, s.control2.x, s.control2.y, s.end.x, s.end.y) }
                }
                // A gentle area under the curve.
                val area = Path().apply {
                    addPath(curve)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.02f)), startY = 0f, endY = size.height))
                drawPath(curve, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                // The latest reading: one calm marker, ringed in the card colour.
                val end = Offset(pts.last().x, pts.last().y)
                drawCircle(color.copy(alpha = 0.16f), radius = 6.dp.toPx(), center = end)
                drawCircle(theme.card, radius = 3.5.dp.toPx() + 1.dp.toPx(), center = end)
                drawCircle(color, radius = 3.5.dp.toPx(), center = end)
            }
            val first = sorted.firstOrNull()?.day
            val last = sorted.lastOrNull()?.day
            if (first != null && last != null && first != last) {
                Row(Modifier.fillMaxWidth()) {
                    Text(formats.monthDay(first), style = KeelFont.sans(10.sp), color = theme.muted.copy(alpha = 0.7f))
                    Spacer(Modifier.weight(1f))
                    Text(formats.monthDay(last), style = KeelFont.sans(10.sp), color = theme.muted.copy(alpha = 0.7f))
                }
            }
        }
    }
}

private fun axisLabel(value: Double): String =
    if (value == Math.rint(value)) value.toLong().toString() else formatOneDecimal(value)
