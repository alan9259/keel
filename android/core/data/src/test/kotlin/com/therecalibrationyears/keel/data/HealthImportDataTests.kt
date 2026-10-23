package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.ActivityLogEntity
import com.therecalibrationyears.keel.data.db.CheckInEntity
import com.therecalibrationyears.keel.data.db.CheckInSymptomEntity
import com.therecalibrationyears.keel.data.db.CycleEntryEntity
import com.therecalibrationyears.keel.data.db.HealthActivitySampleEntity
import com.therecalibrationyears.keel.data.db.HealthDatabase
import com.therecalibrationyears.keel.data.db.HealthSampleEntity
import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.data.db.SymptomEntity
import com.therecalibrationyears.keel.domain.DayAmount
import com.therecalibrationyears.keel.domain.FlowLevel
import com.therecalibrationyears.keel.domain.HealthSnapshot
import com.therecalibrationyears.keel.domain.HealthSyncCatalog
import com.therecalibrationyears.keel.domain.MergedActivity
import com.therecalibrationyears.keel.domain.PatternEngine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Her records and the separate health store, on a fixed UTC clock. */
private class HealthStore {
    val clock = TestClock(Instant.parse("2026-10-23T10:00:00Z"))
    val main: MainDatabase = inMemoryDatabase()
    val health: HealthDatabase = HealthDatabase.inMemory(RuntimeEnvironment.getApplication())
    val ingestor = HealthIngestor(health, main, { "local-TEST" }, clock)
    val repo = HealthRepository(health, clock)
    val cycle = CycleRepository(main.cycle(), { "local-TEST" }, clock)
    val today: LocalDate = LocalDate.of(2026, 10, 23)
    fun start(day: LocalDate): Instant = day.atStartOfDay(ZoneOffset.UTC).toInstant()
    fun close() { main.close(); health.close() }
}

/** Port of KeelTests/HealthSleepSourceTests.swift and HealthKitImportIntegrationTests.swift. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthImportTests {
    private val s = HealthStore()

    @After fun tearDown() = s.close()

    private suspend fun manual(): List<DayAmount> = ActivityRepository(s.main.activityLogs(), s.clock).observeAll().first()
    private suspend fun imported(): List<DayAmount> = s.repo.observeActivity().first()

    @Test fun importedSleepLandsInTheHealthStoreAndTheMergePrefersHers() = runBlocking {
        val manualDay = s.today.minusDays(1); val gapDay = s.today.minusDays(2); val staleDay = s.today.minusDays(3)
        val t = s.clock.now()
        s.main.activityLogs().upsert(ActivityLogEntity("A1", s.start(manualDay), "sleep", 6.0, ownerID = "o", createdAt = t, updatedAt = t))
        // A stale imported value (the iOS 13.2 hours bug) that Health Connect has since corrected.
        s.health.activity().upsertAll(listOf(HealthActivitySampleEntity("H1", s.start(staleDay), "sleep", 13.2, "o", t, t)))

        s.ingestor.ingest(HealthSnapshot(sleepByDay = mapOf(manualDay to 7.5, gapDay to 8.0, staleDay to 7.0)))

        // Imports go to the health store, never her activity log; her entry is untouched.
        assertEquals(listOf(DayAmount("sleep", manualDay, 6.0)), manual())
        assertEquals(8.0, imported().single { it.day == gapDay }.amount, 0.0)
        assertEquals(7.0, imported().single { it.day == staleDay }.amount, 0.0)
        // Hers wins her day; gaps and corrections come from Health Connect.
        assertEquals(6.0, MergedActivity.amount("sleep", manualDay, manual(), imported()))
        assertEquals(8.0, MergedActivity.amount("sleep", gapDay, manual(), imported()))
        assertEquals(7.0, MergedActivity.amount("sleep", staleDay, manual(), imported()))
    }

    @Test fun hasImportedDataReflectsTheStore() = runBlocking {
        assertFalse(s.repo.hasImportedData())
        s.ingestor.ingest(HealthSnapshot(activityAmounts = mapOf("steps" to mapOf(s.today to 8000.0))))
        assertTrue(s.repo.hasImportedData())
    }

    @Test fun importRoutesToTheHealthStoreAndNeverImportsSymptomsOrCycle() = runBlocking {
        val snap = HealthSnapshot(
            sleepByDay = mapOf(s.today to 7.5, s.today.minusDays(1) to 6.0),
            activityAmounts = mapOf("steps" to mapOf(s.today to 8000.0), "exercise" to mapOf(s.today to 30.0)),
            vitals = listOf(
                HealthSnapshot.VitalSeries("heartRate", "bpm", mapOf(s.today to 64.0)),
                HealthSnapshot.VitalSeries("restingHeartRate", "bpm", mapOf(s.today to 58.04)),
            ),
        )
        val first = s.ingestor.ingest(snap)
        assertEquals(4, first.activity)
        assertEquals(2, first.vitals)
        assertTrue(manual().isEmpty())
        assertTrue(s.main.checkIns().observeLive().first().isEmpty())
        assertTrue(s.main.cycle().observeLive().first().isEmpty())
        assertEquals(58.0, s.health.samples().liveOfType("restingHeartRate").single().value, 0.0) // one decimal

        // Idempotent: the same read again adds nothing.
        assertEquals(0, s.ingestor.ingest(snap).total)
        assertEquals(4, imported().size)
    }

    @Test fun aRevisedValueReplacesTheImportedOne() = runBlocking {
        s.ingestor.ingest(HealthSnapshot(activityAmounts = mapOf("steps" to mapOf(s.today to 3000.0))))
        val again = s.ingestor.ingest(HealthSnapshot(activityAmounts = mapOf("steps" to mapOf(s.today to 8000.0))))
        assertEquals(1, again.activity)
        assertEquals(listOf(8000.0), imported().map { it.amount })
    }

    @Test fun aZeroAmountIsNotImported() = runBlocking {
        s.ingestor.ingest(HealthSnapshot(activityAmounts = mapOf("steps" to mapOf(s.today to 0.0))))
        assertTrue(imported().isEmpty())
    }

    /** Port of HealthSyncCatalogTests.testDisabledItemIsNotIngestedButEnabledIs. */
    @Test fun aSwitchedOffItemIsNotImportedButOthersAre() = runBlocking {
        val snap = HealthSnapshot(activityAmounts = mapOf("steps" to mapOf(s.today to 8000.0), "exercise" to mapOf(s.today to 30.0)))
        s.ingestor.ingest(HealthSyncCatalog.filter(snap, setOf("exercise")))
        assertEquals(setOf("steps"), imported().map { it.activityId }.toSet())
    }
}

