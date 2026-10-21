package com.therecalibrationyears.keel.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port of KeelTests/CycleStatsTests.swift: the cycle arithmetic behind the timeline, history
 * and next-period estimate. Days are `LocalDate`s, so nothing depends on a time zone.
 */
class CycleStatsTests {
    private val base = LocalDate.of(2020, 9, 13)
    private fun d(offset: Int) = base.plusDays(offset.toLong())
    private fun stats(offsets: List<Int>) = CycleStats(offsets.map(::d))

    @Test fun periodStartsCollapseConsecutiveDays() {
        // Days 0,1,2 are one period; 28,29 the next → starts at 0 and 28.
        assertEquals(listOf(d(0), d(28)), CycleStats.periodStarts(listOf(0, 1, 2, 28, 29).map(::d)))
    }

    @Test fun cycleLengths() {
        assertEquals(listOf(28, 28), stats(listOf(0, 28, 56)).cycleLengths)
        assertEquals(listOf(24, 31), stats(listOf(0, 24, 55)).cycleLengths)
    }

    @Test fun typicalRange() {
        assertEquals(24..31, stats(listOf(0, 24, 55, 79)).typicalRange) // lengths 24, 31, 24
        assertNull(stats(listOf(0)).typicalRange) // no full cycle yet
    }

    @Test fun cycleDay() {
        val s = stats(listOf(0, 28)) // last start at day 28
        assertEquals(1, s.cycleDay(d(28)))
        assertEquals(18, s.cycleDay(d(45)))
        assertNull(s.cycleDay(d(27))) // before the last start
    }

    @Test fun estimateWhenRecentCyclesAreConsistent() {
        // lengths 27, 28, 29 → spread 2 ≤ 7 → estimate from the last start (day 84).
        val s = stats(listOf(0, 27, 55, 84))
        assertTrue(s.canEstimate)
        val window = s.estimatedWindow()!!
        assertEquals(d(84 + 27), window.start)
        assertEquals(d(84 + 29), window.endInclusive)
    }

    @Test fun noEstimateWhenTooVariable() {
        // lengths 22, 40, 25 → spread 18 > 7 → too variable to forecast.
        val s = stats(listOf(0, 22, 62, 87))
        assertFalse(s.canEstimate)
        assertNull(s.estimatedWindow())
        assertEquals(22..40, s.typicalRange) // the history is still honestly available
    }

    @Test fun noEstimateWithFewerThanThreeCycles() {
        assertFalse(stats(listOf(0, 28)).canEstimate)
        assertFalse(stats(listOf(0, 28, 56)).canEstimate)
        assertFalse(stats(emptyList()).canEstimate)
    }

    @Test fun phaseAnchorsToCycleStartNotTheLastPeriodDay() {
        val s = stats(listOf(0, 28, 56, 84))
        assertEquals(CyclePhase.MENSTRUAL, s.phase(d(84)))
        assertEquals(CyclePhase.FOLLICULAR, s.phase(d(84 + 12)))
        assertEquals(CyclePhase.LUTEAL, s.phase(d(84 + 17))) // not ovulation (the bug it fixes)
    }

    @Test fun phaseUnknownBeforeAnyStartAndWhenOverdue() {
        val s = stats(listOf(0, 28, 56, 84))
        assertEquals(CyclePhase.LUTEAL, s.phase(d(83))) // still the previous cycle's luteal
        assertEquals(CyclePhase.UNKNOWN, s.phase(d(-1)))
        assertEquals(CyclePhase.UNKNOWN, s.phase(d(84 + 60)))
    }

    @Test fun phaseScalesToHerCycleLength() {
        assertEquals(CyclePhase.LUTEAL, stats(listOf(0, 24, 48, 72)).phase(d(72 + 12))) // short cycles
        assertEquals(CyclePhase.FOLLICULAR, stats(listOf(0, 32, 64, 96)).phase(d(96 + 12))) // long cycles
    }

    @Test fun currentCycleStartIgnoresOlderCyclesForToday() {
        val s = stats(listOf(0, 28, 56, 84))
        assertEquals(d(84), s.currentCycleStart(d(90)))
        assertEquals(d(84), s.currentCycleStart(d(200)))
    }

    /** Port of testEstimatedPhaseBoundaries (KeelTests/CycleRepositoryTests.swift), on one start. */
    @Test fun phaseBoundariesWithASingleStart() {
        val s = stats(listOf(0))
        mapOf(
            0 to CyclePhase.MENSTRUAL, 4 to CyclePhase.MENSTRUAL, 5 to CyclePhase.FOLLICULAR, 12 to CyclePhase.FOLLICULAR,
            13 to CyclePhase.OVULATION, 15 to CyclePhase.OVULATION, 16 to CyclePhase.LUTEAL, 39 to CyclePhase.LUTEAL,
            40 to CyclePhase.UNKNOWN,
        ).forEach { (day, phase) -> assertEquals("day $day", phase, s.phase(d(day))) }
    }

    @Test fun spottingAndPeriodEndNeverStartACycle() {
        val days = listOf(
            d(0) to CycleDay(CycleEntryType.PERIOD_START, FlowLevel.MEDIUM),
            d(14) to CycleDay(CycleEntryType.PERIOD_START, FlowLevel.SPOTTING),
            d(20) to CycleDay(CycleEntryType.PERIOD_END, FlowLevel.UNSPECIFIED),
            d(28) to CycleDay(CycleEntryType.PERIOD_START, FlowLevel.UNSPECIFIED),
        )
        assertEquals(listOf(d(0), d(28)), CycleStats.fromEntries(days).starts)
    }

