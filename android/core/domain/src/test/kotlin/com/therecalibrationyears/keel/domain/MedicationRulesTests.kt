package com.therecalibrationyears.keel.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val UTC: ZoneId = ZoneOffset.UTC
private fun day(y: Int, m: Int, d: Int): LocalDate = LocalDate.of(y, m, d)
private fun at(y: Int, m: Int, d: Int, hh: Int = 0, mm: Int = 0): ZonedDateTime = ZonedDateTime.of(y, m, d, hh, mm, 0, 0, UTC)

/** Locale words fixed for tests: en_AU, 12-hour, week starting Monday. */
internal object TestWords : ScheduleWords {
    override fun time(slot: DoseSlot): String {
        val h = slot.hour ?: return "Any time"
        val m = slot.minute ?: 0
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(h12, m, if (h < 12) "am" else "pm")
    }
    override fun shortWeekday(weekday: Int): String = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")[weekday - 1]
    override val firstWeekday: Int = 2
}

/**
 * Port of KeelTests/DoseScheduleTests.swift: when a medication is due and which doses fall
 * on a day. Fixed UTC dates, so nothing depends on the machine's zone or "today".
 */
class DoseScheduleTests {
    @Test fun asNeededAlwaysDueButSchedulesNothing() {
        val s = DoseSchedule(kind = DoseSchedule.Kind.AS_NEEDED, slots = listOf(DoseSlot(hour = 8)))
        assertTrue(s.isDue(day(2026, 8, 12)))
        assertTrue(ReminderPlanner.plannedOccurrences(s, now = at(2026, 8, 12, 6)).isEmpty())
    }

    @Test fun weeklyEveryDayIsAlwaysDue() {
        val s = DoseSchedule(kind = DoseSchedule.Kind.WEEKLY, slots = listOf(DoseSlot(weekdays = (1..7).toSet(), hour = 8)))
        for (d in 12..18) assertTrue("day $d", s.isDue(day(2026, 8, d)))
    }

    @Test fun weeklySpecificDaysDueOnlyThose() {
        // 2026-08-12 is a Wednesday (weekday 4). Mon=2, Wed=4, Fri=6.
        val s = DoseSchedule(kind = DoseSchedule.Kind.WEEKLY, slots = listOf(DoseSlot(weekdays = setOf(2, 4, 6), hour = 8)))
        assertEquals(4, day(2026, 8, 12).iosWeekday())
        assertTrue(s.isDue(day(2026, 8, 12)))
        assertFalse(s.isDue(day(2026, 8, 13)))
        assertTrue(s.isDue(day(2026, 8, 14)))
        assertFalse(s.isDue(day(2026, 8, 15)))
    }

    @Test fun dueSlotsFilterByWeekday() {
        val morning = DoseSlot(weekdays = (1..7).toSet(), hour = 8)
        val weekendEve = DoseSlot(weekdays = setOf(1, 7), hour = 20)
        val s = DoseSchedule(kind = DoseSchedule.Kind.WEEKLY, slots = listOf(morning, weekendEve))
        assertEquals(1, s.dueSlots(day(2026, 8, 12)).size)
        assertEquals(2, s.dueSlots(day(2026, 8, 15)).size)
    }

    @Test fun cycleActiveDaysAndPause() {
        val anchor = day(2026, 8, 1)
        val s = DoseSchedule(kind = DoseSchedule.Kind.CYCLE, slots = listOf(DoseSlot(hour = 8)), cycleLength = 28, pauseDays = 7, anchor = anchor)
        assertEquals(21, s.activeDays)
        assertTrue(s.isDue(anchor))
        assertTrue(s.isDue(day(2026, 8, 21)))
        assertFalse(s.isDue(day(2026, 8, 22)))
        assertFalse(s.isDue(day(2026, 8, 28)))
        assertTrue(s.isDue(day(2026, 8, 29)))
    }

