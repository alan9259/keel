package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.backup.BackupCodec
import com.therecalibrationyears.keel.data.backup.BackupFormatException
import com.therecalibrationyears.keel.data.backup.BackupService
import com.therecalibrationyears.keel.data.backup.UnsupportedBackupVersionException
import com.therecalibrationyears.keel.data.db.CheckInEntity
import com.therecalibrationyears.keel.data.db.CheckInSymptomEntity
import com.therecalibrationyears.keel.data.db.CycleEntryEntity
import com.therecalibrationyears.keel.data.db.HealthDatabase
import com.therecalibrationyears.keel.data.db.HealthSampleEntity
import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.data.db.MedicationLogEntity
import com.therecalibrationyears.keel.data.db.SymptomEntity
import com.therecalibrationyears.keel.data.db.UserProfileEntity
import com.therecalibrationyears.keel.domain.Hysterectomy
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A phone's worth of storage: her records and the imported health readings. */
private class BackupStore {
    val clock = TestClock(Instant.parse("2026-10-22T09:30:45.678Z"))
    val main: MainDatabase = inMemoryDatabase()
    val health: HealthDatabase = HealthDatabase.inMemory(RuntimeEnvironment.getApplication())
    val users = userRepository(main, clock)
    val service = BackupService(main, health, clock) { "1.0.0" }
    fun close() { main.close(); health.close() }
}

private val day = Instant.parse("2026-10-21T00:00:00Z")
private const val OWNER = "local-TEST"

private suspend fun seed(s: BackupStore) {
    val t = s.clock.now()
    s.main.symptoms().upsert(SymptomEntity("S1", "Trouble sleeping", categoryRaw = "sleep", ownerID = "", createdAt = t, updatedAt = t))
    s.main.checkIns().upsert(CheckInEntity("C1", day, "okay", 60, ownerID = OWNER, createdAt = t, updatedAt = t))
    s.main.checkIns().upsertLink(CheckInSymptomEntity("L1", "C1", "S1", severity = 2, ownerID = OWNER, createdAt = t, updatedAt = t))
    s.main.medications().upsert(
        MedicationEntity("M1", "Oestrogel", dosage = "2 pumps", timing = "Morning", kindRaw = "treatment", autoLogDoses = true, ownerID = OWNER, createdAt = t, updatedAt = t),
    )
    s.main.medications().upsertLog(MedicationLogEntity("G1", day, taken = true, medicationID = "M1", ownerID = OWNER, createdAt = t, updatedAt = t))
    s.main.cycle().upsert(CycleEntryEntity("Y1", day, typeRaw = "periodStart", flowLevelRaw = "medium", ownerID = OWNER, createdAt = t, updatedAt = t))
    s.health.samples().insertAll(listOf(HealthSampleEntity("H1", "restingHeartRate", day, 60.0, "bpm", ownerID = OWNER, createdAt = t, updatedAt = t)))
}

