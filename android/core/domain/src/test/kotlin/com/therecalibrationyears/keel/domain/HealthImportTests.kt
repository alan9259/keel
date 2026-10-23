package com.therecalibrationyears.keel.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of KeelTests/HealthSleepMergeTests.swift: hours are the union of the night's intervals. */
class HealthSleepMergeTests {
    private val base = Instant.ofEpochSecond(1_700_000_000)
    private fun r(startHour: Double, endHour: Double) =
        base.plusMillis((startHour * 3_600_000).toLong()) to base.plusMillis((endHour * 3_600_000).toLong())

    @Test fun emptyIsZero() = assertEquals(0.0, HealthSleep.mergedHours(emptyList()), 0.0001)
    @Test fun singleInterval() = assertEquals(8.0, HealthSleep.mergedHours(listOf(r(0.0, 8.0))), 0.0001)
    @Test fun nonOverlappingIntervalsAddUp() = assertEquals(7.5, HealthSleep.mergedHours(listOf(r(0.0, 1.0), r(1.5, 8.0))), 0.0001)

    /** The iOS bug: a whole-night sample plus stages inside it gave 14.5 hours, not 8. */
    @Test fun overlappingStagesAreNotDoubleCounted() =
        assertEquals(8.0, HealthSleep.mergedHours(listOf(r(0.0, 8.0), r(0.0, 1.0), r(1.5, 4.0), r(5.0, 8.0))), 0.0001)

    @Test fun twoSourcesCoveringTheSameNight() = assertEquals(8.0, HealthSleep.mergedHours(listOf(r(0.0, 8.0), r(0.0, 8.0))), 0.0001)
    @Test fun adjacentIntervalsMerge() = assertEquals(8.0, HealthSleep.mergedHours(listOf(r(0.0, 4.0), r(4.0, 8.0))), 0.0001)
    @Test fun unorderedInputIsHandled() = assertEquals(8.0, HealthSleep.mergedHours(listOf(r(5.0, 8.0), r(0.0, 1.0), r(0.0, 8.0))), 0.0001)
    @Test fun partialOverlapUnions() = assertEquals(9.0, HealthSleep.mergedHours(listOf(r(0.0, 5.0), r(4.0, 9.0))), 0.0001)
}

/** Sleep sessions to nights (plan §3.3, D8). */
class HealthSleepSessionTests {
    private val sydney = ZoneId.of("Australia/Sydney")
    private fun at(text: String) = Instant.parse(text)
    private val tue = LocalDate.of(2026, 10, 20)

    @Test fun aSessionWithoutStagesIsAllSleep() {
        val night = HealthSleep.Session(at("2026-10-19T11:30:00Z"), at("2026-10-19T19:30:00Z")) // 22:30 to 06:30 Sydney
        assertEquals(mapOf(tue to 8.0), HealthSleep.hoursByDay(listOf(night), sydney))
    }

    @Test fun withStagesOnlyAsleepCounts() {
        val start = at("2026-10-19T11:00:00Z")
        val night = HealthSleep.Session(
            start, start.plus(Duration.ofHours(8)),
            listOf(
                HealthSleep.Segment(start, start.plus(Duration.ofMinutes(30)), HealthSleep.Stage.OTHER), // awake in bed
                HealthSleep.Segment(start.plus(Duration.ofMinutes(30)), start.plus(Duration.ofHours(4)), HealthSleep.Stage.ASLEEP),
                HealthSleep.Segment(start.plus(Duration.ofHours(4)), start.plus(Duration.ofMinutes(270)), HealthSleep.Stage.OTHER),
                HealthSleep.Segment(start.plus(Duration.ofMinutes(270)), start.plus(Duration.ofHours(8)), HealthSleep.Stage.ASLEEP),
            ),
        )
        assertEquals(7.0, HealthSleep.hoursByDay(listOf(night), sydney).getValue(tue), 0.0001)
    }

    @Test fun stagesWithNoSleepFallBackToTheSessionAsInBed() {
        val start = at("2026-10-19T12:00:00Z")
        val night = HealthSleep.Session(
            start, start.plus(Duration.ofHours(6)),
            listOf(HealthSleep.Segment(start, start.plus(Duration.ofHours(6)), HealthSleep.Stage.IN_BED)),
        )
        assertEquals(6.0, HealthSleep.hoursByDay(listOf(night), sydney).getValue(tue), 0.0001)
    }

    /** In-bed time stands in only where a day has no asleep time at all. */
    @Test fun asleepTimeReplacesInBedTimeForTheSameNight() {
        val start = at("2026-10-19T12:00:00Z")
        val inBedOnly = HealthSleep.Session(start, start.plus(Duration.ofHours(9)), listOf(HealthSleep.Segment(start, start.plus(Duration.ofHours(9)), HealthSleep.Stage.IN_BED)))
        val watch = HealthSleep.Session(start.plus(Duration.ofHours(1)), start.plus(Duration.ofHours(8)))
        assertEquals(7.0, HealthSleep.hoursByDay(listOf(inBedOnly, watch), sydney).getValue(tue), 0.0001)
    }

