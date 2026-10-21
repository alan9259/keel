package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.domain.EatingLog
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Port of KeelTests/EatingLogTests.swift on the activity repository, plus water and the
 * Health Connect items setting. Yes and no each persist a real row (1 and 0); clearing
 * removes it, so the day reads as not logged rather than a clean no.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EatingLogTests {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: MainDatabase
    private lateinit var repo: ActivityRepository
    private val clock = TestClock(Instant.parse("2026-10-21T09:00:00Z"))
    private val day = LocalDate.of(2026, 10, 21)
    private val id = "eat.alcohol"

    @Before fun setUp() {
        db = inMemoryDatabase()
        repo = ActivityRepository(db.activityLogs(), clock) { "o" }
    }

    @After fun tearDown() = db.close()

    private suspend fun state() = EatingLog.state(id, day, repo.observeAll().first())
    private suspend fun rows() = db.activityLogs().allFor(id).size

    @Test fun yesNoNullRoundTrip() = runBlocking {
        assertNull(state()) // starts as not logged
        repo.setEating(id, day, true)
        assertEquals(true, state())
        assertEquals(1, rows())
        repo.setEating(id, day, false) // the same row, now an explicit 0
        assertEquals(false, state())
        assertEquals(1, rows())
        repo.setEating(id, day, null) // cleared: not logged, row gone
        assertNull(state())
        assertEquals(0, rows())
    }

    @Test fun noWithNoPriorRowStillPersistsAZero() = runBlocking {
        repo.setEating(id, day, false)
        assertEquals(false, state())
        assertEquals(1, rows())
    }

    @Test fun waterCountsUpAndBackToZeroWithoutAnEmptyRowFirst() = runBlocking {
        repo.setAmount("water", day, 0.0) // nothing to record yet
        assertTrue(db.activityLogs().allFor("water").isEmpty())
        repo.setAmount("water", day, 1.0)
        repo.setAmount("water", day, 2.0)
        assertEquals(2.0, repo.observeAll().first().single().amount, 0.0)
        repo.setAmount("water", day, 0.0) // the day's row keeps a 0, as iOS
        assertEquals(0.0, db.activityLogs().allFor("water").single().amount, 0.0)
    }

    @Test fun healthItemsAreAllOnUntilSheTurnsOneOff() = runBlocking {
        val settings = SettingsStore(tempDataStore(tmp.root))
        assertTrue(settings.disabledHealthItemIds.first().isEmpty())
        settings.setDisabledHealthItemIds(setOf("heartVitals"))
        assertEquals(setOf("heartVitals"), settings.disabledHealthItemIds.first())
    }
}