    @Test fun flowLevelsReadAsIos() {
        assertEquals(FlowLevel.UNSPECIFIED, FlowLevel.fromRaw(null)) // legacy period day, no level
        assertEquals("Period", FlowLevel.UNSPECIFIED.label)
        assertEquals(listOf("spotting", "light", "medium", "heavy"), FlowLevel.loggingOrder.map { it.raw })
        assertEquals(CycleEntryType.FLOW, CycleEntryType.fromRaw("unknown"))
    }
}

/** Port of KeelTests/CycleTimelineTests.swift: the timeline's day window, including empty. */
class CycleTimelineTests {
    private val today = LocalDate.of(2026, 8, 25)

    @Test fun emptyStateWindowIsARecentWeekEndingToday() {
        val bounds = CycleTimeline.railBounds(lastStart = null, estimateEnd = null, today = today)
        assertEquals(today.minusDays(6), bounds.start)
        assertEquals(today, bounds.endInclusive)
        assertEquals(7, CycleTimeline.days(bounds).size) // seven real, tappable days
    }

    @Test fun withDataSpansCycleStartToTheEstimate() {
        val start = LocalDate.of(2026, 8, 5)
        val estimateEnd = LocalDate.of(2026, 9, 2)
        val bounds = CycleTimeline.railBounds(start, estimateEnd, today)
        assertEquals(start, bounds.start)
        assertEquals(estimateEnd.plusDays(1), bounds.endInclusive)
    }

    @Test fun withDataButNoEstimateEndsJustAfterToday() {
        val bounds = CycleTimeline.railBounds(LocalDate.of(2026, 8, 20), null, today)
        assertEquals(today.plusDays(4), bounds.endInclusive) // max(today, today+3) + 1
    }

    /** Port of testHerOwnEntryWinsAndTheDayIsShownOnce (KeelTests/AppleHealthPeriodsTests.swift). */
    @Test fun herOwnEntryWinsAndTheDayIsShownOnce() {
        val shared = today
        val importedOnly = today.minusDays(1)
        val hersOnly = today.minusDays(2)
        val marks = CycleDayMark.merge(
            manual = mapOf(shared to FlowLevel.HEAVY, hersOnly to FlowLevel.LIGHT),
            imported = mapOf(shared to FlowLevel.LIGHT, importedOnly to FlowLevel.MEDIUM),
        )
        assertEquals(3, marks.size)
        assertEquals(CycleDayMark(FlowLevel.HEAVY, imported = false), marks[shared])
        assertEquals(CycleDayMark(FlowLevel.MEDIUM, imported = true), marks[importedOnly])
        assertEquals(CycleDayMark(FlowLevel.LIGHT, imported = false), marks[hersOnly])
    }
}

/**
 * Port of KeelTests/PatternEngineTests.swift: single measures only (her cycle-length range).
 * V1 never links two measures in a sentence.
 */
class PatternEngineTests {
    private val base = LocalDate.of(2020, 9, 13)
    private fun day(offset: Int) = base.plusDays(offset.toLong())
    private fun engine(starts: List<LocalDate>) = PatternEngine(checkInDays = emptyList(), periodStarts = starts)

    @Test fun cycleVariabilityReportsHerRealRange() {
        val finding = engine(listOf(day(-90), day(-65), day(-30))).findings().first { it.kind == PatternFinding.Kind.CYCLE_VARIABILITY }
        assertEquals(
            "Your recent cycles ranged from about 25 to 35 days apart. That's the kind of detail that can be useful to bring to your GP.",
            finding.detail,
        )
    }

    @Test fun timeframeCountsCyclesNotStarts() {
        assertEquals("Across your last 2 logged cycles", engine(listOf(day(-90), day(-65), day(-30))).findings().first().timeframe)
    }

    /** Regression: no finding sentence may link two measures. */
    @Test fun noFindingLinksTwoMeasures() {
        val today = base
        val cycle = listOf(-95, -70, -35).map { today.plusDays(it.toLong()) to CycleDay(CycleEntryType.FLOW, FlowLevel.MEDIUM) }
        val findings = PatternEngine.build((0 until 40).map { today.minusDays(it.toLong()) }, cycle, today).findings()
        assertEquals(listOf(PatternFinding.Kind.CYCLE_VARIABILITY), findings.map { it.kind })
        val linking = listOf("sleep", "heart rate", "temperature", "energy", "before your period", "on average", "after")
        findings.forEach { f -> linking.forEach { phrase -> assertFalse("${f.kind}: ${f.detail}", f.detail.lowercase().contains(phrase)) } }
    }

    /** Regression: a stray spotting day mid-cycle counted as a start and invented a range. */
    @Test fun spottingDoesNotCreateAFalseCycleRange() {
        val today = base
        val cycle = listOf(-84, -56, -28).map { today.plusDays(it.toLong()) to CycleDay(CycleEntryType.PERIOD_START, FlowLevel.MEDIUM) } +
            (today.minusDays(40) to CycleDay(CycleEntryType.PERIOD_START, FlowLevel.SPOTTING))
        assertTrue(PatternEngine.build(emptyList(), cycle, today).findings().isEmpty())
    }

    @Test fun onlyTheLast120DaysCount() {
        val today = base
        val cycle = listOf(-200, -95, -70, -35).map { today.plusDays(it.toLong()) to CycleDay(CycleEntryType.FLOW, FlowLevel.MEDIUM) }
        val engine = PatternEngine.build(listOf(today, today, today.minusDays(130)), cycle, today)
        assertEquals(3, engine.periodStarts.size)
        assertEquals(1, engine.loggedDayCount) // distinct days in the window
    }
}
