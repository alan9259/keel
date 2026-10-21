package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.domain.DoseSchedule
import com.therecalibrationyears.keel.domain.DoseSlot
import com.therecalibrationyears.keel.domain.DoseUnit
import com.therecalibrationyears.keel.domain.MedicationMethod
import com.therecalibrationyears.keel.domain.MedicationSchedules
import com.therecalibrationyears.keel.domain.ReminderConfig
import com.therecalibrationyears.keel.domain.ScheduleWords
import com.therecalibrationyears.keel.domain.TreatmentCatalog
import com.therecalibrationyears.keel.domain.TreatmentDraft
import com.therecalibrationyears.keel.domain.TreatmentKind
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private object Words : ScheduleWords {
    override fun time(slot: DoseSlot): String = slot.hour?.let { "%d:%02d".format(it, slot.minute ?: 0) } ?: "Any time"
    override fun shortWeekday(weekday: Int): String = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")[weekday - 1]
    override val firstWeekday: Int = 2
}

/**
 * Port of KeelTests/MedicationRepositoryTests.swift against an in-memory Room store:
 * stop/reactivate, whole-day and per-slot ticks, and auto-log idempotency. Fixed UTC clock
 * at 10:00 on Wednesday 12 Aug 2026 rather than the wall clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MedicationRepositoryTests {
    private lateinit var db: MainDatabase
    private val clock = TestClock(Instant.parse("2026-08-12T10:00:00Z"))
    private lateinit var repo: MedicationRepository
    private val today = LocalDate.of(2026, 8, 12)

    @Before fun setUp() {
        db = inMemoryDatabase()
        repo = MedicationRepository(db.medications(), { "test-owner" }, clock, { Words })
    }

    @After fun tearDown() = db.close()

    private suspend fun makeMed(hour: Int? = 8, autoLog: Boolean = false, weekdays: Set<Int> = (1..7).toSet(), name: String = "Test"): MedicationEntity =
        repo.add(
            TreatmentDraft(
                name = name, kind = TreatmentKind.SUPPLEMENT, autoLogDoses = autoLog,
                schedule = DoseSchedule(slots = listOf(DoseSlot(weekdays = weekdays, hour = hour))),
            ),
        )

    @Test fun stopRecordsDateAndKeepsRecord_reactivateClearsIt() = runBlocking {
        val med = makeMed()
        assertTrue(med.isActive)
        val stopDate = Instant.ofEpochSecond(1_700_000_000)
        repo.update(med.id, med.toDraft(ZoneOffset.UTC).copy(isActive = false, stoppedAt = stopDate))
        val stopped = db.medications().byId(med.id)!!
        assertFalse(stopped.isActive)
        assertEquals(stopDate, stopped.stoppedAt)
        assertNull("stopping keeps the record, it is not a delete", stopped.deletedAt)
        assertFalse(repo.active().any { it.id == med.id })
        assertTrue(repo.stoppedTreatments().any { it.id == med.id })

        repo.update(med.id, stopped.toDraft(ZoneOffset.UTC).copy(isActive = true))
        val back = db.medications().byId(med.id)!!
        assertTrue(back.isActive)
        assertNull(back.stoppedAt)
        assertTrue(repo.active().any { it.id == med.id })
        assertFalse(repo.stoppedTreatments().any { it.id == med.id })
    }

    @Test fun stoppingWithoutADateLeavesItBlank() = runBlocking {
        val med = makeMed()
        repo.update(med.id, med.toDraft(ZoneOffset.UTC).copy(isActive = false, stoppedAt = null))
        val stopped = db.medications().byId(med.id)!!
        assertFalse(stopped.isActive)
        assertNull("no date entered means the stop date stays blank", stopped.stoppedAt)
    }

    @Test fun wholeDayTickAndRead() = runBlocking {
        val med = makeMed()
        assertFalse(repo.isTaken(med, today, null))
        repo.setTaken(med, today, null, taken = true)
        assertTrue(repo.isTaken(med, today, null))
    }

    @Test fun untickRemovesLog() = runBlocking {
        val med = makeMed()
        repo.setTaken(med, today, null, taken = true)
        repo.setTaken(med, today, null, taken = false)
        assertFalse(repo.isTaken(med, today, null))
    }

    @Test fun tickIsPerDay() = runBlocking {
        val med = makeMed()
        repo.setTaken(med, today, null, taken = true)
        assertTrue(repo.isTaken(med, today, null))
        assertFalse(repo.isTaken(med, today.minusDays(1), null))
    }

    @Test fun clearTakenWipesEverySlotForTheDay() = runBlocking {
        val med = makeMed()
        repo.setTaken(med, today, "slot-a", taken = true)
        repo.setTaken(med, today, "slot-b", taken = true)
        repo.clearTaken(med, today)
        assertFalse(repo.isTaken(med, today, "slot-a"))
        assertFalse(repo.isTaken(med, today, "slot-b"))
    }

    @Test fun autoLogLogsADuePastDoseOnce() = runBlocking {
        makeMed(hour = 1, autoLog = true)
        assertEquals("one due dose should auto-log", 1, repo.autoLogTodaysDueDoses().size)
        assertEquals("already-logged dose must not double-log", 0, repo.autoLogTodaysDueDoses().size)
    }

    @Test fun autoLogSkipsAFutureDoseToday() = runBlocking {
        makeMed(hour = 23, autoLog = true)
        assertEquals(0, repo.autoLogTodaysDueDoses().size)
    }

    @Test fun autoLogUntimedDoseLogsBySlotAsSoonAsDue() = runBlocking {
        val med = makeMed(hour = null, autoLog = true)
        val logged = repo.autoLogTodaysDueDoses()
        assertEquals(1, logged.size)
        assertEquals(med.schedule(ZoneOffset.UTC).slots.single().id, logged.single().slot)
    }

    @Test fun autoLogSkipsDayTheMedIsNotDue() = runBlocking {
        makeMed(hour = 1, autoLog = true, weekdays = setOf(2)) // Mondays; today is a Wednesday
        assertEquals(0, repo.autoLogTodaysDueDoses().size)
    }

    @Test fun autoLogIgnoresMedsWithoutAutoLog() = runBlocking {
        makeMed(hour = 1, autoLog = false)
        assertEquals(0, repo.autoLogTodaysDueDoses().size)
    }

    // Android additions: the upsert-by-name, the stored text, and the launch tidy-ups.

    @Test fun addingTheSameNameUpdatesInsteadOfDuplicating() = runBlocking {
        val first = repo.add(TreatmentDraft(name = "Magnesium", kind = TreatmentKind.SUPPLEMENT, doseAmount = 400.0, doseUnit = DoseUnit.MG))
        val again = repo.add(TreatmentDraft(name = "  MAGNESIUM ", kind = TreatmentKind.SUPPLEMENT, doseAmount = 200.0, doseUnit = DoseUnit.MG))
        assertEquals(first.id, again.id)
        assertEquals(1, repo.active().size)
        assertEquals("200mg", repo.active().single().dosage)
        assertEquals("MAGNESIUM", repo.active().single().name)
    }

    @Test fun savedTextAndColumnsComeFromTheDraft() = runBlocking {
        val med = repo.add(
            TreatmentDraft(
                name = "Oestrogen gel", kind = TreatmentKind.TREATMENT, method = MedicationMethod.GEL,
                doseAmount = 2.0, doseUnit = DoseUnit.PUMPS, note = "  switched brands  ",
                schedule = DoseSchedule(slots = listOf(DoseSlot(hour = 21, minute = 30))),
            ),
        )
        assertEquals("2 pumps", med.dosage)
        assertEquals("21:30", med.timing)
        assertEquals("gel", med.methodRaw)
        assertEquals("switched brands", med.note)
        assertEquals("weekly", med.scheduleKindRaw)
        assertNull("only a date she entered is recorded", med.date)
        assertEquals("2 pumps · Every day at 21:30", med.detailLine(Words, ZoneOffset.UTC))
        // A supplement never keeps a method.
        assertNull(repo.add(TreatmentDraft(name = "Zinc", kind = TreatmentKind.SUPPLEMENT, method = MedicationMethod.TABLET)).methodRaw)
    }

    @Test fun updateKeepsHerFirstStartDate() = runBlocking {
        val started = Instant.parse("2026-01-05T00:00:00Z")
        val med = repo.add(TreatmentDraft(name = "Vitamin D", kind = TreatmentKind.SUPPLEMENT, date = started))
        repo.update(med.id, med.toDraft(ZoneOffset.UTC).copy(date = null))
        assertEquals(started, db.medications().byId(med.id)!!.date)
    }

    @Test fun archiveRemovesItFromBothLists() = runBlocking {
        val med = makeMed()
        repo.archive(med.id)
        assertTrue(repo.active().isEmpty())
        assertTrue(repo.stoppedTreatments().isEmpty())
        assertNull(repo.byId(med.id))
    }

    @Test fun legacyEntriesMoveOntoASchedule() = runBlocking {
        val now = clock.now()
        db.medications().upsert(
            MedicationEntity(
                id = "LEGACY", name = "Cyclic progesterone", timing = "Evening", frequencyRaw = "cyclic", timeOfDayRaw = "evening",
                date = Instant.parse("2026-08-01T00:00:00Z"), ownerID = "o", createdAt = now, updatedAt = now,
            ),
        )
        repo.migrateLegacySchedules()
        val m = db.medications().byId("LEGACY")!!
        val s = m.schedule(ZoneOffset.UTC)
        assertEquals(DoseSchedule.Kind.CYCLE, s.kind)
        assertEquals(LocalDate.of(2026, 8, 1), s.anchor)
        assertEquals(20, s.slots.single().hour)
        assertEquals("20:00", m.timing)
        // Once only: a second run changes nothing.
        repo.migrateLegacySchedules()
        assertEquals(m.updatedAt, db.medications().byId("LEGACY")!!.updatedAt)
    }

    /** Port of KeelTests/MedicationNameCleanupTests.swift (repository part). */
    @Test fun cleanupRenamesSavedEntriesAndIsIdempotent() = runBlocking {
        val now = clock.now()
        db.medications().upsert(MedicationEntity(id = "OLD", name = "Oral micronised progesterone (e.g. Prometrium)", dosage = "100mg", timing = "Night", kindRaw = "treatment", ownerID = "o", createdAt = now, updatedAt = now))
        db.medications().upsert(MedicationEntity(id = "MINE", name = "Magnesium (glycinate)", dosage = "400mg", timing = "Night", ownerID = "o", createdAt = now, updatedAt = now))
        repo.stripExampleBrandsFromNames()
        repo.stripExampleBrandsFromNames()
        assertEquals("Oral micronised progesterone", db.medications().byId("OLD")!!.name)
        assertEquals("100mg", db.medications().byId("OLD")!!.dosage)
        assertEquals("Magnesium (glycinate)", db.medications().byId("MINE")!!.name)
    }

    @Test fun formReadsBackWhatWasSaved() = runBlocking {
        val med = makeMed(hour = 7)
        val draft = med.toDraft(ZoneOffset.UTC)
        assertEquals(7, draft.schedule.slots.single().hour)
        assertEquals("", draft.legacyTiming) // a structured time replaces the free text
        val old = MedicationEntity(id = "X", name = "Old", dosage = "a scoop", timing = "Mornings", ownerID = "o", createdAt = clock.now(), updatedAt = clock.now())
        assertEquals("a scoop", old.toDraft(ZoneOffset.UTC).legacyDosage)
        assertEquals("Mornings", old.toDraft(ZoneOffset.UTC).legacyTiming)
        // Nothing set yet: the form starts from "every day", not "as directed".
        val slot = old.toDraft(ZoneOffset.UTC).schedule.slots.single()
        assertEquals(DoseSchedule.Kind.WEEKLY, old.toDraft(ZoneOffset.UTC).schedule.kind)
        assertTrue(slot.isEveryDay && !slot.hasTime)
    }
}

