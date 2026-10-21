package com.therecalibrationyears.keel.home

import com.therecalibrationyears.keel.data.db.CheckInEntity
import com.therecalibrationyears.keel.data.db.LoggedSymptom
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.data.db.MedicationLogEntity
import com.therecalibrationyears.keel.domain.DayAmount
import com.therecalibrationyears.keel.domain.EnergyLevel
import com.therecalibrationyears.keel.domain.Mood
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's rules (port of the derived values in iOS `DashboardView`), in Sydney time. */
class HomeModelTests {
    private val zone = ZoneId.of("Australia/Sydney")
    private val today = LocalDate.of(2026, 10, 21)

    private fun at(day: LocalDate, hour: Int, minute: Int = 0): Instant =
        LocalDateTime.of(day, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private fun checkIn(id: String, at: Instant, mood: Mood = Mood.OKAY, energy: Int = 0, notes: String? = null) =
        CheckInEntity(id, at, mood.raw, energy, notes, ownerID = "o", createdAt = at, updatedAt = at)

    private fun med(id: String, tracked: Boolean = true, autoLog: Boolean = false) = MedicationEntity(
        id = id, name = id, isTracked = tracked, autoLogDoses = autoLog, ownerID = "o",
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private fun taken(medId: String, day: LocalDate, slot: String? = null) = MedicationLogEntity(
        id = "$medId-$day-$slot", date = day.atStartOfDay(zone).toInstant(), slot = slot, taken = true,
        medicationID = medId, ownerID = "o", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private fun model(data: HomeData) = HomeModel(data, today, zone)

    /** Newest first, as the check-in query returns them. */
    private fun newestFirst(vararg c: CheckInEntity) = c.sortedByDescending { it.date }

    @Test fun aDaysEntriesAreEarliestFirstWithTheirSymptomsAndNotes() {
        val data = HomeData(
            checkIns = newestFirst(
                checkIn("late", at(today, 20), Mood.GOOD, energy = 80, notes = "  "),
                checkIn("early", at(today, 7), Mood.LOW, notes = "Slept poorly"),
                checkIn("yesterday", at(today.minusDays(1), 9)),
            ),
            loggedSymptoms = listOf(LoggedSymptom("L1", "early", "Hot flushes", 3)),
        )
        val entries = model(data).entries(today)
        assertEquals(listOf("early", "late"), entries.map { it.id })
        assertEquals(listOf("Hot flushes"), entries.first().symptoms.map { it.name })
        assertEquals("Slept poorly", entries.first().note)
        assertNull("a blank note isn't shown", entries.last().note)
        assertEquals(EnergyLevel.GOOD, entries.last().energy)
        assertNull(entries.first().energy) // she didn't pick one
    }

    /** Days are her local days: 1 am in Sydney is "today" even though it's yesterday in UTC. */
    @Test fun entriesBelongToHerLocalDay() {
        val oneAm = at(today, 1)
        assertEquals(LocalDate.of(2026, 10, 20), oneAm.atZone(ZoneId.of("UTC")).toLocalDate())
        val m = model(HomeData(checkIns = listOf(checkIn("c", oneAm))))
        assertEquals(1, m.entries(today).size)
        assertTrue(m.entries(today.minusDays(1)).isEmpty())
    }

    @Test fun moodStripShowsTheDaysLatestEntry() {
        val m = model(HomeData(checkIns = newestFirst(checkIn("a", at(today, 8), Mood.LOW), checkIn("b", at(today, 21), Mood.GREAT))))
        assertEquals(Mood.GREAT, m.latestMood(today))
        assertNull(m.latestMood(today.minusDays(1)))
    }

    @Test fun energyBarsAverageOnlyTheEnergyShePicked() {
        val m = model(
            HomeData(
                checkIns = newestFirst(
                    checkIn("a", at(today, 8), energy = 40), checkIn("b", at(today, 12), energy = 80),
                    checkIn("c", at(today, 18), energy = 0), // not picked: left out, not counted as 0
                ),
            ),
        )
        assertEquals(60.0, m.energyBars.last().value!!, 0.0)
        assertNull(m.energyBars.first().value) // an empty track, never a value
        assertEquals(7, m.energyBars.size)
        assertEquals("2 entries this week", m.energyTrailing)
    }

    @Test fun energySummaryOnlyCountsTheLastSevenDays() {
        val m = model(
            HomeData(
                checkIns = newestFirst(
                    checkIn("old1", at(today.minusDays(7), 9), energy = 20),
                    checkIn("old2", at(today.minusDays(8), 9), energy = 20),
                    checkIn("now", at(today, 9), energy = 100),
                ),
            ),
        )
        assertEquals("1 entry this week", m.energyTrailing)
    }

    @Test fun sleepUsesHerEntryOverAnImportedOneAndAveragesNightsWithData() {
        val m = model(
            HomeData(
                manualSleep = listOf(DayAmount("sleep", today, 6.0)),
                importedSleep = listOf(DayAmount("sleep", today, 8.0), DayAmount("sleep", today.minusDays(1), 7.0)),
            ),
        )
        assertEquals(6.0, m.sleepHours(today)!!, 0.0)
        assertEquals(6.5, m.sleepAverage, 0.0)
        assertEquals(0.0, model(HomeData()).sleepAverage, 0.0)
    }

    @Test fun medicineRowsAreTrackedOrAutoLoggedAndTickedForThatDayOnly() {
        val data = HomeData(
            medications = listOf(med("Magnesium"), med("Untracked", tracked = false), med("Auto", tracked = false, autoLog = true)),
            medicationLogs = listOf(taken("Magnesium", today.minusDays(1)), taken("Auto", today, slot = "AM")),
        )
        val m = model(data)
        assertEquals(listOf("Magnesium" to false, "Auto" to true), m.medicineRows(today).map { it.medication.id to it.taken })
        assertEquals(listOf("Magnesium" to true, "Auto" to false), m.medicineRows(today.minusDays(1)).map { it.medication.id to it.taken })
        assertTrue(m.anyAutoLogged)
    }

    @Test fun daysTakenCountsDistinctDaysInTheLastSevenForHomeMedicinesOnly() {
        val data = HomeData(
            medications = listOf(med("A"), med("B"), med("Hidden", tracked = false)),
            medicationLogs = listOf(
                taken("A", today), taken("B", today),          // same day: counts once
                taken("A", today.minusDays(6)),                // oldest of the 7
                taken("A", today.minusDays(7)),                // outside the window
                taken("Hidden", today.minusDays(2)),           // not in Home's log
            ),
        )
        val m = model(data)
        assertEquals(2, m.daysTakenLast7)
        assertFalse(m.anyAutoLogged)
    }

    @Test fun futureDaysAreNotSelectable() {
        val m = model(HomeData())
        assertTrue(m.isFuture(today.plusDays(1)))
        assertFalse(m.isFuture(today))
        assertEquals(today, m.last7.last())
    }
}