    /** D8: the whole night counts toward the day it ended, even the part before midnight. */
    @Test fun aNightCountsTowardTheDayItEndedInHerZone() {
        val night = HealthSleep.Session(at("2026-10-19T10:00:00Z"), at("2026-10-19T20:00:00Z")) // 21:00 Mon to 07:00 Tue
        assertEquals(setOf(tue), HealthSleep.hoursByDay(listOf(night), sydney).keys)
        // In London the same session ends on Monday evening.
        assertEquals(setOf(LocalDate.of(2026, 10, 19)), HealthSleep.hoursByDay(listOf(night), ZoneId.of("Europe/London")).keys)
    }

    @Test fun aSessionWithNoLengthIsIgnored() =
        assertTrue(HealthSleep.hoursByDay(listOf(HealthSleep.Session(at("2026-10-19T12:00:00Z"), at("2026-10-19T12:00:00Z"))), sydney).isEmpty())

    @Test fun valuesKeepOneDecimalRoundedAwayFromZero() {
        assertEquals(7.5, oneDecimal(7.45), 0.0)
        assertEquals(7.4, oneDecimal(7.44), 0.0)
        assertEquals(60.0, oneDecimal(59.96), 0.0)
    }
}

/** Port of the flow parts of KeelTests/AppleHealthPeriodsTests.swift (plus period records, D5). */
class HealthFlowTests {
    private val d = LocalDate.of(2026, 10, 20)

    @Test fun flowMappingKeepsTheHeaviestPerDay() {
        val out = HealthFlow.byDay(listOf(d to FlowLevel.LIGHT, d to FlowLevel.HEAVY, d.minusDays(2) to FlowLevel.UNSPECIFIED))
        assertEquals(mapOf(d to FlowLevel.HEAVY, d.minusDays(2) to FlowLevel.UNSPECIFIED), out)
    }

    @Test fun onATieTheFirstStays() {
        assertEquals(FlowLevel.MEDIUM, HealthFlow.byDay(listOf(d to FlowLevel.MEDIUM, d to FlowLevel.UNSPECIFIED))[d])
    }

    /** D5: days a period record covers, with no flow logged, are period days with no level given. */
    @Test fun periodRecordsFillDaysWithoutFlow() {
        val out = HealthFlow.byDay(listOf(d to FlowLevel.HEAVY), listOf(d.minusDays(1)..d.plusDays(1)))
        assertEquals(mapOf(d to FlowLevel.HEAVY, d.minusDays(1) to FlowLevel.UNSPECIFIED, d.plusDays(1) to FlowLevel.UNSPECIFIED), out)
    }

    @Test fun snapshotWithOnlyFlowIsNotEmpty() = assertFalse(HealthSnapshot(menstrualFlow = mapOf(d to FlowLevel.LIGHT)).isEmpty)

    @Test fun switchingPeriodsOffStopsImport() {
        val s = HealthSnapshot(menstrualFlow = mapOf(d to FlowLevel.LIGHT), flowWindowStart = d.minusDays(30))
        val off = HealthSyncCatalog.filter(s, setOf("periods"))
        assertTrue(off.menstrualFlow.isEmpty())
        assertNull(off.flowWindowStart)
        assertEquals(mapOf(d to FlowLevel.LIGHT), HealthSyncCatalog.filter(s, setOf("steps")).menstrualFlow)
    }

    /** Plan §3.4: without the history permission, Health Connect shares from 30 days before the first grant. */
    @Test fun theWindowAnEmptyReadCanBeTrustedFor() {
        assertEquals(d.minusDays(365), HealthFlow.windowStart(d, historyGranted = true, firstMenstruationGrant = d.minusDays(3)))
        assertEquals(d.minusDays(33), HealthFlow.windowStart(d, historyGranted = false, firstMenstruationGrant = d.minusDays(3)))
        assertEquals(d.minusDays(365), HealthFlow.windowStart(d, historyGranted = false, firstMenstruationGrant = d.minusDays(500)))
    }
}

/** Port of the pure parts of KeelTests/HealthSyncCatalogTests.swift. */
class HealthSyncCatalogTests {
    private val day = LocalDate.of(2026, 10, 20)
    private fun sample() = HealthSnapshot(
        sleepByDay = mapOf(day to 7.5),
        activityAmounts = mapOf("steps" to mapOf(day to 8000.0), "exercise" to mapOf(day to 30.0)),
        vitals = listOf(
            HealthSnapshot.VitalSeries("restingHeartRate", "bpm", mapOf(day to 58.0)),
            HealthSnapshot.VitalSeries("hrvRmssd", "ms", mapOf(day to 42.0)),
            HealthSnapshot.VitalSeries("activeEnergy", "kcal", mapOf(day to 400.0)),
            HealthSnapshot.VitalSeries("bodyMass", "kg", mapOf(day to 68.0)),
        ),
    )

    @Test fun emptyDisabledIsPassthrough() = assertEquals(sample(), HealthSyncCatalog.filter(sample(), emptySet()))