/** Port of KeelTests/TreatmentCatalogTests.swift. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TreatmentCatalogTests {
    private fun group(id: String, names: List<String>) =
        TreatmentCatalog.Group(id = id, kindRaw = "treatment", section = id, title = id, items = names.map { TreatmentCatalog.Item(it) })

    @Test fun filterBlankQueryReturnsEverything() {
        val g = listOf(group("a", listOf("One", "Two")), group("b", listOf("Three")))
        assertEquals(2, TreatmentCatalog.filter(g, "   ").size)
        assertEquals(2, TreatmentCatalog.filter(g, "").size)
    }

    @Test fun filterKeepsOnlyMatchingItemsAndDropsEmptyGroups() {
        val g = listOf(group("oestrogen", listOf("Oestrogen patch", "Oestrogen gel")), group("progesterone", listOf("Progestogen IUD")))
        val result = TreatmentCatalog.filter(g, "gel")
        assertEquals(1, result.size)
        assertEquals(listOf("Oestrogen gel"), result.first().items.map { it.name })
    }

    @Test fun filterIsCaseInsensitiveAndSubstring() {
        val g = listOf(group("oestrogen", listOf("Oestrogen patch")))
        assertEquals(1, TreatmentCatalog.filter(g, "PATCH").first().items.size)
        assertEquals(1, TreatmentCatalog.filter(g, "oestro").first().items.size)
    }

    @Test fun filterNoMatchReturnsEmpty() {
        assertTrue(TreatmentCatalog.filter(listOf(group("oestrogen", listOf("Oestrogen patch"))), "vitamin").isEmpty())
    }

    @Test fun bundledTreatmentsHaveNoBrandsNoIndicationNotesNoEmpties() {
        val catalog = TreatmentCatalogStore(RuntimeEnvironment.getApplication()).catalog
        val treatments = catalog.groups(TreatmentKind.TREATMENT)
        assertFalse("catalog failed to load", treatments.isEmpty())
        for (g in treatments) {
            assertNull("${g.id} has an indication/why note", g.note)
            assertFalse("${g.id} is an empty category", g.items.isEmpty())
        }
        val brands = listOf("Estradot", "Estraderm", "Estrogel", "Estramon", "AndroFeme", "Testogel", "Prometrium", "Femoston", "Mirena")
        val names = treatments.flatMap { g -> g.items.map { it.name } }
        for (brand in brands) assertFalse("brand '$brand' is still listed", names.any { it.contains(brand, ignoreCase = true) })
    }

    // Android additions: the bundled copy is the iOS file, and a cached copy wins.

    @Test fun bundledCatalogIsTheIosFile() {
        val ios = File(System.getProperty("keel.repoRoot"), "Keel/Resources/treatment-catalog.json").readText()
        val bundled = TreatmentCatalogStore(RuntimeEnvironment.getApplication()).catalog
        assertEquals(TreatmentCatalog.decode(ios), bundled)
        assertTrue(bundled.groups(TreatmentKind.SUPPLEMENT).isNotEmpty())
    }

    @Test fun cachedCopyWinsAndABrokenOneIsIgnored() {
        val app = RuntimeEnvironment.getApplication()
        val cache = File(app.filesDir, "treatment-catalog.json")
        try {
            cache.writeText("""{"version":9,"groups":[{"id":"x","kind":"supplement","section":"S","title":"T","items":[{"name":"Iron"}],"extra":1}]}""")
            assertEquals(9, TreatmentCatalogStore(app).catalog.version)
            cache.writeText("{ broken")
            assertTrue(TreatmentCatalogStore(app).catalog.version > 0)
        } finally {
            cache.delete()
        }
    }

    @Test fun itemsSortAlphabeticallyIgnoringCase() {
        val g = group("s", listOf("zinc", "Ashwagandha", "CoQ10", "calcium"))
        assertEquals(listOf("Ashwagandha", "calcium", "CoQ10", "zinc"), g.sortedItems(java.util.Locale.forLanguageTag("en-AU")).map { it.name })
    }
}

/** The reminder settings: same keys and defaults as iOS. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderSettingsTests {
    private lateinit var settings: SettingsStore

    @Before fun setUp() {
        settings = SettingsStore(tempDataStore(Files.createTempDirectory("keel").toFile()))
    }

    /** Port of ReminderWordingTests.testTheSettingIsOffByDefault. */
    @Test fun medicineNamesInRemindersIsOffByDefault() = runBlocking {
        assertFalse(settings.medicineNamesInReminders.first())
    }

    @Test fun defaultsMatchIos() = runBlocking {
        assertTrue(settings.pushNotifications.first())
        assertEquals(setOf("dailyCheckIn", "medication"), settings.enabledReminderIds.first())
        assertEquals(ReminderConfig(), settings.reminderConfig.first())
        assertFalse(settings.notificationPermissionAsked.first())
    }

    @Test fun remindersAndTimingPersist() = runBlocking {
        settings.setReminderEnabled("hydration", true)
        settings.setReminderEnabled("medication", false)
        assertEquals(setOf("dailyCheckIn", "hydration"), settings.enabledReminderIds.first())
        val config = ReminderConfig(checkInHour = 7, checkInMinute = 15, movementWeekdaysOnly = false)
        settings.setReminderConfig(config)
        assertEquals(config, settings.reminderConfig.first())
    }
}
