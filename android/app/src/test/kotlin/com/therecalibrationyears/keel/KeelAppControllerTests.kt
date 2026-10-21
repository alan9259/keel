package com.therecalibrationyears.keel

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.therecalibrationyears.keel.data.DeviceContext
import com.therecalibrationyears.keel.data.LocalIdentity
import com.therecalibrationyears.keel.data.SettingsStore
import com.therecalibrationyears.keel.data.UserRepository
import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.domain.Pathway
import java.io.File
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
    private fun launch(): KeelAppController {
        val identity = LocalIdentity(prefs)
        val users = UserRepository(db.userProfiles(), identity::ownerId, KeelClock.System) { device }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }
        return KeelAppController(users, identity, SettingsStore(prefs), scope)
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
}
