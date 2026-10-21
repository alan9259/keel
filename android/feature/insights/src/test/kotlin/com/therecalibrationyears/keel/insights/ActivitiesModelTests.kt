package com.therecalibrationyears.keel.insights

import com.therecalibrationyears.keel.domain.ActivityPeriod
import com.therecalibrationyears.keel.domain.DayAmount
import com.therecalibrationyears.keel.domain.EnergyLevel
import com.therecalibrationyears.keel.domain.Mood
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Activities screen's rules (iOS `ActivitiesView` derived values), on Wednesday 21 Oct 2026. */
class ActivitiesModelTests {
    private val today = LocalDate.of(2026, 10, 21)
    private fun day(offset: Int) = today.plusDays(offset.toLong())
    private fun model(data: ActivitiesData = ActivitiesData(), period: ActivityPeriod = ActivityPeriod.DAY, anchor: LocalDate = today) =
        ActivitiesModel(data, today, period, anchor, DayOfWeek.MONDAY)
    private fun metric(id: String) = ActivitiesModel.METRICS.first { it.id == id }

    @Test fun everyMetricShowsUntilSheTurnsItsItemOff() {
        assertEquals(10, model().shownMetrics.size)
        val off = model(ActivitiesData(disabledItems = setOf("heartVitals", "steps")))
        assertEquals(listOf("exercise", "activeEnergy", "distance", "flights", "sleep", "bodyTemperature"), off.shownMetrics.map { it.id })
        // Switched off but with data on screen: still shown, so nothing quietly disappears.
        val withData = model(ActivitiesData(disabledItems = setOf("heartVitals"), readings = listOf(HealthReading("heartRate", today, 70.0))))
        assertTrue(withData.shownMetrics.any { it.id == "heartRate" })
    }

    @Test fun herOwnEntryWinsOverAnImportedOne() {
        val m = model(ActivitiesData(manual = listOf(DayAmount("sleep", today, 6.5)), imported = listOf(DayAmount("sleep", today, 7.25), DayAmount("steps", today, 4200.0))))
        assertEquals(6.5, m.value(metric("sleep"), today)!!, 0.0)
        assertEquals(4200.0, m.value(metric("steps"), today)!!, 0.0)
        assertNull(m.value(metric("exercise"), today))
    }

    @Test fun theArrowNeedsThreeRecentDaysAndNeverJudgesABodyReading() {
        val past = (1..3).map { DayAmount("steps", day(-it), 5000.0) }
        assertNull(model(ActivitiesData(imported = past.take(2))).trend(metric("steps"), 9000.0)) // two days: says nothing
        val m = model(ActivitiesData(imported = past))
        assertEquals(TrendArrow.UP, m.trend(metric("steps"), 5500.0)) // 10% above her usual
        assertEquals(TrendArrow.SAME, m.trend(metric("steps"), 5400.0))
        assertEquals(TrendArrow.DOWN, m.trend(metric("steps"), 4500.0))
        assertNull(m.trend(metric("steps"), null))
        val hr = (1..3).map { HealthReading("heartRate", day(-it), 60.0) }
        assertNull(model(ActivitiesData(readings = hr)).trend(metric("heartRate"), 90.0)) // a vital: value only
    }

    @Test fun weekRollUpsAndTheCountsOnlyCountDaysThatHaveHappened() {
        val data = ActivitiesData(
            manual = listOf(DayAmount("water", day(-1), 3.0), DayAmount("water", day(-2), 0.0), DayAmount("sleep", day(-1), 6.0), DayAmount("sleep", day(-2), 8.0)),
            feelings = listOf(DayFeeling(today, Mood.OKAY, null), DayFeeling(today, Mood.GOOD, null), DayFeeling(day(-9), Mood.LOW, null)),
        )
        val week = model(data, ActivityPeriod.WEEK)
        assertEquals(day(-2), week.visibleDays.first()) // the Monday
        assertEquals(3, week.elapsedCount) // Mon, Tue, Wed so far
        assertEquals(1, week.checkInDays) // two entries today count once; last week's doesn't
        assertEquals(1, week.waterDays) // a 0 isn't water logged
        assertEquals(7.0, week.rollup(metric("sleep")).summary!!, 0.0) // the average of the nights she has
    }

