package com.therecalibrationyears.keel.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** One reminder occurrence the scheduler decided to create (iOS `NotificationService.Occurrence`). */
data class Occurrence(val slotId: String, val dayKey: String, val day: LocalDate, val hour: Int, val minute: Int)

/**
 * The medication-reminder decision (port of iOS `NotificationService` pure helpers):
 * which dated occurrences to lay out, skip-if-logged, past times skipped, and a horizon
 * budget shared across doses. No side effects; `now` and zone injected.
 */
object ReminderPlanner {
    /** "20260812", used in reminder ids so a single day's occurrence can be cancelled. */
    fun dayKey(day: LocalDate): String = "%04d%02d%02d".format(day.year, day.monthValue, day.dayOfMonth)

    /** A budget per medicine, so many medicines don't crowd everything else out. */
    fun cycleHorizon(activeMedications: Int): Int = (48 / maxOf(activeMedications, 1)).coerceIn(4, 24)

    /**
     * - as directed, or no timed dose: nothing;
     * - only future moments (a dose time already past today is skipped);
     * - today is skipped when the whole day, or that dose, is already logged;
     * - each dose gets its own slice of the horizon.
     */
    fun plannedOccurrences(
        schedule: DoseSchedule,
        cycleHorizon: Int = 24,
        loggedTodayWholeDay: Boolean = false,
        loggedTodaySlots: Set<String> = emptySet(),
        now: ZonedDateTime,
    ): List<Occurrence> {
        val timed = schedule.sortedSlots.filter { it.hasTime }
        if (schedule.kind == DoseSchedule.Kind.AS_NEEDED || timed.isEmpty()) return emptyList()
        val perSlot = maxOf(cycleHorizon / timed.size, 2)
        val zone: ZoneId = now.zone
        val today = now.toLocalDate()
        val out = mutableListOf<Occurrence>()
        for (dose in timed) {
            // Extra candidates, so days skipped (past or logged) don't eat into the budget.
            var scheduled = 0
            for (day in schedule.upcomingDueDates(dose, today, perSlot + 3)) {
                if (scheduled >= perSlot) break
                val fire = day.atTime(dose.hour ?: 0, dose.minute ?: 0).atZone(zone)
                if (!fire.isAfter(now)) continue
                if (day == today && (loggedTodayWholeDay || dose.id in loggedTodaySlots)) continue
                out += Occurrence(dose.id, dayKey(day), day, dose.hour ?: 0, dose.minute ?: 0)
                scheduled++
            }
        }
        return out
    }

    /**
     * A medicine reminder's wording. Reminders show on the lock screen, so the name appears
     * only if she has chosen that in Settings. An auto-logged medicine says plainly that
     * Keel will record it as taken, so her record never claims a dose she didn't expect.
     */
    fun medicationReminderText(name: String, showName: Boolean, autoLog: Boolean): Pair<String, String> {
        val title = if (showName) "Time for $name" else "Time for your medication"
        val body = if (autoLog) "A gentle reminder. Keel will record it as taken unless you change it."
        else "A gentle reminder. Tap to mark it taken."
        return title to body
    }
}

/** Fixed wording of the everyday reminders (iOS `NotificationService.Copy`): no health claims. */
object ReminderCopy {
    const val CHECK_IN_TITLE = "How are you feeling today?"
    const val CHECK_IN_BODY = "A quick check-in adds to your record."
    const val HYDRATION_TITLE = "A glass of water?"
    const val HYDRATION_BODY = "A small sip counts."
    const val MOVEMENT_TITLE = "A little movement?"
    const val MOVEMENT_BODY = "A short walk or a stretch. Whatever feels good today."
    const val WIND_DOWN_TITLE = "Time to wind down"
    const val WIND_DOWN_BODY = "A calmer evening, whatever that looks like for you."
}

/**
 * What turning a reminder on should do (port of iOS `NotificationService.gate`, for
 * Android's model): proceed if notifications are allowed; on Android 13+ ask when she
 * hasn't been asked yet or the system will still show the prompt; otherwise she has said
 * no and the system won't ask again, so guide her to Settings.
 */
enum class NotificationGate { REQUEST, PROCEED, BLOCKED }

object NotificationGates {
    fun gate(enabled: Boolean, sdk: Int, alreadyAsked: Boolean, systemWillAsk: Boolean): NotificationGate = when {
        enabled -> NotificationGate.PROCEED
        sdk >= 33 && (!alreadyAsked || systemWillAsk) -> NotificationGate.REQUEST
        else -> NotificationGate.BLOCKED
    }
}

/**
 * Timing for the lifestyle reminders (port of iOS `ReminderConfig`, same field names, stored
 * as JSON under `keel.reminderConfig`). Medication reminders are set per medicine.
 */
@kotlinx.serialization.Serializable
data class ReminderConfig(
    val checkInHour: Int = 9,
    val checkInMinute: Int = 0,
    val hydrationStartHour: Int = 8,
    val hydrationEndHour: Int = 21,
    val hydrationIntervalHours: Int = 2,
    val movementHour: Int = 14,
    val movementMinute: Int = 0,
    val movementWeekdaysOnly: Boolean = true,
    val windDownHour: Int = 21,
    val windDownMinute: Int = 30,
)