/** Port of the import parts of KeelTests/AppleHealthPeriodsTests.swift. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthConnectPeriodsTests {
    private val s = HealthStore()
    private val day get() = s.today

    @After fun tearDown() = s.close()

    private suspend fun flow() = s.repo.observeFlow().first()

    @Test fun importIsIdempotentAndRefreshesTheLevel() = runBlocking {
        val snap = HealthSnapshot(menstrualFlow = mapOf(day to FlowLevel.LIGHT, day.minusDays(1) to FlowLevel.MEDIUM))
        s.ingestor.ingest(snap)
        s.ingestor.ingest(snap)
        assertEquals(2, flow().size)
        s.ingestor.ingest(snap.copy(menstrualFlow = snap.menstrualFlow + (day to FlowLevel.HEAVY)))
        assertEquals(FlowLevel.HEAVY, flow()[day])
        assertTrue("never written into her cycle entries", s.main.cycle().observeLive().first().isEmpty())
    }

    /** Regression (iOS review): a day she deleted in Health Connect stayed on the Cycle screen. */
    @Test fun aDayDeletedInHealthConnectIsRemoved() = runBlocking {
        val window = day.minusDays(30)
        s.ingestor.ingest(HealthSnapshot(menstrualFlow = mapOf(day to FlowLevel.LIGHT, day.minusDays(1) to FlowLevel.MEDIUM), flowWindowStart = window))
        s.ingestor.ingest(HealthSnapshot(menstrualFlow = mapOf(day to FlowLevel.LIGHT), flowWindowStart = window))
        assertEquals(mapOf(day to FlowLevel.LIGHT), flow())
        s.ingestor.ingest(HealthSnapshot(flowWindowStart = window))
        assertTrue(flow().isEmpty())
    }

    @Test fun daysBeforeTheWindowAreKept() = runBlocking {
        s.ingestor.ingest(HealthSnapshot(menstrualFlow = mapOf(day.minusDays(400) to FlowLevel.MEDIUM)))
        s.ingestor.ingest(HealthSnapshot(flowWindowStart = day.minusDays(365)))
        assertEquals(1, flow().size)
    }

    @Test fun noWindowMeansNothingIsRemoved() = runBlocking {
        s.ingestor.ingest(HealthSnapshot(menstrualFlow = mapOf(day to FlowLevel.LIGHT)))
        s.ingestor.ingest(HealthSnapshot())
        assertEquals(1, flow().size)
        s.ingestor.ingest(HealthSyncCatalog.filter(HealthSnapshot(flowWindowStart = day.minusDays(30)), setOf("periods")))
        assertEquals(1, flow().size)
    }

    private val regularStarts = listOf(-84, -56, -28, 0)
    private val irregularStarts = listOf(-95, -70, -35, 0)
    private fun flowOf(starts: List<Int>) = starts.flatMap { start -> (0 until 4).map { day.plusDays((start + it).toLong()) to FlowLevel.MEDIUM } }.toMap()

    /** Positive control: the same days logged in Keel do drive the estimate and the pattern. */
    @Test fun controlTheSameDaysLoggedInKeelDriveEstimateAndPattern() = runBlocking {
        flowOf(regularStarts).forEach { (d, level) -> s.cycle.setFlow(level, d) }
        assertTrue(s.cycle.stats(today = day).canEstimate)
    }

    /** Imported period days never feed the estimate or the patterns (they live in another store). */
    @Test fun importedPeriodsNeverFeedTheEstimateOrPatterns() = runBlocking {
        s.ingestor.ingest(HealthSnapshot(menstrualFlow = flowOf(regularStarts)))
        val stats = s.cycle.stats(today = day)
        assertNull(stats.lastStart)
        assertFalse(stats.canEstimate)
        s.ingestor.ingest(HealthSnapshot(menstrualFlow = flowOf(irregularStarts)))
        val days = s.cycle.observeDays().first()
        assertTrue(PatternEngine.build(emptyList(), days, day).findings().isEmpty())
    }

    @Test fun removeImportedClearsImportedPeriods() = runBlocking {
        s.ingestor.ingest(HealthSnapshot(menstrualFlow = mapOf(day to FlowLevel.LIGHT)))
        assertEquals(1, s.ingestor.purgeAllImportedHealthData())
        assertTrue(flow().isEmpty())
    }
}