    @Test fun cycleDayWrapsAndHandlesBeforeAnchor() {
        val anchor = day(2026, 8, 10)
        val s = DoseSchedule(kind = DoseSchedule.Kind.CYCLE, cycleLength = 28, pauseDays = 7, anchor = anchor)
        assertEquals(1, s.cycleDay(anchor))
        assertEquals(2, s.cycleDay(day(2026, 8, 11)))
        assertEquals(1, s.cycleDay(day(2026, 9, 7)))
        val before = s.cycleDay(day(2026, 8, 9))
        assertNotNull(before)
        assertEquals(28, before)
    }

    @Test fun upcomingDueDatesWeeklyEveryDayIsConsecutive() {
        val dose = DoseSlot(weekdays = (1..7).toSet(), hour = 8)
        val s = DoseSchedule(kind = DoseSchedule.Kind.WEEKLY, slots = listOf(dose))
        assertEquals(listOf(12, 13, 14, 15, 16), s.upcomingDueDates(dose, day(2026, 8, 12), 5).map { it.dayOfMonth })
    }

    @Test fun upcomingDueDatesCycleSkipsPause() {
        val dose = DoseSlot(hour = 8)
        val s = DoseSchedule(kind = DoseSchedule.Kind.CYCLE, slots = listOf(dose), cycleLength = 28, pauseDays = 7, anchor = day(2026, 8, 1))
        assertEquals(listOf(20, 21, 29), s.upcomingDueDates(dose, day(2026, 8, 20), 3).map { it.dayOfMonth })
    }

    // Android additions: the edges of the "never hide an entry" rule.

    @Test fun cycleWithNoActiveDaysIsAlwaysDue() {
        val s = DoseSchedule(kind = DoseSchedule.Kind.CYCLE, cycleLength = 7, pauseDays = 7, anchor = day(2026, 8, 1))
        assertTrue(s.isDue(day(2026, 8, 3)))
    }

    @Test fun cycleWithoutAnchorCountsFromTheDayAsked() {
        val s = DoseSchedule(kind = DoseSchedule.Kind.CYCLE)
        assertEquals(1, s.cycleDay(day(2026, 8, 3)))
        assertNull(DoseSchedule(kind = DoseSchedule.Kind.WEEKLY).cycleDay(day(2026, 8, 3)))
    }

    @Test fun nextDueLooksAfterTheDayAndIsBounded() {
        val s = DoseSchedule(kind = DoseSchedule.Kind.WEEKLY, slots = listOf(DoseSlot(weekdays = setOf(2))))
        assertEquals(day(2026, 8, 17), s.nextDue(day(2026, 8, 12))) // next Monday
        assertEquals(day(2026, 8, 24), s.nextDue(day(2026, 8, 17))) // never the day itself
        assertNull(s.nextDue(day(2026, 8, 12), within = 3))
    }
}

/** Port of KeelTests/DoseScheduleSummaryTests.swift, plus the locale parts pinned with fixed words. */
class DoseScheduleSummaryTests {
    @Test fun phraseNamedSets() {
        assertEquals("Every day", DoseSchedule.phrase((1..7).toSet(), TestWords))
        assertEquals("Every day", DoseSchedule.phrase(emptySet(), TestWords))
        assertEquals("Weekdays", DoseSchedule.phrase(setOf(2, 3, 4, 5, 6), TestWords))
        assertEquals("Weekends", DoseSchedule.phrase(setOf(1, 7), TestWords))
    }

    @Test fun listJoiner() {
        assertEquals("a", DoseSchedule.list(listOf("a")))
        assertEquals("a and b", DoseSchedule.list(listOf("a", "b")))
        assertEquals("a, b and c", DoseSchedule.list(listOf("a", "b", "c")))
        assertEquals("", DoseSchedule.list(emptyList()))
    }

    @Test fun summaryEveryDayNoTime() {
        assertEquals("Every day", DoseSchedule(slots = listOf(DoseSlot(weekdays = (1..7).toSet()))).summary(TestWords))
    }

    @Test fun summaryWeekdaysNoTime() {
        assertEquals("Weekdays", DoseSchedule(slots = listOf(DoseSlot(weekdays = setOf(2, 3, 4, 5, 6)))).summary(TestWords))
    }

