package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.CheckInEntity
import com.therecalibrationyears.keel.data.db.CheckInSymptomEntity
import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.data.db.SymptomEntity
import com.therecalibrationyears.keel.data.db.ActivityLogEntity
import com.therecalibrationyears.keel.domain.KeelClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sydney clock, so day boundaries are tested away from UTC (DST starts 4 Oct 2026). */
private class SydneyClock(var instant: Instant = Instant.parse("2026-10-20T23:30:00Z")) : KeelClock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = ZoneId.of("Australia/Sydney")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeDataTests {
    private lateinit var db: MainDatabase
    private val clock = SydneyClock()
    private lateinit var meds: MedicationRepository
    private val today = LocalDate.of(2026, 10, 21) // 23:30 UTC on the 20th is 10:30 on the 21st in Sydney

    private fun med(id: String, tracked: Boolean = true, autoLog: Boolean = false, active: Boolean = true) =
        MedicationEntity(id = id, name = "Magnesium", isTracked = tracked, autoLogDoses = autoLog, isActive = active,
            ownerID = "o", createdAt = clock.instant, updatedAt = clock.instant)

    @Before fun setUp() {
        db = inMemoryDatabase()
        meds = MedicationRepository(db.medications(), { "local-TEST" }, clock)
    }

    @After fun tearDown() = db.close()

    /** Port of KeelTests/MedicationHomeLogTests.swift. */
    @Test fun appearsInHomeLogWhenTrackedOrAutoLogged() {
        assertTrue(med("a", tracked = true, autoLog = false).appearsInHomeLog)
        assertTrue("auto-logged must show even when untracked", med("b", tracked = false, autoLog = true).appearsInHomeLog)
        assertTrue(med("c", tracked = true, autoLog = true).appearsInHomeLog)
        assertFalse(med("d", tracked = false, autoLog = false).appearsInHomeLog)
    }

    @Test fun tickingWritesOneWholeDayLogAtHerLocalMidnight() = runBlocking {
        val m = med("M1").also { db.medications().upsert(it) }
        meds.setTaken(m, today, slot = null, taken = true)
        meds.setTaken(m, today, slot = null, taken = true) // again: still one record
        val logs = meds.observeLogs().first()
        assertEquals(1, logs.size)
        assertEquals(Instant.parse("2026-10-20T13:00:00Z"), logs.single().date) // 00:00 AEDT
        assertEquals(null, logs.single().slot)
        assertEquals("local-TEST", logs.single().ownerID)
    }

    @Test fun untickingRemovesTheRecord() = runBlocking {
        val m = med("M1").also { db.medications().upsert(it) }
        meds.setTaken(m, today, null, taken = true)
        meds.setTaken(m, today, null, taken = false)
        assertTrue(meds.observeLogs().first().isEmpty())
    }

    /** Unticking on Home clears every dose slot that day, and only that day. */
    @Test fun clearTakenRemovesAllSlotsForThatDayOnly() = runBlocking {
        val m = med("M1").also { db.medications().upsert(it) }
        meds.setTaken(m, today, "SLOT-AM", taken = true)
        meds.setTaken(m, today, "SLOT-PM", taken = true)
        meds.setTaken(m, today.minusDays(1), null, taken = true)
        meds.clearTaken(m, today)
        val left = meds.observeLogs().first()
        assertEquals(1, left.size)
        assertEquals(Instant.parse("2026-10-19T13:00:00Z"), left.single().date)
    }

    @Test fun onlyActiveMedicinesAreObserved() = runBlocking {
        db.medications().upsert(med("A"))
        db.medications().upsert(med("B", active = false))
        assertEquals(listOf("A"), meds.observeActive().first().map { it.id })
    }

    /** A removed link, or a link whose catalog symptom is gone, isn't shown on the entry. */
    @Test fun loggedSymptomsSkipRemovedLinksAndMissingSymptoms() = runBlocking {
        val t = clock.instant
        db.checkIns().upsert(CheckInEntity("C1", t, "okay", ownerID = "o", createdAt = t, updatedAt = t))
        listOf("Hot flushes", "Brain fog").forEachIndexed { i, name ->
            db.symptoms().upsert(SymptomEntity("S$i", name, "body", ownerID = "", createdAt = t, updatedAt = t))
        }
        db.checkIns().upsertLink(CheckInSymptomEntity("L1", "C1", "S0", severity = 3, ownerID = "o", createdAt = t, updatedAt = t))
        db.checkIns().upsertLink(CheckInSymptomEntity("L2", "C1", "S1", ownerID = "o", createdAt = t, updatedAt = t, deletedAt = t))
        db.checkIns().upsertLink(CheckInSymptomEntity("L3", "C1", null, ownerID = "o", createdAt = t, updatedAt = t))
        val shown = CheckInRepository(db.checkIns()).observeLoggedSymptoms().first()
        assertEquals(listOf("Hot flushes" to 3), shown.map { it.name to it.severity })
    }

    @Test fun activityAmountsAreHerLocalDays() = runBlocking {
        val sydneyMidnight = Instant.parse("2026-10-20T13:00:00Z")
        db.activityLogs().upsert(ActivityLogEntity("A1", sydneyMidnight, "sleep", 7.5, ownerID = "o", createdAt = sydneyMidnight, updatedAt = sydneyMidnight))
        db.activityLogs().upsert(ActivityLogEntity("A2", sydneyMidnight, "water", 4.0, ownerID = "o", createdAt = sydneyMidnight, updatedAt = sydneyMidnight))
        val sleep = ActivityRepository(db.activityLogs(), clock).observeAmounts("sleep").first()
        assertEquals(listOf(today), sleep.map { it.day })
        assertEquals(7.5, sleep.single().amount, 0.0)
    }
}