/** Port of KeelTests/HealthImportPurgeTests.swift and the purge in SubmissionPackATests.swift. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthImportPurgeTests {
    private val s = HealthStore()

    @After fun tearDown() = s.close()

    @Test fun purgeRemovesOnlyDiscontinuedHealthImports() = runBlocking {
        val t = s.clock.now()
        val d = s.start(s.today)
        s.main.checkIns().upsert(CheckInEntity("C1", d, "okay", 60, ownerID = "o", createdAt = t, updatedAt = t))
        s.main.symptoms().upsert(SymptomEntity("S1", "Hot flushes", categoryRaw = "body", ownerID = "o", createdAt = t, updatedAt = t))
        s.main.symptoms().upsert(SymptomEntity("S2", "Anxious", categoryRaw = "mood", ownerID = "o", createdAt = t, updatedAt = t))
        s.main.checkIns().upsertLink(CheckInSymptomEntity("L1", "C1", "S1", 2, sourceRaw = "healthKit", ownerID = "o", createdAt = t, updatedAt = t))
        s.main.checkIns().upsertLink(CheckInSymptomEntity("L2", "C1", "S2", 1, sourceRaw = "manual", ownerID = "o", createdAt = t, updatedAt = t))
        s.main.cycle().upsert(CycleEntryEntity("Y1", s.start(s.today.minusDays(1)), "periodStart", "medium", "manual", "o", t, t))
        s.main.cycle().upsert(CycleEntryEntity("Y2", s.start(s.today.minusDays(2)), "periodStart", "light", "healthKit", "o", t, t))
        s.health.samples().upsertAll(
            listOf(
                HealthSampleEntity("H1", "symptom.hot_flushes", d, 2.0, "severity", ownerID = "o", createdAt = t, updatedAt = t),
                HealthSampleEntity("H2", "restingHeartRate", d, 62.0, "bpm", ownerID = "o", createdAt = t, updatedAt = t),
            ),
        )

        assertEquals(3, s.ingestor.purgeDiscontinuedHealthImports()) // the imported link, cycle entry and symptom sample

        assertEquals(listOf("L2"), s.main.checkIns().liveLinks("C1").map { it.id })
        assertEquals(listOf("Y1"), s.main.cycle().observeLive().first().map { it.id })
        assertEquals(listOf("restingHeartRate"), s.health.samples().observeLive().first().map { it.typeID })
        assertEquals(0, s.ingestor.purgeDiscontinuedHealthImports()) // idempotent
    }

    /** SubmissionPackA: a reading Keel no longer reads goes; ones it still reads stay. */
    @Test fun previouslyImportedUnusedTypesArePurged() = runBlocking {
        val t = s.clock.now()
        s.health.samples().upsertAll(listOf("basalBodyTemperature", "oxygenSaturation", "restingHeartRate").mapIndexed { i, type ->
            HealthSampleEntity("H$i", type, s.start(s.today), 1.0, "", ownerID = "o", createdAt = t, updatedAt = t)
        })
        s.ingestor.purgeDiscontinuedHealthImports()
        assertEquals(setOf("oxygenSaturation", "restingHeartRate"), s.health.samples().observeLive().first().map { it.typeID }.toSet())
    }

    @Test fun purgeAllImportedHealthDataClearsTheHealthStoreOnly() = runBlocking {
        val t = s.clock.now()
        val d = s.start(s.today)
        s.health.activity().upsertAll(listOf(HealthActivitySampleEntity("A1", d, "steps", 8000.0, "o", t, t)))
        s.health.samples().upsertAll(listOf(HealthSampleEntity("H1", "restingHeartRate", d, 62.0, "bpm", ownerID = "o", createdAt = t, updatedAt = t)))
        s.main.activityLogs().upsert(ActivityLogEntity("M1", d, "water", 6.0, ownerID = "o", createdAt = t, updatedAt = t))

        assertEquals(2, s.ingestor.purgeAllImportedHealthData())
        assertFalse(s.repo.hasImportedData())
        assertEquals(1, ActivityRepository(s.main.activityLogs(), s.clock).observeAll().first().size) // hers untouched
    }
}
