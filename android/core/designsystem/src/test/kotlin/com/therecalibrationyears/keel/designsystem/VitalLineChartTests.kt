package com.therecalibrationyears.keel.designsystem

import com.therecalibrationyears.keel.designsystem.components.ChartPoint
import com.therecalibrationyears.keel.designsystem.components.VitalChartPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of KeelTests/VitalLineChartTests.swift: the curve builder behind the vitals chart. */
class VitalLineChartTests {
    @Test fun emptyInputProducesEmptyPath() = assertTrue(VitalChartPath.segments(emptyList()).isEmpty())

    @Test fun twoPointsFallBackToALine() {
        val segments = VitalChartPath.segments(listOf(ChartPoint(0f, 0f), ChartPoint(10f, 5f)))
        assertEquals(1, segments.size)
        assertEquals(ChartPoint(10f, 5f), segments.single().end)
    }

    @Test fun manyPointsProduceACurveThroughTheEnds() {
        val pts = (0 until 8).map { ChartPoint(it * 10f, (it % 2) * 20f) }
        val segments = VitalChartPath.segments(pts)
        assertEquals(7, segments.size)
        // A Catmull-Rom curve interpolates its points: every segment ends on the next one.
        assertEquals(pts.drop(1), segments.map { it.end })
        assertEquals(70f, segments.last().end.x)
    }
}
