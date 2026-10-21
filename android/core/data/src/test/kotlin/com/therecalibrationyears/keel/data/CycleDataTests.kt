package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.domain.CyclePhase
import com.therecalibrationyears.keel.domain.FlowLevel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Port of KeelTests/CycleRepositoryTests.swift and CycleRepositoryFlowTests.swift: period
 * logging, flow levels and the gentle phase estimate, on an in-memory store. "Today" is a
 * fixed day (iOS used the wall clock). The Apple Health import test arrives with M7.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CycleRepositoryTests {
    private lateinit var db: MainDatabase
    private lateinit var repo: CycleRepository
    private val clock = TestClock(Instant.parse("2026-10-21T09:00:00Z"))
    private val today = LocalDate.of(2026, 10, 21)

    @Before fun setUp() {
        db = inMemoryDatabase()
        repo = CycleRepository(db.cycle(), { "test-owner" }, clock)
    }

    @After fun tearDown() = db.close()

    @Test fun togglePeriodDayOnThenOff() = runBlocking {
        assertFalse(repo.isPeriodDay(today))
        repo.togglePeriodDay(today)
        assertTrue(repo.isPeriodDay(today))
        repo.togglePeriodDay(today) // toggling again removes it
        assertFalse(repo.isPeriodDay(today))
    }

    @Test fun togglePeriodDayIsPerDay() = runBlocking {
        repo.togglePeriodDay(today)
        assertTrue(repo.isPeriodDay(today))
        assertFalse(repo.isPeriodDay(today.plusDays(1)))
    }

    @Test fun cycleStartReturnsMostRecentStartOnOrBefore() = runBlocking {
        repo.togglePeriodDay(today.minusDays(20))
        repo.togglePeriodDay(today.minusDays(5))
        assertEquals(today.minusDays(5), repo.cycleStart(before = today))
    }

    @Test fun cycleStartNullWhenNone() = runBlocking { assertNull(repo.cycleStart(before = today)) }

    @Test fun estimatedPhaseUnknownWithoutHistory() = runBlocking { assertEquals(CyclePhase.UNKNOWN, repo.estimatedPhase(today)) }

    @Test fun estimatedPhaseBoundaries() = runBlocking {
        val start = today.minusDays(20) // a period start 20 days ago
        repo.togglePeriodDay(start)
        mapOf(
            0 to CyclePhase.MENSTRUAL, 4 to CyclePhase.MENSTRUAL, 5 to CyclePhase.FOLLICULAR, 12 to CyclePhase.FOLLICULAR,
            13 to CyclePhase.OVULATION, 15 to CyclePhase.OVULATION, 16 to CyclePhase.LUTEAL, 39 to CyclePhase.LUTEAL,
            40 to CyclePhase.UNKNOWN, // stale
        ).forEach { (day, phase) -> assertEquals("day $day", phase, repo.estimatedPhase(start.plusDays(day.toLong()))) }
    }

    @Test fun entriesWindowIsInclusive() = runBlocking {
        repo.togglePeriodDay(today)
        repo.togglePeriodDay(today.plusDays(3))
        assertEquals(2, repo.entries(today, today.plusDays(3)).size)
        assertEquals(1, repo.entries(today, today.plusDays(1)).size)
    }

    // Port of KeelTests/CycleRepositoryFlowTests.swift.

    @Test fun setFlowUpsertsAndClears() = runBlocking {
        assertNull(repo.flow(today))
        repo.setFlow(FlowLevel.HEAVY, today)
        assertEquals(FlowLevel.HEAVY, repo.flow(today))
        repo.setFlow(FlowLevel.LIGHT, today) // change the level
        assertEquals(FlowLevel.LIGHT, repo.flow(today))
        assertEquals(1, db.cycle().observeLive().first().size) // one entry per day
        repo.setFlow(null, today) // clear it
        assertNull(repo.flow(today))
        assertFalse(repo.isPeriodDay(today))
    }

    @Test fun togglePeriodDayLogsUnspecified() = runBlocking {
        repo.togglePeriodDay(today)
        assertEquals(FlowLevel.UNSPECIFIED, repo.flow(today))
        repo.togglePeriodDay(today)
        assertNull(repo.flow(today))
    }

    @Test fun statsFromLoggedStarts() = runBlocking {
        repo.setFlow(FlowLevel.MEDIUM, today.minusDays(56))
        repo.setFlow(FlowLevel.MEDIUM, today.minusDays(28))
        repo.setFlow(FlowLevel.MEDIUM, today)
        assertEquals(listOf(28, 28), repo.stats(lookbackDays = 400, today = today).cycleLengths)
    }

    @Test fun spottingDayDoesNotReanchorThePhase() = runBlocking {
        repo.setFlow(FlowLevel.MEDIUM, today.minusDays(28)) // a real start 28 days ago
        repo.setFlow(FlowLevel.SPOTTING, today.minusDays(3)) // a stray spotting day mid-cycle
        assertEquals(today.minusDays(28), repo.cycleStart(before = today))
        assertEquals(CyclePhase.LUTEAL, repo.estimatedPhase(today))
    }

    @Test fun catchingUpTheLatestPeriodReanchorsThePhase() = runBlocking {
        repo.setFlow(FlowLevel.MEDIUM, today.minusDays(40)) // old start: day 40 → overdue
        assertEquals(CyclePhase.UNKNOWN, repo.estimatedPhase(today))
        repo.setFlow(FlowLevel.MEDIUM, today.minusDays(3)) // she backfills the one she had
        assertEquals(today.minusDays(3), repo.cycleStart(before = today))
        assertEquals(CyclePhase.MENSTRUAL, repo.estimatedPhase(today))
    }

    /** Days are her local days: a period logged in Sydney stays on that day. */
    @Test fun daysFollowHerZone() = runBlocking {
        val sydney = object : com.therecalibrationyears.keel.domain.KeelClock {
            override fun now(): Instant = Instant.parse("2026-10-20T14:30:00Z") // 01:30 on the 21st in Sydney
            override fun zone(): ZoneId = ZoneId.of("Australia/Sydney")
        }
        val local = CycleRepository(db.cycle(), { "o" }, sydney)
        local.togglePeriodDay(today)
        assertEquals(listOf(today), local.observeDays().first().map { it.first })
        assertEquals(1, local.stats().starts.size) // "today" is the 21st there
    }
}

