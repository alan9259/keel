package com.therecalibrationyears.keel.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Port of KeelTests/SymptomTallyTests.swift: same day twice counts once, vasomotor union, the type id bridge. */
class SymptomTallyTests {
    private val base = LocalDate.of(2020, 9, 13)
    private fun day(offset: Int) = base.plusDays(offset.toLong())

    @Test fun sameDayFromTwoSourcesCountsOnce() {
        val t = SymptomTally()
        t.add("Hot flushes", day(0))
        t.add("Hot flushes", day(0))
        t.add("Hot flushes", day(1))
        assertEquals(2, t.daysByName["Hot flushes"]?.size)
    }

    @Test fun rankedOrdersByDaysThenName() {
        val t = SymptomTally()
        (0 until 3).forEach { t.add("Headache", day(it)) }
        t.add("Bloating", day(0))
        t.add("Nausea", day(0)) // a tie with Bloating: alphabetical
        assertEquals(listOf("Headache", "Bloating", "Nausea"), t.ranked().map { it.first })
        assertEquals(3, t.ranked().first().second)
    }

    @Test fun vasomotorUnionCountsAMixedDayOnce() {
        val t = SymptomTally()
        t.add("Hot flushes", day(0))
        t.add("Night sweats", day(0))
        t.add("Night sweats", day(1))
        t.add("Headache", day(2))
        assertEquals(2, t.vasomotorDays)
    }

    @Test fun healthTypeIdBridgeRoundTrips() {
        assertEquals("symptom.hot_flushes", SymptomTally.healthTypeId("Hot flushes"))
        assertEquals("Hot flushes", SymptomTally.name("symptom.hot_flushes"))
        assertEquals("Night sweats", SymptomTally.name("symptom.night_sweats"))
        assertEquals("Fatigue or low energy", SymptomTally.name("symptom.fatigue_or_low_energy"))
        assertNull(SymptomTally.name("restingHeartRate"))
    }

    @Test fun blankNamesAreIgnored() {
        val t = SymptomTally()
        t.add("  ", day(0))
        assertEquals(0, t.daysByName.size)
    }
}

/** Port of KeelTests/StatsDayCountsTests.swift (A5): the sentence and the cards count days, so they agree. */
class StatsDayCountsTests {
    private val base = LocalDate.of(2023, 11, 14)
    private fun day(n: Int) = base.plusDays(n.toLong())

    /** Regression: 15 entries on 7 days read "7 of 30 days" above a card saying "15 of 30 days". */
    @Test fun severalEntriesOnOneDayCountOnce() {
        val entries = (0 until 7).flatMap { listOf(day(it) to false, day(it) to false) } + (day(0) to false)
        assertEquals(15, entries.size)
        assertEquals(7, StatsDayCounts.of(entries).checkInDays)
    }

    @Test fun noSymptomsLoggedIsPerDay() {
        val counts = StatsDayCounts.of(listOf(day(0) to false, day(0) to true, day(1) to false, day(1) to false, day(2) to true))
        assertEquals(3, counts.checkInDays)
        assertEquals(1, counts.noSymptomDays)
    }

    @Test fun empty() = assertEquals(StatsDayCounts(0, 0), StatsDayCounts.of(emptyList()))
}
