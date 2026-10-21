package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.data.db.SymptomEntity
import com.therecalibrationyears.keel.domain.Mood
import com.therecalibrationyears.keel.domain.SymptomCatalog
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * Ports of KeelTests/CheckInRepositoryTests.swift, SymptomRepositoryTests.swift, the
 * repository half of QuickChipRankingTests.swift and the recording half of AlcoholTests.swift.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CheckInDataTests {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: MainDatabase
    private val clock = TestClock()
    private lateinit var settings: SettingsStore
    private lateinit var checkIns: CheckInRepository
    private lateinit var symptoms: SymptomRepository

    @Before fun setUp() {
        db = inMemoryDatabase()
        settings = SettingsStore(tempDataStore(tmp.root))
        checkIns = CheckInRepository(db.checkIns(), { "local-TEST" }, clock)
        symptoms = SymptomRepository(db.symptoms(), db.checkIns(), settings, { "local-TEST" }, clock)
    }

    @After fun tearDown() = db.close()

    private suspend fun custom(name: String) = symptoms.findOrCreateCustom(name, "body")
    private suspend fun names(checkInId: String) =
        checkIns.picks(checkInId).map { p -> db.symptoms().all().first { it.id == p.symptomId }.name }.sorted()

    // CheckInRepositoryTests

    @Test fun createWithSymptoms() = runBlocking {
        val hot = custom("Hot flushes")
        val entry = checkIns.create(Mood.OKAY, 60, "note", listOf(SymptomPick(hot.id, 2)), clock.now())
        assertEquals(listOf("Hot flushes"), names(entry.id))
        assertEquals(1, checkIns.observeLive().first().size)
    }

    @Test fun createTrimsBlankNotesToNull() = runBlocking {
        assertNull(checkIns.create(Mood.GOOD, 70, "   \n", emptyList(), clock.now()).notes)
        assertEquals("a rough night", checkIns.create(Mood.GOOD, 70, "  a rough night \n", emptyList(), clock.now()).notes)
    }

    @Test fun updateReconcilesSymptomsAddKeepRemove() = runBlocking {
        val a = custom("A"); val b = custom("B"); val c = custom("C")
        val entry = checkIns.create(Mood.OKAY, 50, null, listOf(SymptomPick(a.id, 1), SymptomPick(b.id, 1)), clock.now())
        checkIns.update(entry.id, Mood.LOW, 40, "changed", listOf(SymptomPick(a.id, 3), SymptomPick(c.id, 2)))
        assertEquals(listOf("A", "C"), names(entry.id))
        assertEquals(3, checkIns.picks(entry.id).first { it.symptomId == a.id }.severity)
        // B's link is tombstoned, not hard-deleted (history preserved).
        val bDeleted = withContext(Dispatchers.IO) {
            db.query("SELECT deletedAt FROM check_in_symptom WHERE symptomID = ?", arrayOf(b.id)).use { it.moveToFirst(); !it.isNull(0) }
        }
        assertTrue(bDeleted)
        val updated = checkIns.entry(entry.id)!!
        assertEquals(Mood.LOW, updated.mood); assertEquals("changed", updated.notes)
    }

    @Test fun deleteSoftDeletesAndTombstonesLinksButNotTheSymptom() = runBlocking {
        val a = custom("A")
        val entry = checkIns.create(Mood.OKAY, 50, null, listOf(SymptomPick(a.id, 1)), clock.now())
        checkIns.delete(entry.id)
        assertTrue(checkIns.observeLive().first().isEmpty())
        assertNull(checkIns.entry(entry.id))           // gone from reads
        assertTrue(checkIns.picks(entry.id).isEmpty())  // links tombstoned
        assertNull(db.symptoms().all().first { it.id == a.id }.deletedAt) // catalog untouched
    }

    // AlcoholTests (recording)

    @Test fun newEntryHasNoAlcoholRecorded() = runBlocking {
        assertNull(checkIns.create(Mood.OKAY, 50, null, emptyList(), clock.now()).alcoholCount)
    }

    @Test fun zeroIsARealAnswerAndClearReturnsToNotRecorded() = runBlocking {
        val c = checkIns.create(Mood.OKAY, 50, null, emptyList(), clock.now())
        checkIns.setAlcohol(c.id, 0); assertEquals(0, checkIns.entry(c.id)!!.alcoholCount)
        checkIns.setAlcohol(c.id, 3); assertEquals(3, checkIns.entry(c.id)!!.alcoholCount)
        checkIns.setAlcohol(c.id, null); assertNull(checkIns.entry(c.id)!!.alcoholCount)
        checkIns.setAlcohol(c.id, -2); assertEquals(0, checkIns.entry(c.id)!!.alcoholCount)
    }

    @Test fun editingTheRestOfAnEntryKeepsItsAlcohol() = runBlocking {
        val c = checkIns.create(Mood.OKAY, 50, null, emptyList(), clock.now())
        checkIns.setAlcohol(c.id, 2)
        checkIns.update(c.id, Mood.GOOD, 70, "edited", emptyList())
        assertEquals(2, checkIns.entry(c.id)!!.alcoholCount)
    }

    @Test fun alcoholSettingIsOnByDefaultAndHerChoiceIsKept() = runBlocking {
        assertTrue(settings.notesAlcohol.first())
        settings.setNotesAlcohol(false)
        assertFalse(settings.notesAlcohol.first())
    }

    // SymptomRepositoryTests

    @Test fun syncBuiltInsSeedsTheCatalogAsGlobalReferenceData() = runBlocking {
        symptoms.syncBuiltIns()
        val all = symptoms.allActive()
        assertEquals(SymptomCatalog.builtIn.size, all.size)
        assertTrue(all.all { !it.isCustom && it.ownerID.isEmpty() && it.syncStatusRaw == "synced" })
        assertEquals(SymptomCatalog.VERSION, settings.symptomCatalogVersion.first())
    }

    @Test fun syncBuiltInsIsIdempotent() = runBlocking {
        symptoms.syncBuiltIns(); val first = symptoms.allActive().size
        symptoms.syncBuiltIns()
        assertEquals(first, symptoms.allActive().size)
    }

    /** Old wording carries its history across; built-ins the catalog dropped are archived, never deleted. */
    @Test fun reconcileRenamesOldWordingAndArchivesDroppedBuiltIns() = runBlocking {
        val t = clock.now()
        db.symptoms().upsert(SymptomEntity("OLD", "Insomnia", "sleep", ownerID = "", createdAt = t, updatedAt = t))
        db.symptoms().upsert(SymptomEntity("GONE", "Retired symptom", "body", ownerID = "", createdAt = t, updatedAt = t))
        symptoms.syncBuiltIns()
        val all = db.symptoms().all()
        assertEquals("Trouble sleeping", all.first { it.id == "OLD" }.name)
        assertEquals(1, all.count { it.name == "Trouble sleeping" }) // no second chip
        assertTrue(all.first { it.id == "GONE" }.isArchived)
        assertNull(all.first { it.id == "GONE" }.deletedAt)
    }

    @Test fun findOrCreateCustomCreatesOnceIgnoringCaseAndSpaces() = runBlocking {
        val a = symptoms.findOrCreateCustom("Brain fog", "cognition")
        val b = symptoms.findOrCreateCustom("  brain FOG ", "cognition")
        assertEquals(a.id, b.id)
        assertTrue(a.isCustom)
        assertEquals("local-TEST", a.ownerID)
    }

    @Test fun searchFiltersByName() = runBlocking {
        symptoms.findOrCreateCustom("Night sweats", "body")
        symptoms.findOrCreateCustom("Headache", "aches")
        assertEquals(listOf("Night sweats"), symptoms.search("sweat").map { it.name })
        assertEquals(symptoms.allActive().size, symptoms.search("   ").size)
    }

    @Test fun groupedOnlyIncludesNonEmptyCategories() = runBlocking {
        symptoms.findOrCreateCustom("Custom body thing", "body")
        val grouped = symptoms.grouped()
        assertTrue(grouped.all { it.second.isNotEmpty() })
        assertEquals(listOf("body"), grouped.map { it.first.raw })
    }

    /** Remove archives (reversible), rename keeps the id, move changes only the group. */
    @Test fun archiveRenameAndMove() = runBlocking {
        val s = symptoms.findOrCreateCustom("Itchy ears", "body")
        symptoms.rename(s, "  Itchy ears at night ")
        symptoms.move(db.symptoms().all().first { it.id == s.id }, "Garden aches")
        val moved = db.symptoms().all().first { it.id == s.id }
        assertEquals("Itchy ears at night", moved.name); assertEquals("Garden aches", moved.categoryRaw)
        symptoms.archive(moved)
        assertTrue(symptoms.allActive().none { it.id == s.id })
        assertNotNull(db.symptoms().all().first { it.id == s.id }.isArchived)
    }

    // QuickChipRankingTests (end to end)

    @Test fun defaultChipsColdStartIsCatalogDefaults() = runBlocking {
        symptoms.syncBuiltIns()
        val chips = symptoms.defaultChips()
        assertEquals(SymptomCatalog.defaultOrder, chips.map { it.name })
        assertTrue(chips.all { it.isDefaultChip })
    }

    @Test fun defaultChipsPromotesAFrequentlyLoggedCustomSymptom() = runBlocking {
        symptoms.syncBuiltIns()
        val tinnitus = symptoms.findOrCreateCustom("Tinnitus", "body")
        repeat(5) { checkIns.create(Mood.OKAY, 50, null, listOf(SymptomPick(tinnitus.id, 2)), clock.now()) }
        assertEquals("Tinnitus", symptoms.defaultChips().first().name)
    }

    // Manual sleep from the check-in

    @Test fun manualSleepIsSavedUpdatedAndClearedForTheNight() = runBlocking {
        val activity = ActivityRepository(db.activityLogs(), clock) { "local-TEST" }
        val night = LocalDate.of(2026, 8, 21)
        assertNull(activity.manualSleep(night))
        activity.saveManualSleep(night, 7.5); assertEquals(7.5, activity.manualSleep(night)!!, 0.0)
        activity.saveManualSleep(night, 6.0); assertEquals(6.0, activity.manualSleep(night)!!, 0.0)
        assertEquals(1, activity.observeAmounts("sleep").first().size) // updated, not duplicated
        activity.saveManualSleep(night, null)
        assertNull(activity.manualSleep(night))
        assertTrue(activity.observeAmounts("sleep").first().isEmpty())
    }
}