/**
 * Looking back's pattern cards (iOS `InsightRepository.refreshDerived`), on an in-memory
 * store: only real findings, none before five days of check-ins, rewritten each launch.
 * The store-path halves of KeelTests/PatternEngineTests.swift run here too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InsightRepositoryTests {
    private lateinit var db: MainDatabase
    private lateinit var cycle: CycleRepository
    private lateinit var checkIns: CheckInRepository
    private lateinit var insights: InsightRepository
    private val clock = TestClock(Instant.parse("2026-10-21T09:00:00Z"))
    private val today = LocalDate.of(2026, 10, 21)

    @Before fun setUp() {
        db = inMemoryDatabase()
        cycle = CycleRepository(db.cycle(), { "o" }, clock)
        checkIns = CheckInRepository(db.checkIns(), { "o" }, clock)
        insights = InsightRepository(db.insights(), db.checkIns(), db.cycle(), { "o" }, clock)
    }

    @After fun tearDown() = db.close()

    private suspend fun checkInOn(daysAgo: Int) = checkIns.create(
        com.therecalibrationyears.keel.domain.Mood.OKAY, 60, null, emptyList(),
        today.minusDays(daysAgo.toLong()).atTime(9, 0).toInstant(java.time.ZoneOffset.UTC),
    )

    /** Starts 25 then 35 days apart: a real 10-day swing. */
    private suspend fun variableCycles() = listOf(95, 70, 35).forEach { cycle.setFlow(FlowLevel.MEDIUM, today.minusDays(it.toLong())) }

    @Test fun aFindingBecomesACardOnceSheHasCheckedInOnFiveDays() = runBlocking {
        variableCycles()
        (0 until 4).forEach { checkInOn(it) }
        insights.refreshDerived()
        assertTrue(insights.observeAll().first().isEmpty()) // four days: still learning
        checkInOn(4)
        checkInOn(4) // a second entry the same day doesn't count twice
        insights.refreshDerived()
        val card = insights.observeAll().first().single()
        assertEquals("Your cycle length", card.title)
        assertEquals("Your recent cycles ranged from about 25 to 35 days apart. That's the kind of detail that can be useful to bring to your GP.", card.detail)
        assertEquals("arrow.left.and.right", card.iconKey)
        assertEquals(com.therecalibrationyears.keel.domain.InsightAccent.SAGE, card.accent)
    }

    @Test fun refreshingRewritesTheSetRatherThanAddingToIt() = runBlocking {
        variableCycles()
        (0 until 5).forEach { checkInOn(it) }
        insights.refreshDerived()
        insights.refreshDerived()
        assertEquals(1, db.insights().all().size)
        // Her cycles settle (she corrects the outlier): the card goes.
        cycle.setFlow(null, today.minusDays(35))
        cycle.setFlow(FlowLevel.MEDIUM, today.minusDays(45))
        insights.refreshDerived()
        assertEquals(0, db.insights().all().size)
    }

    /** Port of testSpottingDoesNotCreateAFalseCycleRange (KeelTests/PatternEngineTests.swift), store path. */
    @Test fun spottingDoesNotCreateAFalseCycleRange() = runBlocking {
        listOf(84, 56, 28).forEach { cycle.setFlow(FlowLevel.MEDIUM, today.minusDays(it.toLong())) } // 28 and 28
        cycle.setFlow(FlowLevel.SPOTTING, today.minusDays(40)) // stray spotting
        (0 until 5).forEach { checkInOn(it) }
        insights.refreshDerived()
        assertTrue(insights.observeAll().first().isEmpty())
    }
}