    @Test fun summaryCyclePrefix() {
        val s = DoseSchedule(kind = DoseSchedule.Kind.CYCLE, slots = listOf(DoseSlot()), cycleLength = 28, pauseDays = 7)
        assertEquals(21, s.activeDays)
        assertEquals("21 days on, 7 off", s.summary(TestWords))
    }

    @Test fun summaryAsDirected() {
        assertEquals("As directed", DoseSchedule(kind = DoseSchedule.Kind.AS_NEEDED, slots = listOf(DoseSlot())).summary(TestWords))
    }

    @Test fun summaryWithTimeIncludesPhraseAndAt() {
        val s = DoseSchedule(slots = listOf(DoseSlot(weekdays = (1..7).toSet(), hour = 8)))
        assertTrue(s.summary(TestWords), s.summary(TestWords).startsWith("Every day at "))
    }

    @Test fun weekdaysUnionAcrossSlots() {
        val s = DoseSchedule(slots = listOf(DoseSlot(weekdays = setOf(2), hour = 8), DoseSlot(weekdays = setOf(4, 6), hour = 20)))
        assertEquals(setOf(2, 4, 6), s.weekdays)
    }

    // Android additions: with the locale words fixed, the whole sentence can be pinned.

    @Test fun narrowerDoseGetsItsOwnClause() {
        val s = DoseSchedule(slots = listOf(DoseSlot(hour = 8), DoseSlot(weekdays = setOf(1, 7), hour = 10)))
        assertEquals("Every day at 8:00 am, Weekends also at 10:00 am", s.summary(TestWords))
    }

    @Test fun namedDaysFollowHerWeekOrder() {
        // Sunday (1) comes last in a week that starts on Monday.
        assertEquals("Mon, Wed and Sun", DoseSchedule.phrase(setOf(1, 2, 4), TestWords))
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 1), DoseSchedule.orderedWeekdays(2))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), DoseSchedule.orderedWeekdays(1))
    }

    @Test fun cycleAndAsDirectedListTheirTimes() {
        val cycle = DoseSchedule(kind = DoseSchedule.Kind.CYCLE, slots = listOf(DoseSlot(hour = 20), DoseSlot(hour = 8)))
        assertEquals("21 days on, 7 off at 8:00 am and 8:00 pm", cycle.summary(TestWords))
        assertEquals("As directed at 9:30 pm", DoseSchedule(kind = DoseSchedule.Kind.AS_NEEDED, slots = listOf(DoseSlot(hour = 21, minute = 30))).summary(TestWords))
    }
}

/** Port of KeelTests/DoseUnitTests.swift. */
class DoseUnitTests {
    @Test fun pumpsIsSelectable() {
        assertTrue(DoseUnit.PUMPS in DoseUnit.entries)
        assertEquals("pumps", DoseUnit.PUMPS.label)
    }

    @Test fun pumpsFormatSingularisesAndSpaces() {
        assertEquals("1 pump", DoseUnit.PUMPS.format(1.0))
        assertEquals("2 pumps", DoseUnit.PUMPS.format(2.0))
        assertEquals("0.5 pumps", DoseUnit.PUMPS.format(0.5))
    }

    @Test fun otherUnitsAreUnchanged() {
        assertEquals("400mg", DoseUnit.MG.format(400.0))
        assertEquals("2000 IU", DoseUnit.IU.format(2000.0))
        assertEquals("0.5ml", DoseUnit.ML.format(0.5))
    }

    // Android addition: the "%" unit and a non-terminating decimal read as iOS writes them.
    @Test fun percentAndDecimalsMatchIos() {
        assertEquals("1%", DoseUnit.PERCENT.format(1.0))
        assertEquals("0.1mg", DoseUnit.MG.format(0.1))
        assertEquals("1.25mg", DoseUnit.MG.format(1.25))
    }
}

/**
 * Port of KeelTests/NotificationGateTests.swift, adapted to Android's model: there is no
 * "provisional", and after a second refusal the system stops asking, so the toggle must
 * send her to Settings rather than silently doing nothing.
 */
class NotificationGateTests {
    @Test fun notYetAskedRequestsThePrompt() {
        assertEquals(NotificationGate.REQUEST, NotificationGates.gate(enabled = false, sdk = 33, alreadyAsked = false, systemWillAsk = false))
    }

