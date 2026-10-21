package com.therecalibrationyears.keel.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port of KeelTests/ActivityAggregationTests.swift: windows for Day/Week/Month, the no-future
 * clamp, and the total/average roll-ups. Weeks start on Monday, as the iOS test calendar.
 */
class ActivityAggregationTests {
    private val monday = DayOfWeek.MONDAY
    private fun date(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)

    @Test fun dayIntervalIsOneDay() =
        assertEquals(1, ActivityAggregation.days(ActivityAggregation.interval(ActivityPeriod.DAY, date(2026, 9, 17), monday)).size)

    @Test fun weekIntervalIsSevenDays() {
        val days = ActivityAggregation.days(ActivityAggregation.interval(ActivityPeriod.WEEK, date(2026, 9, 17), monday))
        assertEquals(7, days.size)
        assertEquals(date(2026, 9, 14), days.first()) // the Monday
        // A Sunday-first locale starts the week on the Sunday before.
        assertEquals(date(2026, 9, 13), ActivityAggregation.interval(ActivityPeriod.WEEK, date(2026, 9, 17), DayOfWeek.SUNDAY).start)
    }

    @Test fun monthIntervalCoversWholeMonth() =
        assertEquals(30, ActivityAggregation.days(ActivityAggregation.interval(ActivityPeriod.MONTH, date(2026, 9, 17), monday)).size)

    @Test fun rollupTotalSumsPresentDays() {
        val days = listOf(date(2026, 9, 15), date(2026, 9, 16), date(2026, 9, 17))
        val r = ActivityAggregation.rollup(mapOf(date(2026, 9, 15) to 1000.0, date(2026, 9, 17) to 3000.0), days, ActivityAggregation.Mode.TOTAL)
        assertEquals(4000.0, r.summary!!, 0.0)
        assertEquals(listOf(1000.0, 0.0, 3000.0), r.perDay)
        assertEquals(2, r.daysWithData)
    }

    @Test fun rollupAverageIgnoresMissingDays() {
        val days = listOf(date(2026, 9, 15), date(2026, 9, 16), date(2026, 9, 17))
        val r = ActivityAggregation.rollup(mapOf(date(2026, 9, 15) to 6.0, date(2026, 9, 17) to 8.0), days, ActivityAggregation.Mode.AVERAGE)
        assertEquals(7.0, r.summary!!, 0.0) // mean of 6 and 8, not / 3
    }

    @Test fun rollupEmptyIsNull() {
        val r = ActivityAggregation.rollup(emptyMap(), listOf(date(2026, 9, 15), date(2026, 9, 16)), ActivityAggregation.Mode.TOTAL)
        assertNull(r.summary)
        assertEquals(listOf(0.0, 0.0), r.perDay)
    }

    @Test fun shiftDayNoFuture() {
        val today = date(2026, 9, 17)
        assertEquals(today, ActivityAggregation.shift(today, 1, ActivityPeriod.DAY, monday, today))
        assertEquals(date(2026, 9, 16), ActivityAggregation.shift(today, -1, ActivityPeriod.DAY, monday, today))
    }

    @Test fun shiftWeekPreviousAllowedNextBlocked() {
        val today = date(2026, 9, 17)
        assertEquals(today, ActivityAggregation.shift(today, 1, ActivityPeriod.WEEK, monday, today))
        assertEquals(today.minusDays(7), ActivityAggregation.shift(today, -1, ActivityPeriod.WEEK, monday, today))
    }

    @Test fun isCurrent() {
        val today = date(2026, 9, 17)
        assertTrue(ActivityAggregation.isCurrent(today, ActivityPeriod.MONTH, monday, today))
        assertFalse(ActivityAggregation.isCurrent(date(2026, 7, 10), ActivityPeriod.MONTH, monday, today))
    }

    /** A month back from the 31st lands on the month's last day, as iOS's calendar does. */
    @Test fun shiftMonthFromTheEndOfALongMonth() =
        assertEquals(date(2026, 9, 30), ActivityAggregation.shift(date(2026, 10, 31), -1, ActivityPeriod.MONTH, monday, date(2026, 10, 31)))
}

/** Port of KeelTests/VitalTrendTests.swift: a real average and direction over her own vitals. */
class VitalTrendTests {
    private val base = LocalDate.of(2020, 9, 13)
    private fun trend(vararg values: Pair<Int, Double>) = VitalTrend(values.map { VitalTrend.Point(base.plusDays(it.first.toLong()), it.second) })

    @Test fun averageIsRealAndRounded() {
        assertEquals(62, trend(0 to 60.0, 1 to 62.0, 2 to 64.0).average)
        assertEquals(63, trend(0 to 62.0, 1 to 63.0).average) // 62.5 rounds away from zero, as Swift
        assertNull(trend().average)
    }

    @Test fun directionUpDownSteady() {
        assertEquals(VitalTrend.Direction.UP, trend(0 to 58.0, 1 to 58.0, 2 to 59.0, 3 to 66.0, 4 to 66.0, 5 to 67.0).direction)
        assertEquals(VitalTrend.Direction.DOWN, trend(0 to 66.0, 1 to 66.0, 2 to 67.0, 3 to 58.0, 4 to 58.0, 5 to 59.0).direction)
        assertEquals(VitalTrend.Direction.STEADY, trend(0 to 60.0, 1 to 61.0, 2 to 60.0, 3 to 61.0, 4 to 60.0, 5 to 61.0).direction)
    }

    @Test fun directionNullWithTooFewDays() = assertNull(trend(0 to 60.0, 1 to 62.0, 2 to 64.0).direction)

    @Test fun valuesAreInDayOrder() = assertEquals(listOf(60.0, 62.0, 64.0), trend(2 to 64.0, 0 to 60.0, 1 to 62.0).values)
}

/** The pure half of KeelTests/EatingLogTests.swift: how a stored row reads (writes are in core/data). */
class EatingStateTests {
    private val day = LocalDate.of(2026, 10, 21)

    @Test fun yesNoAndNotAnswered() {
        assertNull(EatingLog.state("eat.alcohol", day, emptyList()))
        assertEquals(true, EatingLog.state("eat.alcohol", day, listOf(DayAmount("eat.alcohol", day, 1.0))))
        assertEquals(false, EatingLog.state("eat.alcohol", day, listOf(DayAmount("eat.alcohol", day, 0.0))))
        assertNull(EatingLog.state("eat.alcohol", day.minusDays(1), listOf(DayAmount("eat.alcohol", day, 1.0))))
    }

    @Test fun theCatalogIsTheIosOne() {
        assertEquals(listOf("eat.protein", "eat.veg", "eat.calcium", "eat.wholegrains"), EatingCatalog.nourishment.map { it.id })
        assertEquals(listOf("eat.alcohol", "eat.caffeine", "eat.spicy"), EatingCatalog.triggers.map { it.id })
        assertEquals("glasses", ActivityCatalog.def("water")!!.unit)
    }
}
