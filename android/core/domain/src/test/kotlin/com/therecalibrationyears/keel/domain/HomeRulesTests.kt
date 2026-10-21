package com.therecalibrationyears.keel.domain

import com.therecalibrationyears.keel.domain.EnergyLevel.CHARGED
import com.therecalibrationyears.keel.domain.EnergyLevel.DRAINED
import com.therecalibrationyears.keel.domain.EnergyLevel.GOOD
import com.therecalibrationyears.keel.domain.EnergyLevel.LOW
import com.therecalibrationyears.keel.domain.EnergyLevel.OKAY
import com.therecalibrationyears.keel.domain.parity.formatOneDecimal
import com.therecalibrationyears.keel.domain.parity.roundHalfAwayFromZero
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Port of the energy-label tests in KeelTests/LookingBackTests.swift. */
class EnergySummaryTests {
    private val base = Instant.parse("2026-10-21T12:00:00Z")
    private fun e(hoursAgo: Long, level: EnergyLevel) = EnergySummary.Pick(base.minusSeconds(hoursAgo * 3600), level)

    @Test fun labelNeedsThreeEntries() {
        assertEquals("No data yet", EnergySummary.trailing(emptyList()))
        assertEquals("1 entry this week", EnergySummary.trailing(listOf(e(1, OKAY))))
        assertEquals("2 entries this week", EnergySummary.trailing(listOf(e(1, OKAY), e(2, LOW))))
        assertEquals("Okay", EnergySummary.trailing(listOf(e(1, OKAY), e(2, LOW), e(3, OKAY))))
    }

    /** Regression: the label was an average, so Drained, Drained, Charged showed "Low". */
    @Test fun mostlyIsTheMostFrequentLevelNotAnAverage() {
        assertEquals("Drained", EnergySummary.trailing(listOf(e(1, DRAINED), e(30, DRAINED), e(60, CHARGED))))
        assertEquals("Good", EnergySummary.trailing(listOf(e(1, GOOD), e(2, CHARGED), e(3, GOOD), e(4, LOW))))
    }

    /** A tie goes to the tied level she picked most recently. */
    @Test fun tieGoesToTheMostRecentlyPickedLevel() {
        assertEquals(GOOD, EnergySummary.mostFrequent(listOf(e(5, LOW), e(1, GOOD), e(10, LOW), e(3, GOOD))))
        assertEquals(LOW, EnergySummary.mostFrequent(listOf(e(1, LOW), e(5, GOOD), e(10, LOW), e(3, GOOD))))
        assertNull(EnergySummary.mostFrequent(emptyList()))
    }
}

class EnergyLevelTests {
    @Test fun storedPercentRoundsToTheNearestLevelHalvesUp() {
        assertEquals(DRAINED, EnergyLevel.fromPercent(10)) // 0.5 → 1, as iOS .rounded()
        assertEquals(LOW, EnergyLevel.fromPercent(30))     // 1.5 → 2
        assertEquals(OKAY, EnergyLevel.fromPercent(50))    // 2.5 → 3 (Kotlin's round() would give 2)
        assertEquals(CHARGED, EnergyLevel.fromPercent(100))
        assertEquals(CHARGED, EnergyLevel.fromPercent(140)) // clamped
        assertEquals(DRAINED, EnergyLevel.fromPercent(-5))  // clamped
    }

    @Test fun zeroMeansShePickedNothing() {
        assertNull(EnergyLevel.picked(EnergyLevel.NOT_RECORDED))
        assertEquals(GOOD, EnergyLevel.picked(80))
        assertEquals(listOf(20, 40, 60, 80, 100), EnergyLevel.entries.map { it.percent })
    }

    @Test fun severityCyclesThroughFourTaps() {
        assertEquals(listOf(1, 2, 3, 0), listOf(0, 1, 2, 3).map(SymptomSeverity::nextLevel))
        assertEquals("Moderate", SymptomSeverity.of(2)?.label)
        assertNull(SymptomSeverity.of(0))
    }
}

class MoodTests {
    @Test fun rawValuesAndOrderMatchIos() {
        assertEquals(listOf("difficult", "low", "okay", "good", "great"), Mood.entries.map { it.raw })
        assertEquals(Mood.OKAY, Mood.fromRaw("unknown")) // iOS falls back to Okay
    }

    @Test fun moodPacksMapInMoodOrderAndDefaultToOcean() {
        assertEquals("ocean", MoodPacks.pack(null).id)
        assertEquals("ocean", MoodPacks.pack("retired-pack").id)
        assertEquals("🌕", MoodPacks.pack("moon").emojiFor(Mood.GREAT))
        assertEquals("😣", MoodPacks.pack("classic").emojiFor(Mood.DIFFICULT))
        assertEquals(6, MoodPacks.all.size)
        MoodPacks.all.forEach { assertEquals(it.id, 5, it.emoji.size) }
    }
}