    @Test fun refusedOnceStillRequests() {
        assertEquals(NotificationGate.REQUEST, NotificationGates.gate(enabled = false, sdk = 34, alreadyAsked = true, systemWillAsk = true))
    }

    @Test fun deniedIsBlockedSoWeGuideToSettings() {
        assertEquals(NotificationGate.BLOCKED, NotificationGates.gate(enabled = false, sdk = 34, alreadyAsked = true, systemWillAsk = false))
    }

    @Test fun authorizedProceeds() {
        assertEquals(NotificationGate.PROCEED, NotificationGates.gate(enabled = true, sdk = 36, alreadyAsked = true, systemWillAsk = false))
        assertEquals(NotificationGate.PROCEED, NotificationGates.gate(enabled = true, sdk = 28, alreadyAsked = false, systemWillAsk = false))
    }

    @Test fun turnedOffBelowAndroid13IsBlocked() {
        // No runtime prompt exists before 13, so turned off in Settings means Settings.
        assertEquals(NotificationGate.BLOCKED, NotificationGates.gate(enabled = false, sdk = 28, alreadyAsked = false, systemWillAsk = false))
    }
}

/** Port of KeelTests/ReminderWordingTests.swift (the setting default is in core/data). */
class ReminderWordingTests {
    @Test fun medicineNameHiddenByDefault() {
        val (title, body) = ReminderPlanner.medicationReminderText("Prometrium", showName = false, autoLog = false)
        assertEquals("Time for your medication", title)
        assertFalse(title.contains("Prometrium"))
        assertFalse(body.contains("Prometrium"))
    }

    @Test fun medicineNameShownWhenSheChoosesIt() {
        assertEquals("Time for Prometrium", ReminderPlanner.medicationReminderText("Prometrium", showName = true, autoLog = false).first)
    }

    @Test fun bodiesAreHonestAboutLogging() {
        assertEquals("A gentle reminder. Tap to mark it taken.", ReminderPlanner.medicationReminderText("x", false, false).second)
        assertEquals(
            "A gentle reminder. Keel will record it as taken unless you change it.",
            ReminderPlanner.medicationReminderText("x", false, true).second,
        )
    }

    @Test fun everydayRemindersMakeNoHealthClaims() {
        val c = ReminderCopy
        assertEquals("A small sip counts.", c.HYDRATION_BODY)
        assertEquals("A quick check-in adds to your record.", c.CHECK_IN_BODY)
        assertEquals("A calmer evening, whatever that looks like for you.", c.WIND_DOWN_BODY)
        val all = listOf(
            c.CHECK_IN_TITLE, c.CHECK_IN_BODY, c.HYDRATION_TITLE, c.HYDRATION_BODY,
            c.MOVEMENT_TITLE, c.MOVEMENT_BODY, c.WIND_DOWN_TITLE, c.WIND_DOWN_BODY,
        )
        for (text in all) for (claim in listOf("can help", "headache", "better night", "sharpens", "—", "–")) {
            assertFalse("$text contains $claim", text.lowercase().contains(claim))
        }
    }
}

/**
 * Port of KeelTests/MedReminderPlanTests.swift: which dated occurrences get created,
 * honouring skip-if-logged, past-time and the per-dose horizon budget.
 */
class MedReminderPlanTests {
    private fun weeklyEveryDay(hour: Int): Pair<DoseSchedule, DoseSlot> {
        val dose = DoseSlot(weekdays = (1..7).toSet(), hour = hour)
        return DoseSchedule(kind = DoseSchedule.Kind.WEEKLY, slots = listOf(dose)) to dose
    }

    @Test fun dayKeyFormat() {
        assertEquals("20260812", ReminderPlanner.dayKey(day(2026, 8, 12)))
        assertEquals("20261201", ReminderPlanner.dayKey(day(2026, 12, 1)))
    }

