package com.therecalibrationyears.keel.cycle

import com.therecalibrationyears.keel.domain.CycleDay
import com.therecalibrationyears.keel.domain.CycleDayMark
import com.therecalibrationyears.keel.domain.CycleEntryType
import com.therecalibrationyears.keel.domain.FlowLevel
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Cycle screen's rules (iOS `CycleTrackingView` derived values), on a fixed "today". */
class CycleModelTests {
    private val today = LocalDate.of(2026, 10, 21)
    private val month = YearMonth.of(2026, 10)
    private fun day(offset: Int) = today.plusDays(offset.toLong())
    private fun logged(offset: Int, level: FlowLevel = FlowLevel.MEDIUM, type: CycleEntryType = CycleEntryType.PERIOD_START) =
        day(offset) to CycleDay(type, level)
    private fun model(vararg days: Pair<LocalDate, CycleDay>, imported: Map<LocalDate, FlowLevel> = emptyMap(), shown: YearMonth = month) =
        CycleModel(CycleData(days.toList(), imported), today, shown)
    private val words: (LocalDate) -> String = { "${it.dayOfMonth}/${it.monthValue}" }

    @Test fun emptyShowsARecentWeekAndAskForAFirstPeriod() {
        val m = model()
        assertFalse(m.hasAnyPeriod)
        assertEquals((6 downTo 0).map { day(-it) }, m.railDays)
        assertEquals("Log a period to start your cycle.", m.statusLine(words))
        assertNull(m.historyLine)
    }

    /** The iOS -uitSeedCycle shape: 28, 29, 27 days, then a period starting three days ago. */
    @Test fun regularCyclesGiveTheDayAndAnEstimatedWindow() {
        val m = model(
            logged(-84), logged(-56), logged(-27),
            logged(-3, FlowLevel.HEAVY), logged(-2), logged(-1, FlowLevel.LIGHT), logged(0, FlowLevel.SPOTTING),
        )
        // Starts -84, -56, -27, -3: lengths 28, 29, 24 (spotting today doesn't start a cycle).
        assertEquals(listOf(28, 29, 24), m.stats.cycleLengths)
        // From the start on 18 Oct: 24 to 29 days on.
        assertEquals("Day 4 of your cycle. Next period likely 11/11 to 16/11.", m.statusLine(words))
        assertEquals("Lately your cycles have run 24 to 29 days.", m.historyLine)
        assertEquals(listOf(24, 29, 28), m.recentLengths) // newest first
        assertEquals(day(-3), m.railDays.first())
        assertEquals(day(-3 + 29 + 1), m.railDays.last()) // a day past the estimate
        assertTrue(m.inEstimate(day(-3 + 24)))
        assertFalse(m.inEstimate(day(-4))) // never a past day
    }

    @Test fun variedCyclesSaySoInsteadOfEstimating() {
        val m = model(logged(-90), logged(-68), logged(-28), logged(-3)) // 22, 40, 25
        assertEquals("Day 4 of your cycle. Your recent cycles have varied, so no estimate yet.", m.statusLine(words))
        assertNull(m.estimate)
    }

    @Test fun oneLengthIsAboutNDays() {
        assertEquals("About 28 days between periods.", model(logged(-56), logged(-28), logged(0)).historyLine)
    }

    @Test fun herOwnDayWinsAndImportedDaysOnlyCountWhenOnScreen() {
        val m = model(logged(-1, FlowLevel.HEAVY), imported = mapOf(day(-1) to FlowLevel.LIGHT, day(-2) to FlowLevel.MEDIUM))
        assertEquals(CycleDayMark(FlowLevel.HEAVY, imported = false), m.marks[day(-1)])
        assertEquals(CycleDayMark(FlowLevel.MEDIUM, imported = true), m.marks[day(-2)])
        assertTrue(m.hasAnyImported)
        // An imported day far from the timeline and the month shown isn't on screen.
        val far = model(logged(-1), imported = mapOf(day(-200) to FlowLevel.LIGHT))
        assertFalse(far.hasAnyImported)
        assertTrue(model(logged(-1), imported = mapOf(day(-200) to FlowLevel.LIGHT), shown = YearMonth.from(day(-200))).hasAnyImported)
    }

    @Test fun periodEndMarkersAreNotShownAsDays() {
        assertNull(model(logged(-1, FlowLevel.UNSPECIFIED, CycleEntryType.PERIOD_END)).marks[day(-1)])
    }

    @Test fun labelsReadTheDayItsFlowAndWhereItCameFrom() {
        val m = model(logged(-1, FlowLevel.HEAVY), imported = mapOf(day(-2) to FlowLevel.LIGHT))
        assertEquals("Tuesday 20 October, Heavy", m.accessibilityLabel(day(-1), "Tuesday 20 October", inEstimate = false))
        assertEquals("Monday 19 October, Light, from Health Connect", m.accessibilityLabel(day(-2), "Monday 19 October", false))
        assertEquals("Wednesday 21 October, today", m.accessibilityLabel(today, "Wednesday 21 October", false))
        assertEquals("Friday 23 October, estimated period", m.accessibilityLabel(day(2), "Friday 23 October", true))
        assertEquals("Friday 23 October", m.accessibilityLabel(day(2), "Friday 23 October", false))
    }

    @Test fun fillsFollowHeavinessAndImportedDaysAreLighter() {
        assertEquals(0.38f, CycleModel.fillOpacity(FlowLevel.SPOTTING))
        assertEquals(0.9f, CycleModel.fillOpacity(FlowLevel.UNSPECIFIED))
        assertEquals(1f * 0.35f, CycleModel.fillOpacity(CycleDayMark(FlowLevel.HEAVY, imported = true)))
        assertTrue(CycleModel.isDarkFill(CycleDayMark(FlowLevel.MEDIUM, imported = false)))
        assertFalse(CycleModel.isDarkFill(CycleDayMark(FlowLevel.LIGHT, imported = false)))
    }

    @Test fun theMonthGridStopsAtThisMonth() {
        assertTrue(model().isCurrentOrFutureMonth)
        assertFalse(model(shown = month.minusMonths(1)).isCurrentOrFutureMonth)
    }

    @Test fun onlyTheLast400DaysCount() {
        assertEquals(listOf(day(-28), day(0)), model(logged(-500), logged(-28), logged(0)).stats.starts)
    }
}