/**
 * Port of KeelTests/BackupRoundTripTests.swift: export, then wipe and restore, and her records
 * (and imported readings) come back with their links.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRoundTripTests {
    private val s = BackupStore()

    @After fun tearDown() = s.close()

    @Test fun exportThenRestoreRoundTripsHerData() = runBlocking {
        seed(s)
        val data = s.service.exportData()
        val summary = s.service.restore(s.service.decode(data))

        assertEquals(1, summary.checkIns)
        val r = s.main.backup().snapshot()
        assertEquals(1, r.checkIns.size)
        assertEquals(1, r.medications.size)
        assertEquals(1, r.cycleEntries.size)
        assertEquals(1, s.health.samples().all().size) // the health store round-trips
        // The check-in keeps its symptom link.
        assertEquals("S1", r.checkInSymptoms.single().symptomID)
        assertEquals("C1", r.checkInSymptoms.single().checkInID)
        assertEquals("M1", r.medicationLogs.single().medicationID)
    }

    /** Nothing is lost: what comes back is the same rows, field for field (to the second). */
    @Test fun aRoundTripIsLossless() = runBlocking {
        seed(s)
        s.users.upsertProfile("Mischa", email = "m@example.com", appleUserID = null)
        val before = s.main.backup().snapshot()
        s.service.restore(s.service.decode(s.service.exportData()))
        val after = s.main.backup().snapshot()
        fun <T> secs(list: List<T>) = list.toString().replace(Regex("""\.\d+Z"""), "Z")
        assertEquals(secs(before.checkIns), after.checkIns.toString())
        assertEquals(secs(before.medications), after.medications.toString()) // incl. auto-log (plan D1)
        assertEquals(secs(before.checkInSymptoms), after.checkInSymptoms.toString())
        assertEquals(before.profiles.single().email, after.profiles.single().email)
    }

    private suspend fun connectedBackupData(): ByteArray {
        val source = BackupStore()
        try {
            source.users.upsertProfile("Mischa", email = null, appleUserID = null)
            source.users.setHealthConnected(true)
            return source.service.exportData()
        } finally {
            source.close()
        }
    }

    /** Regression (iOS): restoring a connected backup on a fresh phone switched reading on. */
    @Test fun restoreDoesNotConnectHealthOnAFreshDevice() = runBlocking {
        s.service.restore(s.service.decode(connectedBackupData()))
        val p = s.users.currentProfile()!!
        assertEquals("Mischa", p.firstName)
        assertFalse(p.healthConnected)
    }

    @Test fun restoreKeepsThisDevicesExistingConnection() = runBlocking {
        s.users.upsertProfile("Mischa", email = null, appleUserID = null)
        s.users.setHealthConnected(true)
        val source = BackupStore()
        val data = try {
            source.users.upsertProfile("Mischa", email = null, appleUserID = null)
            source.service.exportData()
        } finally {
            source.close()
        }
        s.service.restore(s.service.decode(data))
        assertTrue(s.users.currentProfile()!!.healthConnected)
    }

    /** She restores from inside the app, so she has onboarded here: the restore keeps it. */
    @Test fun restoreKeepsThisPhonesOnboarding() = runBlocking {
        s.users.upsertProfile("Rose", email = null, appleUserID = null)
        s.users.markOnboardingCompleted(Instant.parse("2026-10-01T00:00:00Z"))
        s.service.restore(s.service.decode(connectedBackupData()))
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), s.users.currentProfile()!!.onboardingCompletedAt)
    }

    @Test fun aNewerFormatIsRefusedWithTheIosMessage() = runBlocking {
        val json = Json.parseToJsonElement(s.service.exportData().decodeToString()).jsonObject
        val newer = JsonObject(json + ("version" to kotlinx.serialization.json.JsonPrimitive(2)))
        try {
            s.service.restore(s.service.decode(newer.toString().toByteArray()))
            fail("a newer format must not restore")
        } catch (e: UnsupportedBackupVersionException) {
            assertEquals("This backup was made by a newer version of Keel (format 2). Update the app to restore it.", e.message)
        }
    }

    @Test fun somethingElseIsNotABackup() {
        listOf("not json", "{}", """{"version":1}""", "[1,2]").forEach { text ->
            try {
                s.service.decode(text.toByteArray())
                fail("$text read as a backup")
            } catch (_: BackupFormatException) {
            }
        }
    }

    /** iOS skips a link whose check-in or symptom isn't in the archive, and keeps an orphaned log unlinked. */
    @Test fun danglingLinksAreSkipped() = runBlocking {
        seed(s)
        val json = Json.parseToJsonElement(s.service.exportData().decodeToString()).jsonObject
        val links = json["checkInSymptoms"]!!.jsonArray + Json.parseToJsonElement(
            """{"id":"L2","checkInID":"C9","symptomID":"S1","ownerID":"o","createdAt":"2026-10-21T00:00:00Z","updatedAt":"2026-10-21T00:00:00Z","syncStatusRaw":"synced"}""",
        )
        val logs = json["medicationLogs"]!!.jsonArray + Json.parseToJsonElement(
            """{"id":"G2","date":"2026-10-21T00:00:00Z","taken":true,"medicationID":"M9","ownerID":"o","createdAt":"2026-10-21T00:00:00Z","updatedAt":"2026-10-21T00:00:00Z","syncStatusRaw":"synced"}""",
        )
        val edited = JsonObject(json + ("checkInSymptoms" to JsonArray(links)) + ("medicationLogs" to JsonArray(logs)))
        s.service.restore(s.service.decode(edited.toString().toByteArray()))
        val r = s.main.backup().snapshot()
        assertEquals(listOf("L1"), r.checkInSymptoms.map { it.id })
        assertEquals(null, r.medicationLogs.single { it.id == "G2" }.medicationID)
    }
}

