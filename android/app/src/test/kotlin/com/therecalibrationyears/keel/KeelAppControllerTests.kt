package com.therecalibrationyears.keel

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.therecalibrationyears.keel.data.ActivityRepository
import com.therecalibrationyears.keel.data.CheckInRepository
import com.therecalibrationyears.keel.data.DeviceContext
import com.therecalibrationyears.keel.data.LocalIdentity
import com.therecalibrationyears.keel.data.MedicationRepository
import com.therecalibrationyears.keel.data.SettingsStore
import com.therecalibrationyears.keel.data.SymptomRepository
import com.therecalibrationyears.keel.checkin.CheckInDraft
import com.therecalibrationyears.keel.checkin.CheckInRequest
import com.therecalibrationyears.keel.domain.EnergyLevel
import com.therecalibrationyears.keel.data.UserRepository
import com.therecalibrationyears.keel.data.db.CheckInEntity
import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.domain.Mood
import com.therecalibrationyears.keel.domain.Pathway
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Port of the app-level parts of KeelTests/OnboardingStateTests.swift: whether she has
 * onboarded lives on her profile in the database. (The legacy Keychain-flag tests are
 * iOS-only; delete-all and restore arrive with those features.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class KeelAppControllerTests {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: MainDatabase
    private lateinit var prefsFile: File
    private val scopes = mutableListOf<CoroutineScope>()

    private val device = DeviceContext("AU", "en_AU", "Australia/Sydney", "1.0.0 (1)", "Google Pixel 8", "Android 16 (API 36)")

    @Before fun setUp() {
        db = MainDatabase.inMemory(RuntimeEnvironment.getApplication())
        prefsFile = File(tmp.root, "keel.preferences_pb")
    }

    @After fun tearDown() {
        scopes.forEach { it.cancel() }
        db.close()
    }

    private val prefs by lazy {
        PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { prefsFile }
    }

    /** A launch of the app over the same stored data. */
    /** A fixed Sydney clock, so "today" doesn't depend on when the test runs. */
    private val clock = object : KeelClock {
        override fun now(): Instant = Instant.parse("2026-10-21T01:00:00Z") // 12:00 in Sydney
        override fun zone(): ZoneId = ZoneId.of("Australia/Sydney")
    }

    private fun launch(): KeelAppController {
        val identity = LocalIdentity(prefs)
        val users = UserRepository(db.userProfiles(), identity::ownerId, clock) { device }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }
        val settings = SettingsStore(prefs)
        return KeelAppController(
            users, identity, settings, CheckInRepository(db.checkIns(), identity::ownerId, clock),
            SymptomRepository(db.symptoms(), db.checkIns(), settings, identity::ownerId, clock),
            MedicationRepository(db.medications(), identity::ownerId, clock),
            ActivityRepository(db.activityLogs(), clock, identity::ownerId),
            com.therecalibrationyears.keel.platform.SpeechRecognitionService(RuntimeEnvironment.getApplication()), clock, scope,
        )
    }

    private fun KeelAppController.onboarded(): Boolean = runBlocking {
        withTimeout(5_000) { hasCompletedOnboarding.filterNotNull().first() }
    }

    private fun KeelAppController.awaitOnboarded(expected: Boolean) = runBlocking {
        withTimeout(5_000) { hasCompletedOnboarding.first { it == expected } }
    }

    @Test fun aNewInstallIsNotOnboarded() = assertFalse(launch().onboarded())

    /** Port of testFinishingOnboardingPersistsAcrossLaunches. */
    @Test fun finishingOnboardingPersistsAcrossLaunches() = runBlocking {
        val app = launch()
        app.saveFirstName("Mischa")
        app.markOnboarded()
        app.awaitOnboarded(true)
        assertTrue(launch().onboarded()) // next launch
    }

    /** Port of testAProfileAloneIsNotOnboarded: quitting partway means onboarding again. */
    @Test fun aProfileAloneIsNotOnboarded() = runBlocking {
        launch().apply { saveFirstName("Mischa"); setPathway(Pathway.BOTH) }
        assertFalse(launch().onboarded())
    }

    @Test fun aBlankNameIsSavedAsThereUnderHerLocalIdentity() = runBlocking {
        val app = launch()
        app.saveFirstName("   ")
        val profile = db.userProfiles().current()!!
        assertEquals("there", profile.firstName)
        assertEquals(LocalIdentity(prefs).ownerId(), profile.ownerID)
        assertTrue(profile.ownerID.startsWith("local-"))
    }

    /** Finishing onboarding queues Home's one-line reminders explanation, not the system prompt. */
    @Test fun completingOnboardingQueuesTheRemindersExplanation() = runBlocking {
        val app = launch()
        assertFalse(SettingsStore(prefs).notificationExplainerPending.first())
        app.completeOnboarding()
        assertTrue(SettingsStore(prefs).notificationExplainerPending.first())
    }

    /** The launch screen waits for her saved theme, so a chosen theme never flashes the default. */
    @Test fun appearanceLoadsHerSavedThemeBeforeReady() = runBlocking {
        SettingsStore(prefs).setThemeId("slate")
        val app = launch()
        val appearance = withTimeout(5_000) { app.appearance.filterNotNull().first() }
        assertEquals("slate", appearance.themeId)
        app.onboarded()
        assertTrue(app.isReady)
    }

    /** Home sees her entries, medicines and chosen mood pack as one live snapshot. */
    @Test fun homeDataCombinesTheLiveTables() = runBlocking {
        val t = clock.now()
        db.checkIns().upsert(CheckInEntity("C1", t, Mood.GOOD.raw, 80, ownerID = "o", createdAt = t, updatedAt = t))
        db.medications().upsert(MedicationEntity("M1", "Magnesium", ownerID = "o", createdAt = t, updatedAt = t))
        SettingsStore(prefs).setMoodPackId("moon")
        val data = withTimeout(5_000) { launch().homeData.first { it.moodPackId == "moon" && it.checkIns.isNotEmpty() } }
        assertEquals(listOf("C1"), data.checkIns.map { it.id })
        assertEquals(listOf("Magnesium"), data.medications.map { it.name })
        assertTrue("nothing is imported before Health Connect", data.importedSleep.isEmpty())
    }

    /** Port of the iOS toggleMedicationFromHome contract: tick records the day, untick clears it. */
    @Test fun tickingAMedicineFromHomeRecordsAndClearsTheDay() = runBlocking {
        val app = launch()
        val t = clock.now()
        val med = MedicationEntity("M1", "Magnesium", ownerID = "o", createdAt = t, updatedAt = t).also { db.medications().upsert(it) }
        val today = t.atZone(clock.zone()).toLocalDate()
        app.toggleMedicationFromHome(med, today, currentlyTaken = false)
        assertEquals(1, withTimeout(5_000) { app.homeData.first { it.medicationLogs.isNotEmpty() } }.medicationLogs.size)
        app.toggleMedicationFromHome(med, today, currentlyTaken = true)
        assertTrue(withTimeout(5_000) { app.homeData.first { it.medicationLogs.isEmpty() } }.medicationLogs.isEmpty())
    }

    // Saving a check-in (port of iOS CheckInModal.save)

    private val sydney = ZoneId.of("Australia/Sydney")

    @Test fun aNewEntryIsStampedWithTodaysTimeOnTheChosenDay() = runBlocking {
        val app = launch()
        val day = java.time.LocalDate.of(2026, 10, 18) // a past day, picked on Home
        app.saveCheckIn(CheckInRequest(Mood.LOW, day), CheckInDraft(energy = EnergyLevel.LOW, notes = " Rough night "))
        val saved = db.checkIns().observeLive().first().single()
        assertEquals("2026-10-18T12:00+11:00[Australia/Sydney]", saved.date.atZone(sydney).toString()) // clock is 12:00
        assertEquals(40, saved.energy)
        assertEquals("Rough night", saved.notes)
    }

    @Test fun editingKeepsTheDayAndUpdatesInPlace() = runBlocking {
        val app = launch()
        val day = java.time.LocalDate.of(2026, 10, 18)
        app.saveCheckIn(CheckInRequest(Mood.LOW, day), CheckInDraft())
        val id = db.checkIns().observeLive().first().single().id
        val edit = app.editRequest(id)!!
        assertEquals(day, edit.day)
        app.saveCheckIn(edit.copy(mood = Mood.GREAT), CheckInDraft(energy = EnergyLevel.CHARGED))
        val after = db.checkIns().observeLive().first().single()
        assertEquals(Mood.GREAT, after.mood)
        assertEquals(100, after.energy)
        assertEquals("2026-10-18T12:00+11:00[Australia/Sydney]", after.date.atZone(sydney).toString())
    }

    /** Turning alcohol off later never erases a number she already recorded. */
    @Test fun alcoholIsOnlyWrittenWhileTheSettingIsOn() = runBlocking {
        val app = launch()
        val today = java.time.LocalDate.of(2026, 10, 21)
        app.saveCheckIn(CheckInRequest(Mood.OKAY, today), CheckInDraft(alcohol = 2))
        val id = db.checkIns().observeLive().first().single().id
        SettingsStore(prefs).setNotesAlcohol(false)
        app.saveCheckIn(CheckInRequest(Mood.OKAY, today, id), CheckInDraft(alcohol = null))
        assertEquals(2, db.checkIns().byId(id)!!.alcoholCount)
    }

    @Test fun archivedSymptomsAreDroppedAndSleepIsSavedForThatNight() = runBlocking {
        val app = launch()
        val today = java.time.LocalDate.of(2026, 10, 21)
        val repo = SymptomRepository(db.symptoms(), db.checkIns(), SettingsStore(prefs), { "o" }, clock)
        val kept = repo.findOrCreateCustom("Kept", "body")
        val gone = repo.findOrCreateCustom("Gone", "body").also { repo.archive(it) }
        app.saveCheckIn(CheckInRequest(Mood.OKAY, today), CheckInDraft(sleepHours = 6.5, selected = mapOf(kept.id to 2, gone.id to 3)))
        val id = db.checkIns().observeLive().first().single().id
        assertEquals(listOf(kept.id), db.checkIns().liveLinks(id).map { it.symptomID })
        assertEquals(6.5, ActivityRepository(db.activityLogs(), clock).manualSleep(today)!!, 0.0)
    }

    @Test fun bootstrapSeedsTheSymptomCatalog() = runBlocking {
        launch().bootstrap()
        withTimeout(5_000) { while (db.symptoms().count() == 0) kotlinx.coroutines.delay(20) }
        assertTrue(db.symptoms().count() >= 54)
    }
}