    @Test fun cycleHorizonBudget() {
        assertEquals(24, ReminderPlanner.cycleHorizon(1))
        assertEquals(24, ReminderPlanner.cycleHorizon(2))
        assertEquals(16, ReminderPlanner.cycleHorizon(3))
        assertEquals(12, ReminderPlanner.cycleHorizon(4))
        assertEquals(4, ReminderPlanner.cycleHorizon(12))
        assertEquals(4, ReminderPlanner.cycleHorizon(100))
        assertEquals(24, ReminderPlanner.cycleHorizon(0))
    }

    @Test fun todayScheduledWhenFutureAndUnlogged() {
        val (s, dose) = weeklyEveryDay(20)
        val occ = ReminderPlanner.plannedOccurrences(s, cycleHorizon = 24, now = at(2026, 8, 12, 9))
        assertEquals(24, occ.size)
        assertEquals("20260812", occ.first().dayKey)
        assertTrue(occ.all { it.slotId == dose.id })
        assertEquals(24, occ.map { it.dayKey }.toSet().size)
    }

    @Test fun todaySkippedWhenTimeAlreadyPassed() {
        val (s, _) = weeklyEveryDay(8)
        val occ = ReminderPlanner.plannedOccurrences(s, cycleHorizon = 24, now = at(2026, 8, 12, 9))
        assertEquals(24, occ.size)
        assertEquals("20260813", occ.first().dayKey)
        assertFalse(occ.any { it.dayKey == "20260812" })
    }

    @Test fun todaySkippedWhenWholeDayLogged() {
        val (s, _) = weeklyEveryDay(20)
        val occ = ReminderPlanner.plannedOccurrences(s, cycleHorizon = 24, loggedTodayWholeDay = true, now = at(2026, 8, 12, 9))
        assertFalse(occ.any { it.dayKey == "20260812" })
        assertEquals("20260813", occ.first().dayKey)
        assertEquals(24, occ.size)
    }

    @Test fun todaySkippedWhenThatSlotLogged() {
        val (s, dose) = weeklyEveryDay(20)
        val occ = ReminderPlanner.plannedOccurrences(s, cycleHorizon = 24, loggedTodaySlots = setOf(dose.id), now = at(2026, 8, 12, 9))
        assertFalse(occ.any { it.dayKey == "20260812" })
        assertEquals(24, occ.size)
    }

    @Test fun unrelatedSlotLoggedDoesNotSkipToday() {
        val (s, _) = weeklyEveryDay(20)
        val occ = ReminderPlanner.plannedOccurrences(s, cycleHorizon = 24, loggedTodaySlots = setOf("some-other-slot"), now = at(2026, 8, 12, 9))
        assertTrue(occ.any { it.dayKey == "20260812" })
    }

    @Test fun multiDoseSplitsHorizonPerSlot() {
        val morning = DoseSlot(weekdays = (1..7).toSet(), hour = 8)
        val evening = DoseSlot(weekdays = (1..7).toSet(), hour = 20)
        val s = DoseSchedule(kind = DoseSchedule.Kind.WEEKLY, slots = listOf(morning, evening))
        val occ = ReminderPlanner.plannedOccurrences(s, cycleHorizon = 24, now = at(2026, 8, 12, 6))
        assertEquals(24, occ.size)
        assertEquals(12, occ.count { it.slotId == morning.id })
        assertEquals(12, occ.count { it.slotId == evening.id })
    }

    @Test fun noTimedDoseSchedulesNothing() {
        val s = DoseSchedule(kind = DoseSchedule.Kind.WEEKLY, slots = listOf(DoseSlot(weekdays = (1..7).toSet())))
        assertTrue(ReminderPlanner.plannedOccurrences(s, now = at(2026, 8, 12, 6)).isEmpty())
    }

    @Test fun cycleScheduleSkipsPauseDays() {
        val s = DoseSchedule(kind = DoseSchedule.Kind.CYCLE, slots = listOf(DoseSlot(hour = 8)), cycleLength = 28, pauseDays = 7, anchor = day(2026, 8, 1))
        val days = ReminderPlanner.plannedOccurrences(s, cycleHorizon = 5, now = at(2026, 8, 20, 6)).map { it.dayKey }
        assertFalse(days.contains("20260822"))
        assertFalse(days.contains("20260828"))
        assertTrue(days.contains("20260820"))
        assertTrue(days.contains("20260821"))
        assertTrue(days.contains("20260829"))
    }

