package com.therecalibrationyears.keel

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.therecalibrationyears.keel.data.ActivityRepository
import com.therecalibrationyears.keel.data.CheckInRepository
import com.therecalibrationyears.keel.data.CycleRepository
import com.therecalibrationyears.keel.data.InsightRepository
import com.therecalibrationyears.keel.data.GPSummaryService
import com.therecalibrationyears.keel.designsystem.KeelDateFormats
import com.therecalibrationyears.keel.data.DeviceContext
import com.therecalibrationyears.keel.data.LocalIdentity
import com.therecalibrationyears.keel.data.MedicationRepository
import com.therecalibrationyears.keel.data.SettingsStore
import com.therecalibrationyears.keel.data.SymptomRepository
import com.therecalibrationyears.keel.data.RecordEraser
import com.therecalibrationyears.keel.data.AppLockService
import com.therecalibrationyears.keel.data.backup.BackupService
import com.therecalibrationyears.keel.data.db.HealthDatabase
import com.therecalibrationyears.keel.settings.AppInfo
import com.therecalibrationyears.keel.checkin.CheckInDraft
import com.therecalibrationyears.keel.checkin.CheckInRequest
import com.therecalibrationyears.keel.domain.EnergyLevel
import com.therecalibrationyears.keel.data.UserRepository
import com.therecalibrationyears.keel.data.db.CheckInEntity
import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.domain.Mood
import com.therecalibrationyears.keel.domain.DoseSchedule
import com.therecalibrationyears.keel.domain.DoseSlot
import com.therecalibrationyears.keel.domain.Pathway
import com.therecalibrationyears.keel.domain.TreatmentDraft
import com.therecalibrationyears.keel.domain.TreatmentKind
import com.therecalibrationyears.keel.data.TreatmentCatalogStore
import com.therecalibrationyears.keel.data.schedule
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

    private lateinit var health: HealthDatabase

    @Before fun setUp() {
        db = MainDatabase.inMemory(RuntimeEnvironment.getApplication())
        health = HealthDatabase.inMemory(RuntimeEnvironment.getApplication())
        prefsFile = File(tmp.root, "keel.preferences_pb")
    }

    @After fun tearDown() {
        scopes.forEach { it.cancel() }
        db.close()
        health.close()
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

    /** Reminders armed by the app under test (no alarm service needed). */
    private val scheduler = FakeScheduler()
    private var exactAllowed = true
    private var postedCleared = 0
    /** The PIN as the Keystore would hold it, in memory, shared across launches like the real file. */
    private val pinStore = object : com.therecalibrationyears.keel.data.PINSecureStore {
        var stored: Pair<ByteArray, ByteArray>? = null
        override suspend fun hasPIN() = stored != null
        override suspend fun save(salt: ByteArray, hash: ByteArray) { stored = salt to hash }
        override suspend fun load() = stored
        override suspend fun clear() { stored = null }
    }
    private val biometricsFake = object : com.therecalibrationyears.keel.domain.BiometricAuthenticating {
        override fun canAuthenticate() = false
        override suspend fun authenticate(reason: String) = com.therecalibrationyears.keel.domain.BiometricOutcome.CANCELLED
        override fun canAuthenticateOwner() = false
        override suspend fun authenticateOwner(reason: String) = false
    }
    private var sharedFilesCleared = 0
    /** Health Connect as a fake, shared across launches like the real one. */
    private val healthSource = FakeHealthSource()
    private val healthPermissions = FakeHealthPermissions(healthSource)

    private fun launch(): KeelAppController {
        val identity = LocalIdentity(prefs)
        val users = UserRepository(db.userProfiles(), identity::ownerId, clock) { device }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }
        val settings = SettingsStore(prefs)
        val medications = MedicationRepository(db.medications(), identity::ownerId, clock)
        val checkIns = CheckInRepository(db.checkIns(), identity::ownerId, clock)
        val cycle = CycleRepository(db.cycle(), identity::ownerId, clock)
        return KeelAppController(
            users, identity, settings, checkIns,
            SymptomRepository(db.symptoms(), db.checkIns(), settings, identity::ownerId, clock),
            medications,
            ActivityRepository(db.activityLogs(), clock, identity::ownerId),
            cycle,
            InsightRepository(db.insights(), db.checkIns(), db.cycle(), identity::ownerId, clock),
            GPSummaryService(checkIns, medications, cycle, users, clock) { KeelDateFormats(RuntimeEnvironment.getApplication(), zone = clock.zone()) },
            com.therecalibrationyears.keel.platform.SpeechRecognitionService(RuntimeEnvironment.getApplication()),
            TreatmentCatalogStore(RuntimeEnvironment.getApplication()),
            KeelReminders(settings, medications, scheduler, clock, exactAlarmsAllowed = { exactAllowed }, clearPosted = { postedCleared++ }) { true },
            clock, scope,
            records = RecordEraser(db, health),
            backup = BackupService(db, health, clock) { "1.0.0" },
            lock = AppLockService(pinStore, biometricsFake, prefs, clock).also { runBlocking { it.load() } },
            appInfo = AppInfo("1.0.0", "1.0.0 (1)", "Android 16 (API 36)", "Google Pixel 8"),
            clearSharedFiles = { sharedFilesCleared++ },
            health = KeelHealth(
                healthSource, healthPermissions,
                com.therecalibrationyears.keel.data.HealthIngestor(health, db, identity::ownerId, clock),
                com.therecalibrationyears.keel.data.HealthRepository(health, clock),
                users, settings, clock, scope, retryDelays = emptyList(),
            ),
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
        withTimeout(5_000) { while (db.symptoms().count() == 0) delay(20) }
        assertTrue(db.symptoms().count() >= 54)
    }

    // M3: the Medications screen's actions and the launch steps for medicines.

    /**
     * Regression (found on the emulator, M8): Android starts Keel's process in the background to
     * show a reminder, and the launch work used to log auto-logged doses then, on a day she
     * never opened Keel. Logging waits until she does (D22, as iOS).
     */
    @Test fun aBackgroundStartLogsNothingUntilSheOpensKeel() = runBlocking {
        val app = launch()
        app.medicationsActions.add(TreatmentDraft(name = "Magnesium", kind = TreatmentKind.SUPPLEMENT, schedule = DoseSchedule(slots = listOf(DoseSlot(hour = 8)))))
        val med = db.medications().active().single()
        db.medications().upsert(med.copy(autoLogDoses = true)) // due at 8:00; it's 12:00
        app.bootstrap()
        app.awaitLaunchWork()
        assertTrue("nothing logged by the launch work alone", db.medications().observeLiveLogs().first().isEmpty())
        assertTrue("its reminders are laid out", scheduler.armed.keys.any { it.startsWith("keel.med.${med.id}") })
        app.onForeground()
        withTimeout(5_000) { while (db.medications().observeLiveLogs().first().isEmpty()) delay(20) }
        assertEquals(med.id, db.medications().observeLiveLogs().first().single().medicationID)
    }

    /** A reboot, a clock change or the daily top-up re-arms reminders and logs nothing (D22). */
    @Test fun rearmingRemindersLogsNothing() = runBlocking {
        val app = launch()
        app.medicationsActions.add(TreatmentDraft(name = "Magnesium", kind = TreatmentKind.SUPPLEMENT, schedule = DoseSchedule(slots = listOf(DoseSlot(hour = 8)))))
        val med = db.medications().active().single()
        db.medications().upsert(med.copy(autoLogDoses = true))
        scheduler.armed.clear()
        app.rearmReminders()
        assertTrue(db.medications().observeLiveLogs().first().isEmpty())
        assertTrue(scheduler.armed.keys.any { it.startsWith("keel.med.${med.id}") })
    }

    @Test fun addingWithAutoLogTicksTodayStraightAway() = runBlocking {
        val app = launch()
        app.medicationsActions.add(
            TreatmentDraft(
                name = "Magnesium", kind = TreatmentKind.SUPPLEMENT, autoLogDoses = true,
                schedule = DoseSchedule(slots = listOf(DoseSlot(hour = 8))),
            ),
        )
        val med = app.medicationsActions.active.first().single()
        val logs = db.medications().observeLiveLogs().first()
        assertEquals(1, logs.size) // 8:00 has passed at 12:00 in Sydney
        assertEquals(med.id, logs.single().medicationID)
        assertEquals(LocalDate.of(2026, 10, 21).atStartOfDay(clock.zone()).toInstant(), logs.single().date)
    }

    @Test fun bootstrapMovesOlderEntriesOntoSchedulesAndTidiesNames() = runBlocking {
        val now = clock.now()
        db.medications().upsert(
            MedicationEntity(
                id = "OLD", name = "Oral micronised progesterone (e.g. Prometrium)", frequencyRaw = "daily", timeOfDayRaw = "evening",
                ownerID = "o", createdAt = now, updatedAt = now,
            ),
        )
        launch().bootstrap()
        val m = withTimeout(5_000) {
            var found: MedicationEntity? = null
            while (found?.scheduleKindRaw == null || found.name.contains("(e.g.")) {
                found = db.medications().byId("OLD"); delay(20)
            }
            found
        }
        assertEquals("Oral micronised progesterone", m.name)
        assertEquals("weekly", m.scheduleKindRaw)
    }

    @Test fun removingTakesItOffBothLists() = runBlocking {
        val app = launch()
        app.medicationsActions.add(TreatmentDraft(name = "Iron", kind = TreatmentKind.SUPPLEMENT))
        val id = app.medicationsActions.active.first().single().id
        app.medicationsActions.setTracked(id, false)
        assertFalse(app.medicationsActions.active.first().single().isTracked)
        app.medicationsActions.remove(id)
        assertTrue(app.medicationsActions.active.first().isEmpty())
        assertTrue(app.medicationsActions.stopped.first().isEmpty())
    }

    // M3 reminders: the controller's side (iOS AppEnvironment).

    /** Port of ReminderExplainerTests.testFinishingOnboardingQueuesTheExplanationNotThePrompt. */
    @Test fun finishingOnboardingQueuesTheExplanationNotThePrompt() = runBlocking {
        val app = launch()
        assertFalse(app.notificationExplainerPending.first())
        app.completeOnboarding()
        assertTrue(app.notificationExplainerPending.first())
        assertTrue(scheduler.armed.isEmpty()) // nothing laid out, nothing asked, until Continue
    }

    @Test fun markTakenFromAReminderLogsTheDoseAndDropsItsReminder() = runBlocking {
        val app = launch()
        app.medicationsActions.add(TreatmentDraft(name = "Prometrium", kind = TreatmentKind.TREATMENT, schedule = DoseSchedule(slots = listOf(DoseSlot(hour = 20)))))
        val med = app.medicationsActions.active.first().single()
        val slot = med.schedule(clock.zone()).slots.single().id
        assertTrue(scheduler.armed.keys.any { it.endsWith(".d20261021") })
        app.markMedicationTaken(med.id, slot, LocalDate.of(2026, 10, 21))
        assertFalse(scheduler.armed.keys.any { it.endsWith(".d20261021") })
        assertEquals(slot, db.medications().observeLiveLogs().first().single().slot)
    }

    @Test fun homeTickDropsTheDaysRemindersAndUntickBringsThemBack() = runBlocking {
        val app = launch()
        app.medicationsActions.add(TreatmentDraft(name = "Prometrium", kind = TreatmentKind.TREATMENT, schedule = DoseSchedule(slots = listOf(DoseSlot(hour = 20)))))
        val med = app.medicationsActions.active.first().single()
        val day = LocalDate.of(2026, 10, 21)
        app.toggleMedicationFromHome(med, day, currentlyTaken = false)
        assertFalse(scheduler.armed.keys.any { it.endsWith(".d20261021") })
        app.toggleMedicationFromHome(med, day, currentlyTaken = true)
        assertTrue(scheduler.armed.keys.any { it.endsWith(".d20261021") })
    }

    @Test fun alwaysMarkTakenTurnsOnAutoLogAndRewordsItsReminders() = runBlocking {
        val app = launch()
        app.medicationsActions.add(TreatmentDraft(name = "Prometrium", kind = TreatmentKind.TREATMENT, schedule = DoseSchedule(slots = listOf(DoseSlot(hour = 20)))))
        val med = app.medicationsActions.active.first().single()
        app.enableMedicationAutoLog(med.id, null, LocalDate.of(2026, 10, 21))
        assertTrue(db.medications().byId(med.id)!!.autoLogDoses)
        assertTrue(scheduler.armed.values.all { it.first.autoLog })
        assertFalse(scheduler.armed.keys.any { it.endsWith(".d20261021") }) // today's is logged
    }

    @Test fun removingAMedicineCancelsItsReminders() = runBlocking {
        val app = launch()
        app.medicationsActions.add(TreatmentDraft(name = "Prometrium", kind = TreatmentKind.TREATMENT, schedule = DoseSchedule(slots = listOf(DoseSlot(hour = 20)))))
        assertTrue(scheduler.armed.isNotEmpty())
        app.medicationsActions.remove(app.medicationsActions.active.first().single().id)
        assertTrue(scheduler.armed.isEmpty())
    }

    @Test fun remindersScreenTogglesAndTimingReschedule() = runBlocking {
        val app = launch()
        app.remindersActions.setEnabled("winddown", true)
        assertTrue("keel.winddown" in scheduler.armed)
        app.remindersActions.setConfig(com.therecalibrationyears.keel.domain.ReminderConfig(windDownHour = 22, windDownMinute = 0))
        assertEquals(22, scheduler.armed["keel.winddown"]!!.second.hour)
        app.remindersActions.setEnabled("winddown", false)
        assertFalse("keel.winddown" in scheduler.armed)
    }

    /** Android only (plan D25): saving a timed medicine explains, once, how to get it on time. */
    @Test fun savingATimedMedicineOffersExactTimingOnce() = runBlocking {
        exactAllowed = false
        val app = launch()
        assertFalse(app.exactTimingOfferPending.value)
        app.medicationsActions.add(TreatmentDraft(name = "Prometrium", kind = TreatmentKind.TREATMENT, schedule = DoseSchedule(slots = listOf(DoseSlot(hour = 20)))))
        assertTrue(app.exactTimingOfferPending.value)
        app.answerExactTimingOffer()
        assertFalse(app.exactTimingOfferPending.value)
        val id = app.medicationsActions.active.first().single().id
        app.medicationsActions.update(id, TreatmentDraft(name = "Prometrium", kind = TreatmentKind.TREATMENT, schedule = DoseSchedule(slots = listOf(DoseSlot(hour = 21)))))
        assertFalse(app.exactTimingOfferPending.value)
        assertFalse(launch().exactTimingOfferPending.value) // nor after a relaunch
    }

    @Test fun exactTimingIsNotOfferedWhenRemindersAreAlreadyOnTimeOrUntimed() = runBlocking {
        exactAllowed = false
        val app = launch()
        app.medicationsActions.add(TreatmentDraft(name = "Magnesium", kind = TreatmentKind.SUPPLEMENT, schedule = DoseSchedule()))
        assertFalse(app.exactTimingOfferPending.value) // "Any time": no reminders to deliver
        exactAllowed = true
        app.medicationsActions.add(TreatmentDraft(name = "Prometrium", kind = TreatmentKind.TREATMENT, schedule = DoseSchedule(slots = listOf(DoseSlot(hour = 20)))))
        assertFalse(app.exactTimingOfferPending.value)
    }

    // M4: Cycle and Looking back.

    /** Port of testCurrentReadsThisMonthFromTheStore (KeelTests/LookingBackTests.swift). */
    @Test fun lookingBackReadsThisMonthsLiveCheckIns() = runBlocking {
        val app = launch()
        val repo = CheckInRepository(db.checkIns(), { "o" }, clock)
        val zone = clock.zone()
        fun on(day: Int, month: Int = 10) = LocalDate.of(2026, month, day).atTime(9, 0).atZone(zone).toInstant()
        val symptom = SymptomRepository(db.symptoms(), db.checkIns(), SettingsStore(prefs), { "o" }, clock).findOrCreateCustom("Hot flushes", "vasomotor")
        repo.create(Mood.OKAY, 60, "In the month", listOf(com.therecalibrationyears.keel.data.SymptomPick(symptom.id, 2)), on(3))
        repo.create(Mood.OKAY, 60, "Last month", emptyList(), on(30, month = 9))
        val deleted = repo.create(Mood.OKAY, 60, "Deleted", emptyList(), on(4))
        repo.delete(deleted.id)

        val data = app.lookingBackActions.data.first()
        assertEquals(1, data.summary.daysCheckedIn)
        assertEquals(listOf("In the month"), data.summary.notes.map { it.text })
        assertEquals(listOf(com.therecalibrationyears.keel.domain.MonthSummary.SymptomCount("Hot flushes", 1)), data.summary.topSymptoms)
        assertEquals("October so far", data.summary.heading)
        assertTrue(data.hasAnyNotes)
        // "See all notes" covers every month, newest first.
        assertEquals(listOf("In the month", "Last month"), app.lookingBackActions.allNotes.first().map { it.text })
    }

    @Test fun launchDerivesTheCycleLengthCardForLookingBackAndCycle() = runBlocking {
        val app = launch()
        val cycle = CycleRepository(db.cycle(), { "o" }, clock)
        val today = LocalDate.of(2026, 10, 21)
        listOf(95L, 70L, 35L).forEach { cycle.setFlow(com.therecalibrationyears.keel.domain.FlowLevel.MEDIUM, today.minusDays(it)) }
        val repo = CheckInRepository(db.checkIns(), { "o" }, clock)
        (0L until 5L).forEach { repo.create(Mood.OKAY, 60, null, emptyList(), today.minusDays(it).atTime(9, 0).atZone(clock.zone()).toInstant()) }

        assertEquals("Your cycle length", app.cycleActions.data.first().finding?.title) // live on the Cycle screen
        app.bootstrap()
        val cards = withTimeout(5_000) { app.lookingBackActions.data.first { it.insights.isNotEmpty() }.insights }
        assertEquals("Your recent cycles ranged from about 25 to 35 days apart. That's the kind of detail that can be useful to bring to your GP.", cards.single().detail)
    }

    @Test fun loggingFromTheCycleScreenUpdatesItLive() = runBlocking {
        val app = launch()
        val today = LocalDate.of(2026, 10, 21)
        app.cycleActions.setFlow(com.therecalibrationyears.keel.domain.FlowLevel.HEAVY, today)
        val days = app.cycleActions.data.first { it.days.isNotEmpty() }.days
        assertEquals(listOf(today to com.therecalibrationyears.keel.domain.FlowLevel.HEAVY), days.map { it.first to it.second.level })
        assertTrue(app.cycleActions.data.first().imported.isEmpty()) // nothing imported before Health Connect
    }

    @Test fun activitiesWritesComeBackLiveAndNothingIsImportedYet() = runBlocking {
        val app = launch()
        val today = LocalDate.of(2026, 10, 21)
        app.activitiesActions.setAmount("water", today, 2.0)
        app.activitiesActions.setEating("eat.protein", today, false)
        app.saveCheckIn(CheckInRequest(Mood.GOOD, today), CheckInDraft(energy = EnergyLevel.LOW))
        val data = app.activitiesActions.data.first { it.manual.size == 2 && it.feelings.isNotEmpty() }
        assertEquals(setOf("water" to 2.0, "eat.protein" to 0.0), data.manual.map { it.activityId to it.amount }.toSet())
        assertEquals(Mood.GOOD, data.feelings.single().mood)
        assertTrue(data.imported.isEmpty() && data.readings.isEmpty()) // Health Connect arrives in M7
        assertFalse(data.healthConnected)
        app.activitiesActions.setEating("eat.protein", today, null) // cleared: not answered
        assertEquals(listOf("water"), app.activitiesActions.data.first { it.manual.size == 1 }.manual.map { it.activityId })
    }

    // M6: Delete all my data.

    private fun rows(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    /**
     * Port of KeelTests/EraseAllDataTests.swift `testDeletesEverythingAndResetsTheRest`:
     * everything Keel holds on the phone goes, including reminders and her preferences. (The
     * app lock and imported health data are erased too once they exist on Android.)
     */
    @Test fun deletesEverythingAndResetsTheRest() = runBlocking {
        val app = launch()
        app.saveFirstName("Mischa")
        app.markOnboarded()
        app.awaitOnboarded(true)
        app.bootstrap()
        withTimeout(5_000) { while (db.symptoms().count() == 0) delay(20) }
        app.saveCheckIn(CheckInRequest(Mood.LOW, java.time.LocalDate.of(2026, 10, 21)), CheckInDraft(energy = EnergyLevel.LOW, notes = "rough", sleepHours = 5.0))
        app.medicationsActions.add(TreatmentDraft(name = "Oestrogel", kind = TreatmentKind.TREATMENT, schedule = DoseSchedule(slots = listOf(DoseSlot(hour = 20)))))
        app.cycleActions.setFlow(com.therecalibrationyears.keel.domain.FlowLevel.MEDIUM, java.time.LocalDate.of(2026, 10, 20))
        val store = SettingsStore(prefs)
        store.setNotesAlcohol(false) // she had turned it off
        store.setDisabledHealthItemIds(setOf("steps"))
        store.setReminderEnabled("hydration", true)
        store.setThemeId("forest")
        store.setNotificationPermissionAsked(true)
        health.samples().insertAll(
            listOf(com.therecalibrationyears.keel.data.db.HealthSampleEntity("H1", "heartRate", clock.now(), 70.0, "bpm", ownerID = "o", createdAt = clock.now(), updatedAt = clock.now())),
        )
        assertTrue(app.lock.setPIN("2468"))
        assertTrue(scheduler.armed.isNotEmpty())
        assertTrue(app.connectHealth())
        assertTrue(store.healthAskedPermissions.first().isNotEmpty())

        app.eraseAllData()

        // Regression (iOS): the app lock and its PIN survived "delete everything".
        assertFalse(app.lock.state.value!!.isEnabled)
        assertFalse(app.lock.verifyPIN("2468"))
        assertFalse("off after a relaunch too", launch().lock.state.value!!.isEnabled)

        assertTrue("imported readings go too", health.samples().all().isEmpty())
        assertEquals("Keel's Health Connect access is revoked (plan D4)", 1, healthSource.revokes.get())

        // Every row, soft-deleted ones included.
        listOf("user_profile", "check_in", "check_in_symptom", "medication", "medication_log", "cycle_entry", "insight", "activity_log")
            .forEach { assertEquals(it, 0, rows(it)) }
        assertTrue("built-ins re-seeded for the next person", db.symptoms().count() >= 54)
        assertTrue(db.symptoms().observeActive().first().none { it.isCustom })

        // Regression (iOS): reminders and her preferences survived "delete everything".
        assertTrue(scheduler.armed.isEmpty())
        assertEquals(1, postedCleared)
        assertTrue(store.notesAlcohol.first()) // back to the default (on)
        assertTrue(store.disabledHealthItemIds.first().isEmpty())
        assertEquals(setOf("dailyCheckIn", "medication"), store.enabledReminderIds.first())
        assertEquals(null, store.themeId.first())
        assertTrue("the phone's permission state is kept", store.notificationPermissionAsked.first())
        assertTrue("Health Connect's asked set goes with her answers", store.healthAskedPermissions.first().isEmpty())

        assertTrue(LocalIdentity(prefs).ownerId().isEmpty()) // local identity cleared
        app.awaitOnboarded(false)
        assertEquals(1, sharedFilesCleared) // the GP summary made for sharing
    }

    private suspend fun importIntoHealthStore(snapshot: com.therecalibrationyears.keel.domain.HealthSnapshot) {
        com.therecalibrationyears.keel.data.HealthIngestor(health, db, { "o" }, clock).ingest(snapshot)
    }

    /** What Health Connect brought in reaches the screens that show it: Home's sleep, Cycle, Activities and Stats. */
    @Test fun importedHealthDataReachesTheScreensThatShowIt() = runBlocking {
        val app = launch()
        app.saveFirstName("Mischa")
        val day = java.time.LocalDate.of(2026, 10, 20)
        importIntoHealthStore(
            com.therecalibrationyears.keel.domain.HealthSnapshot(
                sleepByDay = mapOf(day to 6.5),
                activityAmounts = mapOf("steps" to mapOf(day to 8000.0)),
                vitals = listOf(com.therecalibrationyears.keel.domain.HealthSnapshot.VitalSeries("restingHeartRate", "bpm", mapOf(day to 60.0))),
                menstrualFlow = mapOf(day to com.therecalibrationyears.keel.domain.FlowLevel.LIGHT),
            ),
        )
        val sleep = com.therecalibrationyears.keel.domain.DayAmount("sleep", day, 6.5)
        val reading = com.therecalibrationyears.keel.insights.HealthReading("restingHeartRate", day, 60.0)

        assertEquals(listOf(sleep), app.homeData.first().importedSleep)
        assertEquals(mapOf(day to com.therecalibrationyears.keel.domain.FlowLevel.LIGHT), app.cycleActions.data.first().imported)
        val activities = app.activitiesActions.data.first()
        assertEquals(setOf(sleep, com.therecalibrationyears.keel.domain.DayAmount("steps", day, 8000.0)), activities.imported.toSet())
        assertEquals(listOf(reading), activities.readings)
        val stats = app.statsActions.data.first()
        assertEquals(listOf(sleep), stats.importedSleep)
        assertEquals(listOf(reading), stats.readings)
        assertTrue("her own cycle entries stay hers alone", db.cycle().observeLive().first().isEmpty())
    }

    /**
     * Port of the iOS check-in's `loadSleep`: her own night is editable and wins; a night only
     * Health Connect has shows read-only, and saving never writes it into her entries.
     */
    @Test fun checkInSleepIsHersOrHealthConnectsReadOnly() = runBlocking {
        val app = launch()
        app.saveFirstName("Mischa")
        val hers = java.time.LocalDate.of(2026, 10, 19)
        val imported = java.time.LocalDate.of(2026, 10, 20)
        importIntoHealthStore(com.therecalibrationyears.keel.domain.HealthSnapshot(sleepByDay = mapOf(hers to 6.0, imported to 6.5)))
        app.saveCheckIn(CheckInRequest(Mood.GOOD, hers), CheckInDraft(sleepHours = 8.0))

        val own = app.checkInActions.loadDraft(CheckInRequest(Mood.GOOD, hers))
        assertEquals(8.0, own.sleepHours!!, 0.0)
        assertFalse(own.sleepFromHealth)

        val fromHealth = app.checkInActions.loadDraft(CheckInRequest(Mood.GOOD, imported))
        assertEquals(6.5, fromHealth.sleepHours!!, 0.0)
        assertTrue(fromHealth.sleepFromHealth)
        app.saveCheckIn(CheckInRequest(Mood.GOOD, imported), fromHealth)
        val manualNights = app.activitiesActions.data.first().manual.filter { it.activityId == "sleep" }
        assertEquals("only her own night is in her entries", listOf(hers), manualNights.map { it.day })

        val none = app.checkInActions.loadDraft(CheckInRequest(Mood.GOOD, java.time.LocalDate.of(2026, 10, 21)))
        assertEquals(null, none.sleepHours)
        assertFalse(none.sleepFromHealth)
    }

    /** Port of HealthAccessOfferTests.testNothingOfferedBeforeOnboardingWhichHasItsOwnConnectStep, through the controller. */
    @Test fun healthOfferWaitsForOnboarding() = runBlocking {
        val app = launch()
        app.saveFirstName("Mischa")
        assertEquals(com.therecalibrationyears.keel.domain.HealthOffer.NONE, app.checkHealthAccessOnLaunch())
        app.markOnboarded()
        assertEquals(com.therecalibrationyears.keel.domain.HealthOffer.CONNECT, app.checkHealthAccessOnLaunch())
        assertTrue("once per launch", app.claimHealthOfferCheck())
        assertFalse(app.claimHealthOfferCheck())
    }

    /** iOS ran EraseAllDataTests repeatedly after a profile survived a bulk delete; so does this. */
    @Test fun deletingTwentyTimesLeavesNothingBehind() = runBlocking {
        repeat(20) {
            val app = launch()
            app.saveFirstName("Mischa")
            app.saveCheckIn(CheckInRequest(Mood.GOOD, java.time.LocalDate.of(2026, 10, 21)), CheckInDraft())
            app.eraseAllData()
            assertEquals(0, rows("user_profile"))
            assertEquals(0, rows("check_in"))
        }
    }

    // M6: Profile.

    /** Port of the Profile screen's save (iOS `ProfileView.save`): details and background together. */
    @Test fun profileSavesHerDetailsAndBackgroundTogether() = runBlocking {
        val app = launch()
        app.saveFirstName("") // skipped her name during onboarding
        assertEquals("", app.profileActions.load().firstName) // the placeholder isn't shown as her name
        app.profileActions.save(
            com.therecalibrationyears.keel.domain.ProfileForm(
                firstName = " Mischa ", lastName = "Wang", age = "49", email = " m@example.com ",
                hysterectomy = com.therecalibrationyears.keel.domain.Hysterectomy.YES_ONE_OVARY_REMOVED, hysterectomyYear = "2019",
                periodsNote = " after a hysterectomy ",
            ),
        )
        val p = db.userProfiles().current()!!
        assertEquals("Mischa", p.firstName)
        assertEquals(1977, p.birthYear) // the clock is in 2026
        assertEquals("m@example.com", p.email)
        assertEquals(null, p.mobile)
        assertEquals(2019, p.hysterectomyYear)
        assertEquals("after a hysterectomy", p.periodsNotApplicableReason)
        val reloaded = launch().profileActions.load()
        assertEquals("49", reloaded.age)
        assertEquals(com.therecalibrationyears.keel.domain.Hysterectomy.YES_ONE_OVARY_REMOVED, reloaded.hysterectomy)
        assertEquals("2019", reloaded.hysterectomyYear)
    }

    // M6: Backup & Restore.

    private fun iphoneBackup(): ByteArray {
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "fixtures").isDirectory }
        return java.io.File(root, "fixtures/backup/v1-ios-sample.keelbackup").readBytes()
    }

    /**
     * Restoring from inside the app (iOS `performRestore`): she stays onboarded here, older iOS
     * medicine entries are carried onto schedules, and the restored medicines' reminders are laid
     * out while those of the medicines it replaced are gone.
     */
    @Test fun restoringAnIphoneBackupKeepsHerHereAndReArmsReminders() = runBlocking {
        val app = launch()
        app.saveFirstName("Rose")
        app.markOnboarded()
        app.medicationsActions.add(TreatmentDraft(name = "Iron", kind = TreatmentKind.SUPPLEMENT, schedule = DoseSchedule(slots = listOf(DoseSlot(hour = 9)))))
        val ironId = db.medications().observeActive().first().single().id
        assertTrue(scheduler.ids("keel.med.$ironId").isNotEmpty())

        val preview = app.backupActions.read(iphoneBackup())!!
        assertEquals(3, preview.checkIns)
        val outcome = app.backupActions.restorePending()
        assertTrue(outcome is com.therecalibrationyears.keel.settings.RestoreOutcome.Done)

        app.awaitOnboarded(true)
        assertEquals("Mischa", db.userProfiles().current()!!.firstName)
        assertTrue("the replaced medicine's reminders are gone", scheduler.ids("keel.med.$ironId").isEmpty())
        val gel = db.medications().observeActive().first().single { it.name == "Estradiol gel" }
        assertTrue("the restored one's are armed", scheduler.ids("keel.med.${gel.id}").isNotEmpty())
        val magnesium = db.medications().observeActive().first().single { it.name == "Magnesium" }
        assertTrue("an older entry is carried onto a schedule", magnesium.scheduleKindRaw != null)
        assertTrue(db.medications().observeActive().first().any { it.name == "Progesterone" }) // example brand tidied away
        assertEquals(2, health.samples().all().size)
    }

    @Test fun somethingElseIsNotRestored() = runBlocking {
        val app = launch()
        app.saveFirstName("Rose")
        assertEquals(null, app.backupActions.read("not a backup".toByteArray()))
        assertEquals(com.therecalibrationyears.keel.settings.RestoreOutcome.Refused(null), app.backupActions.restorePending())
        assertEquals("Rose", db.userProfiles().current()!!.firstName)
    }
}
