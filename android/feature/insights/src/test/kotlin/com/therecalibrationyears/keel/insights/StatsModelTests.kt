package com.therecalibrationyears.keel.insights

import com.therecalibrationyears.keel.domain.DayAmount
import com.therecalibrationyears.keel.domain.EnergyLevel
import com.therecalibrationyears.keel.domain.Mood
import com.therecalibrationyears.keel.domain.StatsPeriod
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Stats screen's numbers and sentences (iOS `ReportsView`), on Wednesday 21 Oct 2026. */
class StatsModelTests {
    private val today = LocalDate.of(2026, 10, 21)
    private fun day(offset: Int) = today.plusDays(offset.toLong())
    private fun entry(offset: Int, mood: Mood = Mood.OKAY, energy: EnergyLevel? = null, vararg symptoms: String) =
        StatsEntry(day(offset), mood, energy, energy?.percent ?: 0, symptoms.toList())
    private fun model(data: StatsData, period: StatsPeriod = StatsPeriod.MONTH) = StatsModel(data, today, period, Locale.forLanguageTag("en-AU"))

    @Test fun anEmptyRecordSaysSoWithoutInventingAnything() {
        val m = model(StatsData())
        assertEquals("You checked in on 0 of the last 30 days.", m.checkInLine)
        assertEquals("None yet", m.averageEnergy)
        assertTrue(m.topSymptoms.isEmpty())
        assertNull(m.vasomotorLine)
        assertFalse(m.hasSleep)
        assertFalse(m.hasVitals)
        assertEquals("No vitals from Health Connect in this month yet. Resting heart rate, heart rate variability, weight and blood pressure appear here once there's data.", m.vitalsEmpty)
    }

    @Test fun countsAreDaysInTheWindowAndTheTilesAgree() {
        val data = StatsData(
            entries = listOf(
                entry(0, energy = EnergyLevel.LOW, symptoms = arrayOf("Hot flushes")), entry(0, energy = EnergyLevel.OKAY),
                entry(-3, energy = EnergyLevel.LOW), entry(-40, symptoms = arrayOf("Headaches")), // outside the month
            ),
            activeMedicineCount = 2, takenDays = listOf(day(0), day(0), day(-1), day(-45)),
        )
        val m = model(data)
        assertEquals("You checked in on 2 of the last 30 days.", m.checkInLine)
        assertEquals(
            listOf(
                Triple("Check-ins", "2", "of 30 days"),
                Triple("Avg energy", "Low", "across entries"), // (40 + 60 + 40) / 3 = 46 → Low, integer division as iOS
                Triple("No symptoms logged", "1", "of 2 days logged"),
                Triple("Medicines", "2", "taken of 30 days"),
            ),
            m.tiles,
        )
        assertEquals("Recorded as taken on 2 of 30 days.", m.medicinesLine)
        assertEquals("2 tracked over the month.", m.medicinesCountLine)
        assertEquals("1 tracked over the 3 months.", model(data.copy(activeMedicineCount = 1), StatsPeriod.QUARTER).medicinesCountLine)
        assertEquals("Recorded as taken on 2 of 30 days, including days auto-logged.", model(data.copy(anyAutoLogged = true)).medicinesLine)
    }

    @Test fun moodSharesTruncateAsIos() {
        val m = model(StatsData(entries = listOf(entry(0, Mood.GOOD), entry(-1, Mood.GOOD), entry(-2, Mood.LOW))))
        assertEquals("Good 66%", m.moodLabel(Mood.GOOD))
        assertEquals("Low 33%", m.moodLabel(Mood.LOW))
        assertEquals(0.0, m.moodFraction(Mood.GREAT), 0.0)
    }

    @Test fun vasomotorFrequencyIsAPlainRate() {
        val week = model(StatsData(entries = listOf(entry(0, symptoms = arrayOf("Hot flushes")), entry(-1, symptoms = arrayOf("Night sweats", "Hot flushes")), entry(-2, symptoms = arrayOf("Night sweats")))), StatsPeriod.WEEK)
        assertEquals("Hot flushes or night sweats on 3 of the last 7 days (about 3 a week).", week.vasomotorLine)
        val month = model(StatsData(entries = listOf(entry(0, symptoms = arrayOf("Hot flushes")))))
        assertEquals("Hot flushes or night sweats on 1 of the last 30 days (less than one a week).", month.vasomotorLine)
    }

    @Test fun symptomsRankByDaysAndPageAfterTen() {
        val names = (1..12).map { "Symptom $it" }
        val m = model(StatsData(entries = listOf(entry(0, symptoms = names.toTypedArray()), entry(-1, symptoms = arrayOf("Symptom 9")))))
        assertEquals("Symptom 9" to 2, m.topSymptoms.first())
        assertEquals(listOf(10, 2), m.symptomPages.map { it.size })
        assertEquals("1 day", m.symptomDays(1))
        assertEquals("2 days", m.symptomDays(2))
    }

    @Test fun sleepCoversUpToTwoWeeksAndHerOwnEntryWins() {
        val data = StatsData(manualSleep = listOf(DayAmount("sleep", today, 7.0), DayAmount("sleep", day(-2), 6.5)), importedSleep = listOf(DayAmount("sleep", today, 5.0), DayAmount("sleep", day(-20), 8.0)))
        val m = model(data)
        assertEquals(14, m.sleepDays.size)
        assertEquals(today, m.sleepDays.last())
        assertEquals(7.0, m.sleepSeries.last(), 0.0)
        assertEquals("6.8h", m.hours(m.sleepAverage)) // (7 + 6.5) / 2 = 6.75, %.1f ties to even
        assertEquals(7, model(data, StatsPeriod.WEEK).sleepDays.size)
        assertEquals("Tap a bar for that night's hours.", m.sleepDetail(null) { "x" })
        assertEquals("Last night: 7.0h", m.sleepDetail(13) { "x" })
        assertEquals("Mon 19 Oct: 6.5h", m.sleepDetail(11) { "Mon 19 Oct" })
        assertEquals("Tap a bar for that night's hours.", m.sleepDetail(12) { "x" }) // a night with nothing
    }

    @Test fun vitalsShowAveragesAndRangesOnceThereAreEnough() {
        val readings = listOf(58.0, 62.0, 70.0).mapIndexed { i, v -> HealthReading("restingHeartRate", day(-i), v) } +
            listOf(HealthReading("bloodPressureSystolic", day(0), 120.0), HealthReading("bloodPressureSystolic", day(-1), 124.0)) +
            listOf(HealthReading("bloodPressureDiastolic", day(0), 78.0), HealthReading("bloodPressureDiastolic", day(-1), 78.0))
        val m = model(StatsData(readings = readings))
        assertTrue(m.hasVitals)
        assertEquals(63, m.restingHeartRate.average)
        assertEquals("58 to 70", m.range(m.restingHeartRate))
        assertEquals("122/78", m.bloodPressure)
        assertEquals("Averages and ranges over the month, from Health Connect. Keel shows these as they were recorded.", m.vitalsFooter)
    }
}