/** The file itself, as iOS reads and writes it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupFormatTests {
    private val s = BackupStore()

    @After fun tearDown() = s.close()

    private fun exported(): JsonObject = runBlocking { seed(s); Json.parseToJsonElement(s.service.exportData().decodeToString()).jsonObject }

    /** Swift's `.iso8601` rejects fractional seconds, so every date is whole seconds in UTC. */
    @Test fun datesAreWholeSecondsUtc() {
        val json = exported()
        assertEquals("2026-10-22T09:30:45Z", json["exportedAt"]!!.jsonPrimitive.content)
        val checkIn = json["checkIns"]!!.jsonArray.single().jsonObject
        assertEquals("2026-10-21T00:00:00Z", checkIn["date"]!!.jsonPrimitive.content)
        assertEquals("2026-10-22T09:30:45Z", checkIn["createdAt"]!!.jsonPrimitive.content)
    }

    @Test fun everyTopLevelKeyIosExpectsIsThere() {
        assertEquals(
            setOf(
                "version", "exportedAt", "appVersion", "profiles", "symptoms", "checkIns", "checkInSymptoms", "cycleEntries",
                "medications", "medicationLogs", "insights", "activityLogs", "chatMessages", "dailySummaries", "healthSamples",
            ),
            exported().keys,
        )
    }

    /** An empty optional is left out (as Swift does), and a required field is always written. */
    @Test fun emptyOptionalsAreLeftOutAndKeysAreSorted() {
        val text = runBlocking { seed(s); s.service.exportData().decodeToString() }
        val checkIn = Json.parseToJsonElement(text).jsonObject["checkIns"]!!.jsonArray.single().jsonObject
        assertFalse("notes" in checkIn)
        assertFalse("deletedAt" in checkIn)
        assertEquals(checkIn.keys.sorted(), checkIn.keys.toList())
        assertFalse("null" in text)
    }

    /** Swift writes a whole Double without a fraction; so does Android, so the files match. */
    @Test fun wholeNumbersHaveNoFraction() {
        val sample = exported()["healthSamples"]!!.jsonArray.single().jsonObject
        assertEquals("60", sample["value"]!!.jsonPrimitive.content)
    }

    /** iOS ignores keys it doesn't know, so the medicine fields its archive lacks travel as extras (D1). */
    @Test fun medicineExtrasAreWritten() {
        val med = exported()["medications"]!!.jsonArray.single().jsonObject
        assertEquals("true", med["autoLogDoses"]!!.jsonPrimitive.content)
        assertEquals("true", med["isTracked"]!!.jsonPrimitive.content)
    }

    /** The health readings' day is the start of the day where she is (iOS `HealthSample` init). */
    @Test fun fractionalAndOffsetDatesAreReadLeniently() {
        assertEquals(Instant.parse("2026-10-21T00:00:00.5Z"), BackupCodec.parseDate("2026-10-21T00:00:00.5Z"))
        assertEquals(Instant.parse("2026-10-20T14:00:00Z"), BackupCodec.parseDate("2026-10-21T00:00:00+10:00"))
    }
}