    @Test fun periodLabels() {
        val full: (LocalDate) -> String = { "full ${it.dayOfMonth}" }
        val short: (LocalDate) -> String = { "${it.dayOfMonth}/${it.monthValue}" }
        val month: (java.time.YearMonth) -> String = { "${it.month} ${it.year}" }
        assertEquals("Today", model().periodLabel(full, short, month))
        assertEquals("Yesterday", model(anchor = day(-1)).periodLabel(full, short, month))
        assertEquals("full 19", model(anchor = day(-2)).periodLabel(full, short, month))
        assertEquals("This week", model(period = ActivityPeriod.WEEK).periodLabel(full, short, month))
        assertEquals("12/10 to 18/10", model(period = ActivityPeriod.WEEK, anchor = day(-7)).periodLabel(full, short, month))
        assertEquals("This month", model(period = ActivityPeriod.MONTH).periodLabel(full, short, month))
        assertEquals("SEPTEMBER 2026", model(period = ActivityPeriod.MONTH, anchor = day(-30)).periodLabel(full, short, month))
    }

    @Test fun vitalsShowOnceThereAreEnoughReadings() {
        assertFalse(model().hasVitals)
        val weight = listOf(HealthReading("bodyMass", day(-3), 70.0), HealthReading("bodyMass", day(-1), 70.4))
        assertTrue(model(ActivitiesData(readings = weight)).hasVitals) // weight shows from two
        val old = (25..30).map { HealthReading("restingHeartRate", day(-it), 60.0) }
        assertFalse(model(ActivitiesData(readings = old)).hasVitals) // older than three weeks
    }

    @Test fun theGpNudgeOnlyWhenRestingHeartRateIsClimbing() {
        val climbing = listOf(58.0, 58.0, 59.0, 66.0, 66.0, 67.0).mapIndexed { i, v -> HealthReading("restingHeartRate", day(-6 + i), v) }
        assertEquals(
            "Recent readings from Health Connect, shown as they were recorded. If your resting heart rate keeps climbing, it's worth a mention to your GP.",
            model(ActivitiesData(readings = climbing)).bodyNote,
        )
        val steady = climbing.map { it.copy(value = 60.0) }
        assertEquals("Recent readings from Health Connect, shown as they were recorded.", model(ActivitiesData(readings = steady)).bodyNote)
    }

    @Test fun theDayShowsHowSheFeltAndHerAnswers() {
        val data = ActivitiesData(
            manual = listOf(DayAmount("eat.protein", today, 0.0), DayAmount("water", today, 4.0)),
            feelings = listOf(DayFeeling(today, Mood.GOOD, EnergyLevel.LOW), DayFeeling(day(-1), Mood.LOW, null)),
        )
        val m = model(data)
        assertEquals(Mood.GOOD, m.feeling?.mood)
        assertEquals(false, m.eatingState("eat.protein")) // an explicit no
        assertNull(m.eatingState("eat.veg")) // not answered
        assertEquals(4.0, m.amount("water"), 0.0)
        assertNull(model(data, anchor = day(-2)).feeling)
    }

    @Test fun numbersReadAsOnIos() {
        assertEquals("7.5", ActivitiesModel.format(7.5, decimal = true))
        assertEquals("8", ActivitiesModel.format(8.0, decimal = true))
        assertEquals("0.2", ActivitiesModel.format(0.25, decimal = true)) // %.1f: ties to even
        assertEquals("63", ActivitiesModel.format(62.5, decimal = false)) // half away from zero
        assertEquals("12,345", ActivitiesModel.format(12345.0, decimal = false, Locale.forLanguageTag("en-AU")))
    }
}
