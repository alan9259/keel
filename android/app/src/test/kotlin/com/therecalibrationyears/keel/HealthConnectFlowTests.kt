package com.therecalibrationyears.keel

import android.app.Application
import com.therecalibrationyears.keel.domain.HealthAvailability
import com.therecalibrationyears.keel.domain.HealthOffer
import com.therecalibrationyears.keel.domain.HealthSnapshot
import com.therecalibrationyears.keel.domain.HealthSync
import com.therecalibrationyears.keel.platform.HealthConnectReads
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Connecting to Health Connect (plan §3.5, §3.6, D4), against a fake: what's asked, when she
 * counts as connected, the Play Store on Android 9 to 13, the first import and its retries,
 * the periods read window, Disconnect, and the per-item switches. Includes the port of
 * KeelTests/HealthSyncGatingTests.swift `testConnectAppleHealthPersistsTheFlag`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HealthConnectFlowTests {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var bed: HealthTestbed
    private val health get() = bed.health
    private val source get() = bed.source

    @Before fun setUp() = runBlocking {
        bed = HealthTestbed(tmp.root)
        bed.users.upsertProfile(firstName = "Mischa", email = null, appleUserID = null)
        source.snapshotToReturn = bed.someData()
    }

    @After fun tearDown() = bed.close()

    private suspend fun connected() = bed.users.currentProfile()?.healthConnected == true

    /** Port of testConnectAppleHealthPersistsTheFlag: the shared connect path (onboarding and the screen) saves it. */
    @Test fun connectPersistsTheFlagAndImports() = runBlocking {
        assertTrue(health.connect())
        assertTrue(connected())
        assertEquals(listOf(HealthConnectReads.all.toSet()), bed.permissions.requests) // everything Keel reads, once
        bed.waitUntil { health.hasImportedData() }
    }

    /** Asked about everything, so no launch offer follows; she chose on Health Connect's own screen. */
    @Test fun everythingRefusedIsNotConnectedButCountsAsAsked() = runBlocking {
        bed.permissions.answer = { emptySet() }
        assertFalse(health.connect())
        assertFalse(connected())
        assertEquals(HealthConnectReads.all.toSet(), bed.settings.healthAskedPermissions.first())
        assertEquals(HealthOffer.NONE, health.checkAccessOnLaunch(hasOnboarded = true))
        assertEquals(0, source.snapshotReads.get())
    }

    /** Anything Keel reads is enough (D4): she can allow sleep alone. */
    @Test fun allowingSomeIsConnected() = runBlocking {
        bed.permissions.answer = { setOf(HealthConnectReads.SLEEP) }
        assertTrue(health.connect())
        assertTrue(connected())
    }

    /** Android 9 to 13 without Health Connect (or an old one): the Play Store opens, nothing is asked. */
    @Test fun updateRequiredOpensThePlayStoreAndAsksNothing() = runBlocking {
        source.availability = HealthAvailability.UPDATE_REQUIRED
        assertFalse(health.connect())
        assertEquals(1, bed.permissions.installsOpened)
        assertTrue(bed.permissions.requests.isEmpty())
        assertFalse(connected())
    }

    @Test fun unavailableDoesNothing() = runBlocking {
        source.availability = HealthAvailability.UNAVAILABLE
        assertFalse(health.connect())
        assertEquals(0, bed.permissions.installsOpened)
        assertTrue(bed.permissions.requests.isEmpty())
    }

    /** No screen to ask from (no activity): nothing is recorded as asked, and nothing changes. */
    @Test fun noScreenToAskFromChangesNothing() = runBlocking {
        bed.permissions.answer = { null }
        assertFalse(health.connect())
        assertTrue(bed.settings.healthAskedPermissions.first().isEmpty())
        assertEquals(HealthOffer.CONNECT, health.checkAccessOnLaunch(hasOnboarded = true))
    }

    /** Reads can come back empty just after the first grant: tried again, and only until data arrives. */
    @Test fun anEmptyFirstReadIsRetriedUntilDataArrives() = runBlocking {
        val bed = HealthTestbed(tmp.newFolder(), retryDelays = List(3) { Duration.ofMillis(30) })
        try {
            bed.users.upsertProfile(firstName = "Mischa", email = null, appleUserID = null)
            assertTrue(bed.health.connect()) // the first read is empty
            bed.waitUntil { bed.source.snapshotReads.get() >= 1 && !bed.health.isSyncInFlight }
            bed.source.snapshotToReturn = bed.someData() // access has come through
            bed.waitUntil { bed.health.hasImportedData() }
            delay(200)
            assertEquals("no retry once data is in", 2, bed.source.snapshotReads.get())
        } finally {
            bed.close()
        }
    }

    /** Without the history permission, periods reconcile back to 30 days before they were first allowed. */
    @Test fun periodsReadWindowFollowsTheFirstGrantWithoutHistory() = runBlocking {
        bed.permissions.answer = { it - HealthConnectReads.HISTORY }
        bed.connectAndSettle()
        health.runSync(force = true)
        assertEquals(bed.today.minusDays(30), source.lastWindow)
        assertEquals(bed.today.toEpochDay(), bed.settings.healthFirstMenstruationGrantDay.first())

        // A week later the first grant day is kept, so the window doesn't move.
        bed.now = bed.now.plus(Duration.ofDays(7))
        health.runSync(force = true)
        assertEquals(bed.today.minusDays(37), source.lastWindow)
    }

    @Test fun periodsReadWindowIsAYearWithHistory() = runBlocking {
        bed.connectAndSettle()
        health.runSync(force = true)
        assertEquals(bed.today.minusDays(HealthSync.IMPORT_DAYS.toLong()), source.lastWindow)
    }

    /** Until she has answered for everything Keel reads, an empty read can't mean "no periods": no reconciling. */
    @Test fun noPeriodsReconcilingWhileSomethingIsUnasked() = runBlocking {
        bed.users.setHealthConnected(true)
        source.granted = setOf(HealthConnectReads.MENSTRUATION)
        health.runSync(force = true)
        assertNull(source.lastWindow)
    }

    /**
     * Port of AppleHealthPeriodsTests.testSyncRemovesDeletedDaysOnlyOnceSheHasAnsweredForPeriods:
     * until she has answered for everything Keel reads, an empty read means "not shared yet".
     */
    @Test fun syncRemovesDeletedDaysOnlyOnceSheHasAnsweredForPeriods() = runBlocking {
        bed.users.setHealthConnected(true)
        source.granted = HealthConnectReads.all.toSet()
        source.snapshotToReturn = HealthSnapshot(sleepByDay = mapOf(bed.today to 7.0)) // no periods returned
        bed.health.removeImported()
        com.therecalibrationyears.keel.data.HealthIngestor(bed.healthDb, bed.main, { "o" }, bed.clock)
            .ingest(HealthSnapshot(menstrualFlow = mapOf(bed.today.minusDays(2) to com.therecalibrationyears.keel.domain.FlowLevel.MEDIUM)))

        health.runSync(force = true) // not answered for everything yet
        assertEquals(1, bed.healthDb.flow().live().size)

        bed.everythingAsked() // answered: an empty read now means none
        health.runSync(force = true)
        assertTrue(bed.healthDb.flow().live().isEmpty())
    }

    /** Disconnect (D4): access revoked, no more reads, imports kept, and no offer next launch. */
    @Test fun disconnectRevokesAndStopsReading() = runBlocking {
        bed.connectAndSettle()
        assertTrue(health.hasImportedData())
        val reads = source.snapshotReads.get()

        health.disconnect()

        assertFalse(connected())
        assertEquals(1, source.revokes.get())
        assertTrue(source.granted.isEmpty())
        assertNull("a reconnect starts the periods window afresh", bed.settings.healthFirstMenstruationGrantDay.first())
        health.runSync(force = true)
        assertEquals(reads, source.snapshotReads.get())
        assertTrue("what was imported stays until she removes it", health.hasImportedData())
        assertEquals(HealthOffer.NONE, health.checkAccessOnLaunch(hasOnboarded = true))
    }

    /** Switching an item off stops it; back on brings it in now (iOS `setSyncing`). */
    @Test fun switchingAnItemBackOnSyncsNow() = runBlocking {
        bed.users.setHealthConnected(true)
        bed.everythingAsked()
        health.setSyncing("steps", on = false)
        assertEquals(setOf("steps"), bed.settings.disabledHealthItemIds.first())
        health.runSync(force = true)
        assertFalse("steps switched off aren't imported", health.hasImportedData())

        health.setSyncing("steps", on = true)
        assertTrue(bed.settings.disabledHealthItemIds.first().isEmpty())
        bed.waitUntil { health.hasImportedData() }
    }

    @Test fun removeImportedRemovesEverythingImported() = runBlocking {
        bed.users.setHealthConnected(true)
        source.snapshotToReturn = HealthSnapshot(
            sleepByDay = mapOf(bed.today to 7.0),
            vitals = listOf(HealthSnapshot.VitalSeries("restingHeartRate", "bpm", mapOf(bed.today to 60.0))),
        )
        health.runSync(force = true)
        assertEquals(2, health.removeImported())
        assertFalse(health.hasImportedData())
        assertEquals(0, health.removeImported())
    }
}