    // Android addition: the fire time is the local wall-clock time in her zone, also on the
    // day the clocks go forward (Sydney, 4 Oct 2026: 2:00 am does not exist).
    @Test fun dstGapTimeMovesForwardRatherThanVanishing() {
        val sydney = ZoneId.of("Australia/Sydney")
        val dose = DoseSlot(hour = 2, minute = 30)
        val s = DoseSchedule(slots = listOf(dose))
        val occ = ReminderPlanner.plannedOccurrences(s, cycleHorizon = 4, now = ZonedDateTime.of(2026, 10, 3, 12, 0, 0, 0, sydney))
        assertEquals(listOf("20261004", "20261005", "20261006", "20261007"), occ.map { it.dayKey })
        assertEquals(2, occ.first().hour) // her chosen time is kept; the alarm layer resolves the gap
    }
}

/** Port of KeelTests/MedicationScheduleTests.swift: the stored columns round-trip. */
class MedicationScheduleTests {
    private val now = Instant.parse("2026-08-12T00:00:00Z")
    private fun roundTrip(s: DoseSchedule) = MedicationSchedules.read(MedicationSchedules.write(s, UTC, now), UTC)

    @Test fun weeklyScheduleRoundTrips() {
        val cols = MedicationSchedules.write(DoseSchedule(slots = listOf(DoseSlot(weekdays = setOf(2, 4, 6), hour = 8, minute = 30))), UTC, now)
        val back = MedicationSchedules.read(cols, UTC)
        assertEquals(DoseSchedule.Kind.WEEKLY, back.kind)
        assertEquals(1, back.sortedSlots.size)
        val s = back.sortedSlots.first()
        assertEquals(8, s.hour)
        assertEquals(30, s.minute)
        assertEquals(setOf(2, 4, 6), s.weekdays)
        assertTrue(cols.hasSchedule)
    }

    @Test fun cycleScheduleRoundTrips() {
        val back = roundTrip(DoseSchedule(kind = DoseSchedule.Kind.CYCLE, slots = listOf(DoseSlot(hour = 9)), cycleLength = 28, pauseDays = 7))
        assertEquals(DoseSchedule.Kind.CYCLE, back.kind)
        assertEquals(28, back.cycleLength)
        assertEquals(7, back.pauseDays)
        assertEquals(9, back.sortedSlots.first().hour)
    }

    @Test fun multipleDoseTimesRoundTrip() {
        val back = roundTrip(DoseSchedule(slots = listOf(DoseSlot(hour = 20), DoseSlot(hour = 8))))
        assertEquals(listOf(8, 20), back.sortedSlots.mapNotNull { it.hour })
    }

    @Test fun untimedDosePreservedAsHasScheduleWithNoTime() {
        val cols = MedicationSchedules.write(DoseSchedule(slots = listOf(DoseSlot())), UTC, now)
        assertTrue(cols.hasSchedule)
        assertFalse(MedicationSchedules.read(cols, UTC).sortedSlots.any { it.hasTime })
    }

    @Test fun noScheduleReadsAsNotHavingOne() {
        assertFalse(ScheduleColumns().hasSchedule)
    }

    @Test fun weekdaysMaskRoundTripsThroughStaticHelpers() {
        val days = setOf(1, 3, 5, 7)
        assertEquals(days, MedicationSchedules.weekdays(MedicationSchedules.mask(days)))
        assertEquals(0, MedicationSchedules.mask(emptySet()))
    }

    // Android additions: what iOS writes, and older shapes, read back the same way.

    @Test fun slotJsonMatchesTheIosShape() {
        val slot = DoseSlot(id = "6F9619FF-8B86-D011-B42D-00C04FC964FF", weekdays = setOf(6, 2), hour = 8, minute = 0)
        assertEquals(
            """[{"id":"6F9619FF-8B86-D011-B42D-00C04FC964FF","weekdays":[2,6],"hour":8,"minute":0}]""",
            DoseSlotsJson.encode(listOf(slot)),
        )
        val fresh = DoseSlot().id
        assertEquals(fresh.uppercase(), fresh)
    }