    @Test fun disablingExerciseRemovesOnlyExercise() {
        val out = HealthSyncCatalog.filter(sample(), setOf("exercise"))
        assertNull(out.activityAmounts["exercise"])
        assertTrue("steps" in out.activityAmounts)
    }

    @Test fun disablingSleepClearsSleep() {
        val out = HealthSyncCatalog.filter(sample(), setOf("sleep"))
        assertTrue(out.sleepByDay.isEmpty())
        assertTrue("steps" in out.activityAmounts)
    }

    @Test fun disablingHeartVitalsRemovesItsVitalsOnly() {
        val ids = HealthSyncCatalog.filter(sample(), setOf("heartVitals")).vitals.map { it.typeId }.toSet()
        assertEquals(setOf("activeEnergy", "bodyMass"), ids)
    }

    @Test fun snapshotIsEmptyDetection() {
        assertTrue(HealthSnapshot().isEmpty)
        assertFalse(sample().isEmpty)
        assertFalse(HealthSnapshot(sleepByDay = mapOf(day to 7.0)).isEmpty)
        assertFalse(HealthSnapshot(vitals = listOf(HealthSnapshot.VitalSeries("restingHeartRate", "bpm", mapOf(day to 58.0)))).isEmpty)
        assertTrue(HealthSnapshot(activityAmounts = mapOf("steps" to emptyMap())).isEmpty)
    }

    @Test fun catalogIdsAreUniqueAndAreTheIosIds() {
        val ids = HealthSyncCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            listOf("steps", "exercise", "activeEnergy", "distance", "flights", "sleep", "heartVitals", "bodyTemperature", "bodyWeight", "periods"),
            ids,
        )
    }
}

/** Port of KeelTests/HealthSyncGatingTests.swift (the throttle) and the offer decision in HealthAccessOfferTests. */
class HealthSyncRulesTests {
    private val now = Instant.parse("2026-10-23T10:00:00Z")
    private val window = Duration.ofMinutes(30)

    @Test fun firstSyncRunsWhenNeverSynced() = assertTrue(HealthSync.shouldRun(now, null, force = false, connected = true, minInterval = window))
    @Test fun throttledWithinWindow() = assertFalse(HealthSync.shouldRun(now, now.minusSeconds(60), force = false, connected = true, minInterval = window))
    @Test fun runsAgainAfterWindowElapses() = assertTrue(HealthSync.shouldRun(now, now.minus(window).minusSeconds(1), force = false, connected = true, minInterval = window))
    @Test fun forceBypassesThrottle() = assertTrue(HealthSync.shouldRun(now, now.minusSeconds(60), force = true, connected = true, minInterval = window))
    @Test fun failedSyncLeavesNextAttemptDue() = assertTrue(HealthSync.shouldRun(now, null, force = false, connected = true, minInterval = window))
    @Test fun notConnectedNeverSyncs() = assertFalse(HealthSync.shouldRun(now, null, force = false, connected = false, minInterval = window))
    @Test fun notConnectedBlocksEvenForce() = assertFalse(HealthSync.shouldRun(now, null, force = true, connected = false, minInterval = window))

    @Test fun offerDecision() {
        val ask = HealthRequestStatus.SHOULD_REQUEST
        assertEquals(HealthOffer.CONNECT, HealthSync.offer(ask, true, connected = false, connectAnswered = false, reconnectAnswered = false))
        assertEquals(HealthOffer.NONE, HealthSync.offer(ask, true, connected = false, connectAnswered = true, reconnectAnswered = false))
        assertEquals(HealthOffer.RECONNECT, HealthSync.offer(ask, true, connected = true, connectAnswered = true, reconnectAnswered = false))
        assertEquals(HealthOffer.NONE, HealthSync.offer(ask, true, connected = true, connectAnswered = false, reconnectAnswered = true))
        assertEquals(HealthOffer.NONE, HealthSync.offer(HealthRequestStatus.UNNECESSARY, true, false, false, false))
        assertEquals(HealthOffer.NONE, HealthSync.offer(HealthRequestStatus.UNKNOWN, true, true, false, false))
        assertEquals(HealthOffer.NONE, HealthSync.offer(ask, hasOnboarded = false, connected = false, connectAnswered = false, reconnectAnswered = false))
    }

    /** Plan §3.6: Android's status comes from what Keel has asked for. */
    @Test fun requestStatusFromWhatKeelHasAsked() {
        val reads = setOf("steps", "sleep", "menstruation")
        assertEquals(HealthRequestStatus.UNKNOWN, HealthSync.requestStatus(available = false, asked = reads, requestable = reads))
        assertEquals(HealthRequestStatus.SHOULD_REQUEST, HealthSync.requestStatus(true, emptySet(), reads))
        assertEquals(HealthRequestStatus.UNNECESSARY, HealthSync.requestStatus(true, reads + "extra", reads))
        // A read added in a later version: asked again, once.
        assertEquals(HealthRequestStatus.SHOULD_REQUEST, HealthSync.requestStatus(true, setOf("steps", "sleep"), reads))
    }
}
