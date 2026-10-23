package com.therecalibrationyears.keel

import android.app.Application
import com.therecalibrationyears.keel.domain.HealthAvailability
import com.therecalibrationyears.keel.domain.HealthOffer
import com.therecalibrationyears.keel.domain.HealthSnapshot
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
 * Port of KeelTests/HealthAccessOfferTests.swift (the launch check). Health Connect would ask
 * when Keel reads something she hasn't been asked about (Android keeps the set it has asked
 * for, plan §3.6). Keel offers, in context, once: "Connect to Health Connect?" if she isn't
 * connected, "Update Health Connect access?" if she is. A background sync never asks, and
 * never disconnects her. (The pure decision is in core/domain `HealthSyncRulesTests`.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HealthAccessOfferTests {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var bed: HealthTestbed
    private val health get() = bed.health

    @Before fun setUp() = runBlocking {
        bed = HealthTestbed(tmp.root)
        bed.users.upsertProfile(firstName = "Mischa", email = null, appleUserID = null)
        bed.source.snapshotToReturn = HealthSnapshot(sleepByDay = mapOf(bed.today to 7.0))
    }

    @After fun tearDown() = bed.close()

    private suspend fun check() = health.checkAccessOnLaunch(hasOnboarded = true)
    private suspend fun connected() = bed.users.currentProfile()?.healthConnected == true

    // Not connected

    /** Not asked on this install (a new phone, or a restored one). */
    @Test fun restoredPhoneNotConnectedIsOfferedConnect() = runBlocking {
        assertEquals(HealthOffer.CONNECT, check())
    }

    /** Answering is final for this install: no offer on any later launch. */
    @Test fun notNowIsNotAskedAgain() = runBlocking {
        health.answerOffer(HealthOffer.CONNECT)
        repeat(3) { assertEquals(HealthOffer.NONE, check()) }
    }

    /** Regression (review): Continue counts as her answer, so an ask that can't complete doesn't return every launch. */
    @Test fun connectCountsAsAnAnswerToo() = runBlocking {
        assertEquals(HealthOffer.CONNECT, check())
        health.answerOffer(HealthOffer.CONNECT) // what Continue does first
        assertEquals(HealthOffer.NONE, check())
    }

    /** She answered Health Connect (allowed or refused): it won't ask again, and Keel doesn't offer. */
    @Test fun onceSheHasAnsweredTheSheetSheIsNotAskedAgain() = runBlocking {
        bed.everythingAsked()
        assertEquals(HealthOffer.NONE, check())
    }

    /** Includes a reinstall: she onboards again and connects there. */
    @Test fun nothingOfferedBeforeOnboardingWhichHasItsOwnConnectStep() = runBlocking {
        assertEquals(HealthOffer.NONE, health.checkAccessOnLaunch(hasOnboarded = false))
    }

    // Connected, and Keel now reads something new

    /** A connected user is offered Update once, even past an earlier answer to Connect, and stays connected. */
    @Test fun connectedUserIsOfferedUpdateOnceAndStaysConnected() = runBlocking {
        health.answerOffer(HealthOffer.CONNECT) // an old answer, before she connected
        bed.users.setHealthConnected(true)

        assertEquals(HealthOffer.RECONNECT, check())
        assertTrue(connected())

        health.answerOffer(HealthOffer.RECONNECT) // she continued
        assertEquals(HealthOffer.NONE, check())
        assertTrue(connected())
    }

    /** Regression (review): the launch sync runs first and must not hide the Update offer. */
    @Test fun launchSyncBeforeTheCheckDoesNotHideTheUpdateOffer() = runBlocking {
        bed.users.setHealthConnected(true)
        health.runSync(force = true) // what bootstrap does at launch
        assertEquals(HealthOffer.RECONNECT, check())
    }

    /** Regression (review): with a new read to ask about, the background sync still reads what she shared, without asking. */
    @Test fun backgroundSyncKeepsReadingWithoutAsking() = runBlocking {
        bed.users.setHealthConnected(true)
        health.runSync(force = true)
        assertEquals(0, bed.permissions.requests.size)
        assertEquals(1, bed.source.snapshotReads.get())
        assertTrue(connected())
        assertTrue(health.hasImportedData())
    }

    /** Health Connect missing or out of date (unknown status) never leads to asking, or to the Play Store. */
    @Test fun unknownStatusNeverAsks() = runBlocking {
        bed.users.setHealthConnected(true)
        bed.source.availability = HealthAvailability.UPDATE_REQUIRED
        health.runSync(force = true)
        assertEquals(0, bed.permissions.requests.size)
        assertEquals(0, bed.permissions.installsOpened)
        assertEquals(HealthOffer.NONE, check())
    }

    /** Once she has answered for everything Keel reads, the Update answer is forgotten, so a later new read gets its own offer. */
    @Test fun updateAnswerResetsOnceEverythingIsAnswered() = runBlocking {
        bed.users.setHealthConnected(true)
        health.answerOffer(HealthOffer.RECONNECT)
        bed.everythingAsked()
        check()
        assertFalse(bed.settings.healthReconnectOfferAnswered.first())
        bed.source.requestable = bed.source.requestable + "android.permission.health.READ_NUTRITION" // a later new read
        assertEquals(HealthOffer.RECONNECT, check())
    }

    @Test fun deleteAllMyDataForgetsTheAnswers() = runBlocking {
        health.answerOffer(HealthOffer.CONNECT)
        health.answerOffer(HealthOffer.RECONNECT)
        bed.settings.resetToDefaults()
        assertFalse(bed.settings.healthConnectOfferDeclined.first())
        assertFalse(bed.settings.healthReconnectOfferAnswered.first())
    }
}