    @Test fun slotJsonReadsTolerantly() {
        // Lower-case id, no weekdays, no time: an older entry.
        val back = DoseSlotsJson.decode("""[{"id":"6f9619ff-8b86-d011-b42d-00c04fc964ff"}]""")!!.single()
        assertEquals("6F9619FF-8B86-D011-B42D-00C04FC964FF", back.id)
        assertEquals((1..7).toSet(), back.weekdays)
        assertNull(back.hour)
        assertNull(DoseSlotsJson.decode("not json"))
        assertNull(DoseSlotsJson.decode("[]"))
        assertNull(DoseSlotsJson.decode(null))
    }

    @Test fun daysCarriedOnTheScheduleMoveOntoTheDose() {
        // Written before days belonged to the dose: mask Mon+Thu, dose says every day.
        val cols = ScheduleColumns(
            scheduleKindRaw = "weekly",
            weekdaysMask = MedicationSchedules.mask(setOf(2, 5)),
            doseTimesJSON = """[{"id":"A","weekdays":[1,2,3,4,5,6,7],"hour":8}]""",
        )
        assertEquals(setOf(2, 5), MedicationSchedules.read(cols, UTC).slots.single().weekdays)
    }

    @Test fun noDoseListRebuildsFromTheOlderColumns() {
        val cols = ScheduleColumns(
            scheduleKindRaw = "weekly",
            weekdaysMask = MedicationSchedules.mask(setOf(3)),
            doseTime = Instant.parse("2026-01-01T21:15:00Z"),
        )
        val slot = MedicationSchedules.read(cols, UTC).slots.single()
        assertEquals(setOf(3), slot.weekdays)
        assertEquals(21, slot.hour)
        assertEquals(15, slot.minute)
        // Nothing set at all reads as "as directed", every day, no time.
        val none = MedicationSchedules.read(ScheduleColumns(), UTC)
        assertEquals(DoseSchedule.Kind.AS_NEEDED, none.kind)
        assertEquals((1..7).toSet(), none.slots.single().weekdays)
    }

    @Test fun writeKeepsTheFlatColumnsFilled() {
        val anchor = day(2026, 8, 1)
        val cols = MedicationSchedules.write(
            DoseSchedule(kind = DoseSchedule.Kind.CYCLE, slots = listOf(DoseSlot(weekdays = setOf(2), hour = 20), DoseSlot(weekdays = setOf(4), hour = 7, minute = 45)), anchor = anchor),
            UTC, now,
        )
        assertEquals(MedicationSchedules.mask(setOf(2, 4)), cols.weekdaysMask)
        assertEquals(Instant.parse("2026-08-12T07:45:00Z"), cols.doseTime) // first time of day, carried on today
        assertEquals(anchor, MedicationSchedules.read(cols, UTC).anchor)
    }
}

/** Port of KeelTests/MedicationNameCleanupTests.swift (pure part; the repository part is in core/data). */
class MedicationNameCleanupTests {
    @Test fun stripsOnlyATrailingExample() {
        assertEquals("Oral micronised progesterone", MedicationSchedules.nameWithoutExample("Oral micronised progesterone (e.g. Prometrium)"))
        assertEquals("Oestrogen gel", MedicationSchedules.nameWithoutExample("Oestrogen gel (E.G. Estrogel) "))
        assertEquals("Magnesium (glycinate)", MedicationSchedules.nameWithoutExample("Magnesium (glycinate)"))
        assertEquals("Combined patch (oestrogen and progestogen)", MedicationSchedules.nameWithoutExample("Combined patch (oestrogen and progestogen)"))
        assertEquals("Vitamin D", MedicationSchedules.nameWithoutExample("Vitamin D"))
        assertEquals("(e.g. Prometrium)", MedicationSchedules.nameWithoutExample("(e.g. Prometrium)"))
    }
}

/** The pure parts of iOS `MedicationRepository`: legacy migration, auto-log and the draft. */
class MedicationRepositoryRulesTests {
    private val start = day(2026, 8, 12) // a Wednesday