class GreetingTests {
    @Test fun boundariesMatchIos() {
        assertEquals("Good evening", Greeting.forHour(4))
        assertEquals("Good morning", Greeting.forHour(5))
        assertEquals("Good morning", Greeting.forHour(11))
        assertEquals("Good afternoon", Greeting.forHour(12))
        assertEquals("Good afternoon", Greeting.forHour(16))
        assertEquals("Good evening", Greeting.forHour(17))
        assertEquals("Good evening", Greeting.forHour(0))
    }
}

class MergedActivityTests {
    private val day = LocalDate.of(2026, 10, 20)

    @Test fun herOwnEntryWinsOverAnImportedOne() {
        val manual = listOf(DayAmount("sleep", day, 6.5))
        val imported = listOf(DayAmount("sleep", day, 7.2), DayAmount("sleep", day.minusDays(1), 8.0))
        assertEquals(6.5, MergedActivity.amount("sleep", day, manual, imported)!!, 0.0)
        assertEquals(8.0, MergedActivity.amount("sleep", day.minusDays(1), manual, imported)!!, 0.0)
        assertEquals(mapOf(day to 6.5, day.minusDays(1) to 8.0), MergedActivity.byDay("sleep", manual, imported))
    }

    @Test fun zeroCountsAsNothingAndOtherActivitiesDontLeak() {
        val manual = listOf(DayAmount("sleep", day, 0.0), DayAmount("water", day, 5.0))
        val imported = listOf(DayAmount("sleep", day, 7.0))
        assertEquals(7.0, MergedActivity.amount("sleep", day, manual, imported)!!, 0.0) // 0 falls through
        assertNull(MergedActivity.amount("sleep", day.plusDays(1), manual, imported))
    }
}

class CalendarGridTests {
    @Test fun last7EndsTodayOldestFirst() {
        val today = LocalDate.of(2026, 3, 2)
        assertEquals(LocalDate.of(2026, 2, 24), CalendarGrid.last7(today).first())
        assertEquals(today, CalendarGrid.last7(today).last())
        assertEquals(7, CalendarGrid.last7(today).size)
    }

    /** October 2026 starts on a Thursday. */
    @Test fun monthCellsLeadWithBlanksForTheLocalesFirstDay() {
        val oct = YearMonth.of(2026, 10)
        val sundayFirst = CalendarGrid.monthCells(oct, DayOfWeek.SUNDAY)
        assertEquals(4, sundayFirst.takeWhile { it == null }.size) // Sun Mon Tue Wed blank
        val mondayFirst = CalendarGrid.monthCells(oct, DayOfWeek.MONDAY)
        assertEquals(3, mondayFirst.takeWhile { it == null }.size)  // Mon Tue Wed blank (en_AU)
        assertEquals(LocalDate.of(2026, 10, 31), mondayFirst.last())
        assertEquals(29, CalendarGrid.monthCells(YearMonth.of(2028, 2), DayOfWeek.MONDAY).count { it != null })
    }

    @Test fun weekOrderStartsAtTheFirstDay() {
        assertEquals(DayOfWeek.SUNDAY, CalendarGrid.weekOrder(DayOfWeek.MONDAY).last())
        assertEquals(DayOfWeek.SATURDAY, CalendarGrid.weekOrder(DayOfWeek.SUNDAY).last())
    }
}

class ParityFormattingTests {
    @Test fun roundingIsHalfAwayFromZero() {
        assertEquals(3.0, roundHalfAwayFromZero(2.5), 0.0)
        assertEquals(-3.0, roundHalfAwayFromZero(-2.5), 0.0)
        assertEquals(2.0, roundHalfAwayFromZero(2.49), 0.0)
    }

    /** As Swift String(format: "%.1f"): exact binary value, ties to even. */
    @Test fun oneDecimalMatchesPrintf() {
        assertEquals("7.2", formatOneDecimal(7.25))  // exact tie → even (String.format gives 7.3)
        assertEquals("7.3", formatOneDecimal(7.35))  // 7.3499999... in binary
        assertEquals("0.1", formatOneDecimal(0.05))  // 0.05000000000000000277...
        assertEquals("7.5", formatOneDecimal(7.5))
    }

    @Test fun sleepHoursReadLikeIos() {
        assertEquals("8h", SleepFormat.hours(8.0))
        assertEquals("7.5h", SleepFormat.hours(7.5))
        assertEquals("7.2h", SleepFormat.hours(7.25))
    }
}
