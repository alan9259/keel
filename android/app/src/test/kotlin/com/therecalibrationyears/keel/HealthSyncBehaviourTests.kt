package com.therecalibrationyears.keel

import android.app.Application
import com.therecalibrationyears.keel.data.db.HealthSampleEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Port of KeelTests/HealthSyncBehaviourTests.swift: the real sync driven against a fake Health
 * Connect. Nothing is asked before Connect, a Disconnect mid-read doesn't import, a forced sync
 * during another is queued (not dropped), and an empty read doesn't use up the 30-minute wait.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HealthSyncBehaviourTests {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var bed: HealthTestbed
    private val health get() = bed.health
    private val fake get() = bed.source

    @Before fun setUp() {
        bed = HealthTestbed(tmp.root)
        runBlocking {
            bed.users.upsertProfile(firstName = "Mischa", email = null, appleUserID = null)
            bed.everythingAsked() // she has answered Health Connect before (the usual connected case)
        }
    }

    @After fun tearDown() = bed.close()

    /** Regression (launch prompt): before she connects, even a forced sync never asks or reads. */
    @Test fun notConnectedSyncNeverRequestsAuthorization() = runBlocking {
        health.runSync(force = false)
        health.runSync(force = true)
        assertEquals(0, bed.permissions.requests.size)
        assertEquals(0, fake.snapshotReads.get())
    }

    /** Regression: disconnecting while the year-long read is in flight must not import it. */
    @Test fun disconnectDuringReadDoesNotImport() = runBlocking {
        bed.users.setHealthConnected(true)
        fake.snapshotToReturn = bed.someData()
        fake.holdNextRead = true

        health.sync(force = true)
        bed.waitUntil { fake.isHoldingRead }
        bed.users.setHealthConnected(false) // she taps Disconnect mid-read
        fake.releaseRead()
        bed.waitForIdle()

        assertFalse(health.hasImportedData())
    }

    /** Regression: "Sync now" (or an item back on) while a sync is running runs once it finishes. */
    @Test fun forcedSyncDuringAnotherIsQueuedNotDropped() = runBlocking {
        bed.users.setHealthConnected(true)
        fake.snapshotToReturn = bed.someData()
        fake.holdNextRead = true

        health.sync(force = true)
        bed.waitUntil { fake.isHoldingRead }
        health.runSync(force = true) // arrives mid-sync: queued
        fake.releaseRead()
        bed.waitUntil { fake.snapshotReads.get() == 2 }
        bed.waitForIdle()

        assertEquals(2, fake.snapshotReads.get())
    }

    /** An unforced (foreground) request during a sync is simply coalesced, not queued. */
    @Test fun unforcedSyncDuringAnotherIsCoalesced() = runBlocking {
        bed.users.setHealthConnected(true)
        fake.snapshotToReturn = bed.someData()
        fake.holdNextRead = true

        health.sync(force = true)
        bed.waitUntil { fake.isHoldingRead }
        health.runSync(force = false)
        fake.releaseRead()
        bed.waitForIdle()
        delay(50)

        assertEquals(1, fake.snapshotReads.get())
    }

    /** An empty read leaves the next foreground sync due; a read with data starts the 30-minute wait. */
    @Test fun emptyReadDoesNotConsumeThrottleButDataDoes() = runBlocking {
        bed.users.setHealthConnected(true)

        health.runSync(force = false) // empty
        health.runSync(force = false) // still due
        assertEquals(2, fake.snapshotReads.get())

        fake.snapshotToReturn = bed.someData()
        health.runSync(force = false) // returns data, starts the wait
        health.runSync(force = false) // throttled
        assertEquals(3, fake.snapshotReads.get())
        assertTrue(health.hasImportedData())
    }

    /** Delete all my data resets the wait and her per-item choices for the next person. */
    @Test fun resetHealthSyncStateClearsThrottleAndChoices() = runBlocking {
        bed.users.setHealthConnected(true)
        bed.settings.setDisabledHealthItemIds(setOf("exercise"))
        fake.snapshotToReturn = bed.someData()
        health.runSync(force = false)

        health.resetSyncState()

        assertTrue(bed.settings.disabledHealthItemIds.first().isEmpty())
        health.runSync(force = false) // the wait forgotten, so due again
        assertEquals(2, fake.snapshotReads.get())
    }

    /** Only live rows count as imported (matches the Health Connect screen's note). */
    @Test fun softDeletedRowsDontCountAsImported() = runBlocking {
        val day = bed.today.atStartOfDay(bed.zone).toInstant()
        bed.healthDb.samples().insertAll(
            listOf(HealthSampleEntity("S1", "restingHeartRate", day, 60.0, "bpm", ownerID = "o", createdAt = bed.now, updatedAt = bed.now, deletedAt = bed.now)),
        )
        assertFalse(health.hasImportedData())
    }
}