    @Test fun legacyFrequenciesBecomeSchedules() {
        val daily = MedicationSchedules.legacySchedule("daily", null, start)
        assertEquals(DoseSchedule.Kind.WEEKLY, daily.kind)
        assertEquals((1..7).toSet(), daily.slots.single().weekdays)
        assertNull(daily.slots.single().hour)
        assertEquals(setOf(2, 5), MedicationSchedules.legacySchedule("twiceWeekly", null, start).slots.single().weekdays)
        assertEquals(setOf(4), MedicationSchedules.legacySchedule("weekly", null, start).slots.single().weekdays)
        val cyclic = MedicationSchedules.legacySchedule("cyclic", null, start)
        assertEquals(DoseSchedule.Kind.CYCLE, cyclic.kind)
        assertEquals(start, cyclic.anchor)
        assertEquals(21, cyclic.activeDays)
        assertEquals(DoseSchedule.Kind.AS_NEEDED, MedicationSchedules.legacySchedule("asDirected", null, start).kind)
        assertEquals(DoseSchedule.Kind.WEEKLY, MedicationSchedules.legacySchedule(null, null, start).kind)
    }

    @Test fun legacyDayPartsBecomeTimes() {
        assertEquals(9 to 0, MedicationSchedules.legacySchedule("daily", "morning", start).slots.single().let { it.hour to it.minute })
        assertEquals(13, MedicationSchedules.legacySchedule("daily", "afternoon", start).slots.single().hour)
        assertEquals(20, MedicationSchedules.legacySchedule("daily", "evening", start).slots.single().hour)
    }

    @Test fun autoLogLogsOnlyArrivedDosesBySlot() {
        val morning = DoseSlot(hour = 8)
        val evening = DoseSlot(hour = 20)
        val untimed = DoseSlot()
        val s = DoseSchedule(slots = listOf(morning, evening, untimed))
        val noon = at(2026, 8, 12, 12)
        assertEquals(listOf(morning.id, untimed.id), MedicationSchedules.autoLogSlots(s, noon) { false })
        // Already logged: nothing again.
        assertEquals(listOf(untimed.id), MedicationSchedules.autoLogSlots(s, noon) { it == morning.id })
        // Exactly on time counts as arrived.
        assertEquals(listOf(morning.id, evening.id, untimed.id), MedicationSchedules.autoLogSlots(s, at(2026, 8, 12, 20)) { false })
    }

    /** Regression for the iOS bug: a day the medicine isn't due logged a whole day. */
    @Test fun autoLogLogsNothingOnADayThatIsNotDue() {
        val s = DoseSchedule(slots = listOf(DoseSlot(weekdays = setOf(2), hour = 8)))
        assertTrue(MedicationSchedules.autoLogSlots(s, at(2026, 8, 12, 12)) { false }.isEmpty()) // Wednesday
    }

    @Test fun draftTextFallsBackToWhatSheTyped() {
        val d = TreatmentDraft(name = "Magnesium", kind = TreatmentKind.SUPPLEMENT, legacyDosage = "one scoop", legacyTiming = "Night")
        assertEquals("one scoop", d.doseText)
        assertEquals("Night", d.timingText(TestWords))
        val structured = d.copy(doseAmount = 400.0, doseUnit = DoseUnit.MG, schedule = DoseSchedule(slots = listOf(DoseSlot(hour = 21))))
        assertEquals("400mg", structured.doseText)
        assertEquals("9:00 pm", structured.timingText(TestWords))
        assertNull(structured.copy(method = MedicationMethod.CAPSULE).effectiveMethod)
        assertEquals(MedicationMethod.GEL, structured.copy(kind = TreatmentKind.TREATMENT, method = MedicationMethod.GEL).effectiveMethod)
    }

    @Test fun detailLineSkipsBlanks() {
        assertEquals("400mg · Every day", MedicationSchedules.detailLine(false, "400mg", "Every day", "8:00 am"))
        assertEquals("Compounded · Night", MedicationSchedules.detailLine(true, "400mg", null, "Night"))
        assertEquals("", MedicationSchedules.detailLine(false, "", null, ""))
    }
}
