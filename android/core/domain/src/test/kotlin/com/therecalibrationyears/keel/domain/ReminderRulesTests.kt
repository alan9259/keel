package com.therecalibrationyears.keel.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reminder ids and the everyday reminders (iOS `NotificationService`), as Android arms them. */
class ReminderRulesTests {
    private val utc: ZoneId = ZoneOffset.UTC
    private fun at(d: Int, h: Int, m: Int = 0, zone: ZoneId = utc) = ZonedDateTime.of(2026, 8, d, h, m, 0, 0, zone)

    @Test fun idsMatchIos() {
        val med = "6f9619ff-8b86-d011-b42d-00c04fc964ff"
        assertEquals("keel.med.6F9619FF-8B86-D011-B42D-00C04FC964FF.ABCDEF12.d20260812", ReminderIds.medication(med, "ABCDEF12-3456-7890-ABCD-EF1234567890", "20260812"))
        assertEquals("keel.hydration.14", ReminderIds.hydration(14))
        assertEquals("keel.movement.2", ReminderIds.movement(2))
        assertEquals("keel.movement.daily", ReminderIds.movement(null))
        assertEquals("keel.dailyCheckIn", ReminderIds.CHECK_IN)
        assertEquals("keel.winddown", ReminderIds.WIND_DOWN)
    }

    @Test fun medicationCancelsMatchByDayAndDose() {
        val med = "MED"
        val id = ReminderIds.medication(med, "SLOTAAAA-1", "20260812")
        assertTrue(ReminderIds.matchesMedication(id, med))
        assertTrue(ReminderIds.matchesMedication(id, med, dayKey = "20260812"))
        assertFalse(ReminderIds.matchesMedication(id, med, dayKey = "20260813"))
        assertTrue(ReminderIds.matchesMedication(id, med, dayKey = "20260812", slotId = "SLOTAAAA-1"))
        assertFalse(ReminderIds.matchesMedication(id, med, dayKey = "20260812", slotId = "SLOTBBBB-1"))
        assertFalse(ReminderIds.matchesMedication(id, "MEDX"))
    }

    @Test fun nothingOnMeansNothingPlanned() {
        assertTrue(LifestyleReminders.plan(emptySet(), ReminderConfig()).isEmpty())
        // Medication reminders are per medicine, never part of this plan.
        assertTrue(LifestyleReminders.plan(setOf(ReminderKinds.MEDICATION), ReminderConfig()).isEmpty())
    }

    @Test fun hydrationCoversTheWakingWindowOnly() {
        val hours = LifestyleReminders.plan(setOf(ReminderKinds.HYDRATION), ReminderConfig()).map { it.hour }
        assertEquals(listOf(8, 10, 12, 14, 16, 18, 20), hours) // 8 to 21, every 2 hours
        assertEquals(listOf(9, 12, 15, 18, 21), LifestyleReminders.plan(setOf(ReminderKinds.HYDRATION), ReminderConfig(hydrationStartHour = 9, hydrationIntervalHours = 3)).map { it.hour })
        assertTrue(LifestyleReminders.plan(setOf(ReminderKinds.HYDRATION), ReminderConfig(hydrationStartHour = 22, hydrationEndHour = 8)).isEmpty())
        assertEquals(14, LifestyleReminders.plan(setOf(ReminderKinds.HYDRATION), ReminderConfig(hydrationIntervalHours = 0)).size) // never a loop that can't move
    }

    @Test fun movementIsWeekdaysByDefaultOrEveryDay() {
        val weekdays = LifestyleReminders.plan(setOf(ReminderKinds.MOVEMENT), ReminderConfig())
        assertEquals(listOf(2, 3, 4, 5, 6), weekdays.map { it.weekday })
        assertTrue(weekdays.all { it.hour == 14 && it.minute == 0 })
        val daily = LifestyleReminders.plan(setOf(ReminderKinds.MOVEMENT), ReminderConfig(movementWeekdaysOnly = false)).single()
        assertNull(daily.weekday)
        assertEquals("keel.movement.daily", daily.id)
    }

    @Test fun checkInAndWindDownUseHerTimes() {
        val plan = LifestyleReminders.plan(setOf(ReminderKinds.DAILY_CHECK_IN, ReminderKinds.WIND_DOWN), ReminderConfig(checkInHour = 7, checkInMinute = 15))
        assertEquals(listOf("keel.dailyCheckIn" to (7 to 15), "keel.winddown" to (21 to 30)), plan.map { it.id to (it.hour to it.minute) })
        assertEquals("How are you feeling today?", plan[0].title)
    }

    @Test fun cancelsCoverEveryIdAKindCouldUse() {
        assertEquals(24, LifestyleReminders.allIds(ReminderKinds.HYDRATION).size)
        assertEquals(8, LifestyleReminders.allIds(ReminderKinds.MOVEMENT).size)
        val planned = LifestyleReminders.plan(setOf(ReminderKinds.HYDRATION, ReminderKinds.MOVEMENT), ReminderConfig(movementWeekdaysOnly = false))
        assertTrue(planned.all { it.id in LifestyleReminders.allIds(it.kind) })
    }

    @Test fun nextFireIsStrictlyAfterNow() {
        val r = RepeatingReminder("x", "k", "t", "b", 9, 0)
        assertEquals(at(12, 9), r.nextFire(at(12, 8, 59)))
        assertEquals(at(13, 9), r.nextFire(at(12, 9)))      // at the minute: tomorrow
        assertEquals(at(13, 9), r.nextFire(at(12, 23)))
    }

    @Test fun weeklyNextFireFindsTheRightDay() {
        val monday = RepeatingReminder("x", "k", "t", "b", 14, 0, weekday = 2) // 12 Aug 2026 is a Wednesday
        assertEquals(at(17, 14), monday.nextFire(at(12, 10)))
        assertEquals(at(24, 14), monday.nextFire(at(17, 14, 1)))
    }

    @Test fun nextFireKeepsWallClockTimeAcrossTheClockChange() {
        val sydney = ZoneId.of("Australia/Sydney")
        val r = RepeatingReminder("x", "k", "t", "b", 9, 0)
        val next = r.nextFire(ZonedDateTime.of(2026, 10, 3, 22, 0, 0, 0, sydney)) // clocks go forward overnight
        assertEquals(LocalDate.of(2026, 10, 4), next.toLocalDate())
        assertEquals(9, next.hour)
    }

    @Test fun medicationRemindersCarryIdsWordingAndFireTimes() {
        val dose = DoseSlot(id = "ABCDEF12-0000-0000-0000-000000000000", hour = 20)
        val r = MedicationReminders.plan(
            medicationId = "med-1", name = "Prometrium", schedule = DoseSchedule(slots = listOf(dose)), autoLog = false,
            showName = false, cycleHorizon = 4, loggedTodayWholeDay = false, loggedTodaySlots = emptySet(), now = at(12, 9),
        )
        assertEquals(4, r.size)
        assertEquals("keel.med.MED-1.ABCDEF12.d20260812", r.first().id)
        assertEquals(at(12, 20), r.first().fireAt)
        assertEquals("Time for your medication", r.first().title)
        assertEquals(LocalDate.of(2026, 8, 12), MedicationReminders.dayOf("20260812"))
        assertNull(MedicationReminders.dayOf("bad"))
    }
}