/** Port of the backup tests in KeelTests/HysterectomyTests.swift. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HysterectomyBackupTests {
    private val s = BackupStore()

    @After fun tearDown() = s.close()

    @Test fun backupKeepsHerBackgroundAndDetails() = runBlocking {
        s.users.upsertProfile("Mischa", email = null, appleUserID = null)
        s.users.setHysterectomy(Hysterectomy.YES_BOTH_OVARIES_REMOVED, 2012)
        s.users.setPeriodsNotApplicableReason("After a hysterectomy")
        s.users.updateBasicInfo("Mischa", "R", 1977, "0400 000 000", null)
        val data = s.service.exportData()

        val fresh = BackupStore()
        try {
            fresh.service.restore(fresh.service.decode(data))
            val p = fresh.users.currentProfile()!!
            assertEquals(Hysterectomy.YES_BOTH_OVARIES_REMOVED, p.hysterectomy)
            assertEquals(2012, p.hysterectomyYear)
            assertEquals("After a hysterectomy", p.periodsNotApplicableReason)
            assertEquals("R", p.lastName)
            assertEquals(1977, p.birthYear)
            assertEquals("0400 000 000", p.mobile)
        } finally {
            fresh.close()
        }
    }

    @Test fun olderBackupsWithoutTheseFieldsStillLoad() = runBlocking {
        s.users.upsertProfile("Mischa", email = null, appleUserID = null)
        s.users.setHysterectomy(Hysterectomy.NO, null)
        val json = Json.parseToJsonElement(s.service.exportData().decodeToString()).jsonObject
        val profiles = json["profiles"]!!.jsonArray.map { p ->
            JsonObject(p.jsonObject - setOf("hysterectomyRaw", "hysterectomyYear", "periodsNotApplicableReason", "lastName", "birthYear", "mobile"))
        }
        val fresh = BackupStore()
        try {
            fresh.service.restore(fresh.service.decode(JsonObject(json + ("profiles" to JsonArray(profiles))).toString().toByteArray()))
            val p = fresh.users.currentProfile()!!
            assertEquals("Mischa", p.firstName)
            assertNull(p.hysterectomy)
        } finally {
            fresh.close()
        }
    }
}

/**
 * The shared fixture (plan §4.2): an archive written by Swift's own `JSONEncoder` from the iOS
 * backup structs, with every legacy shape iOS still reads. Android must restore it as iOS would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IosBackupFixtureTests {
    private val s = BackupStore()
    private val fixtureDir = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "fixtures").isDirectory }

    @After fun tearDown() = s.close()

    private fun fixture() = File(fixtureDir, "fixtures/backup/v1-ios-sample.keelbackup").readBytes()

    @Test fun anIphoneBackupRestoresWithItsLegacyShapes() = runBlocking {
        val summary = s.service.restore(s.service.decode(fixture()))
        val r = s.main.backup().snapshot()
        assertEquals(summary.checkIns, r.checkIns.size)
        val p = r.profiles.single()
        assertEquals("Mischa", p.firstName)
        assertFalse("a fresh phone stays disconnected", p.healthConnected)
        // Older shapes fall back to the iOS defaults.
        val legacyMed = r.medications.single { it.name == "Magnesium" }
        assertEquals("startDate becomes date", Instant.parse("2025-03-01T00:00:00Z"), legacyMed.date)
        assertNull("no schedule kind: no schedule columns", legacyMed.scheduleKindRaw)
        assertEquals("daily", legacyMed.frequencyRaw) // migrated at the next launch
        assertTrue(legacyMed.isTracked) // not in an iPhone backup (D1): the iOS default
        val link = r.checkInSymptoms.single { it.id == "AAAAAAAA-0000-0000-0000-00000000000B" }
        assertEquals(1, link.severity) // older archives, before severity
        assertEquals("manual", link.sourceRaw)
        assertTrue(r.symptoms.single { it.name == "Hot flushes" }.isDefaultChip) // missing: from the default order
        assertEquals("woodworking", r.symptoms.single { it.isCustom }.categoryRaw) // custom category verbatim
        assertNull(r.checkIns.single { it.id == "AAAAAAAA-0000-0000-0000-000000000002" }.alcoholCount)
        val activity = r.activityLogs.single()
        assertEquals("", activity.ownerID) // before the envelope
        assertEquals(7.5, activity.amount, 0.0)
        // A link to a check-in that isn't there is skipped.
        assertFalse(r.checkInSymptoms.any { it.checkInID == "AAAAAAAA-0000-0000-0000-0000000000FF" })
        // Unicode survives.
        assertTrue(r.checkIns.any { it.notes == "Slept badly 😴, ok after café" })
        assertEquals(listOf("restingHeartRate", "hrv"), s.health.samples().all().map { it.typeID }.sorted().reversed())
    }

    /**
     * What Android writes from the iPhone backup, committed beside it for the iOS side of the
     * shared fixtures (plan §4.2) and read by Swift's decoder (tools/ios-backup/run.sh read). Fails
     * when Android's output changes, so the committed file is refreshed with the change.
     */
    @Test fun theCommittedAndroidExportIsWhatAndroidWrites() = runBlocking {
        s.service.restore(s.service.decode(fixture()))
        val written = s.service.exportData().decodeToString()
        val out = File(fixtureDir, "android/core/data/build/backup/v1-android-export.keelbackup")
        out.parentFile.mkdirs()
        out.writeText(written)
        val committed = File(fixtureDir, "fixtures/backup/v1-android-export.keelbackup")
        assertTrue("copy ${out.path} to ${committed.path}", committed.exists() && committed.readText() == written)
    }

    /** Restore, then export: the same records come out (dates to the second, ids upper case). */
    @Test fun anIphoneBackupSurvivesARoundTripHere() = runBlocking {
        val original = Json.parseToJsonElement(fixture().decodeToString()).jsonObject
        s.service.restore(s.service.decode(fixture()))
        val again = Json.parseToJsonElement(s.service.exportData().decodeToString()).jsonObject
        assertEquals(original["checkIns"]!!.jsonArray.size, again["checkIns"]!!.jsonArray.size)
        val notes = { o: JsonObject -> o["checkIns"]!!.jsonArray.map { it.jsonObject["notes"]?.jsonPrimitive?.content }.sortedBy { it ?: "" } }
        assertEquals(notes(original), notes(again))
    }
}
