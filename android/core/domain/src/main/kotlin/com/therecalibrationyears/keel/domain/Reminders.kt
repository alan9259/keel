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
 * Reminders on time (Android only, plan D25). Unless she allows alarms and reminders for Keel,
 * Android 12+ may deliver a reminder up to an hour after its time, and Android 14+ starts with
 * that off. Keel explains it once, when it first matters, and the Reminders screen keeps a
 * note while it's off. Never during onboarding, and never before notifications can show.
 */
object ExactTiming {
    /**
     * Explain it now: she has just set up a medicine that has reminders armed, alarms are
     * inexact, notifications can show, and she hasn't been told before.
     */
    fun shouldOffer(exactAllowed: Boolean, alreadyOffered: Boolean, notificationsAllowed: Boolean, medicineHasReminders: Boolean): Boolean =
        !exactAllowed && !alreadyOffered && notificationsAllowed && medicineHasReminders

    /** The note on the Reminders screen: alarms are inexact and some reminder is on. */
    fun showsNote(exactAllowed: Boolean, notificationsAllowed: Boolean, anyReminderOn: Boolean): Boolean =
        !exactAllowed && notificationsAllowed && anyReminderOn
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

/** The everyday reminders, by the ids iOS stores in `keel.reminders`. */
object ReminderKinds {
    const val DAILY_CHECK_IN = "dailyCheckIn"
    const val MEDICATION = "medication"
    const val HYDRATION = "hydration"
    const val MOVEMENT = "movement"
    const val WIND_DOWN = "winddown"
    val LIFESTYLE = listOf(DAILY_CHECK_IN, HYDRATION, MOVEMENT, WIND_DOWN)
}

/** Reminder identifiers, character for character with iOS (`NotificationService`). */
object ReminderIds {
    const val MED_PREFIX = "keel.med."
    const val CHECK_IN = "keel.dailyCheckIn"
    const val HYDRATION_PREFIX = "keel.hydration."
    const val MOVEMENT_PREFIX = "keel.movement."
    const val WIND_DOWN = "keel.winddown"

    /** `keel.med.<UUID>.<first 8 of the slot id>.d<yyyyMMdd>`. */
    fun medication(medicationId: String, slotId: String, dayKey: String): String =
        "$MED_PREFIX${medicationId.uppercase()}.${slotId.take(8)}.d$dayKey"

    fun medicationPrefix(medicationId: String): String = "$MED_PREFIX${medicationId.uppercase()}"

    /**
     * Whether a medication reminder id is for this medicine on this day, and (when [slotId] is
     * given) this dose: the iOS day/slot cancel, as a test on the id.
     */
    fun matchesMedication(id: String, medicationId: String, dayKey: String? = null, slotId: String? = null): Boolean {
        if (!id.startsWith(medicationPrefix(medicationId))) return false
        if (dayKey != null && !id.endsWith(".d$dayKey")) return false
        if (slotId != null && !id.contains(".${slotId.take(8)}.")) return false
        return true
    }

    fun hydration(hour: Int): String = "$HYDRATION_PREFIX$hour"
    fun movement(weekday: Int?): String = "$MOVEMENT_PREFIX${weekday ?: "daily"}"
}

/**
 * One repeating everyday reminder (iOS uses a repeating calendar trigger; Android arms the
 * next occurrence and re-arms when it fires). [weekday] is the iOS weekday (1 is Sunday), or
 * null for every day.
 */
data class RepeatingReminder(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val hour: Int,
    val minute: Int,
    val weekday: Int? = null,
) {
    /** The next time it fires strictly after [now], in her wall-clock time. */
    fun nextFire(now: ZonedDateTime): ZonedDateTime {
        var day = now.toLocalDate()
        repeat(8) {
            if (weekday == null || day.iosWeekday() == weekday) {
                val at = day.atTime(hour, minute).atZone(now.zone)
                if (at.isAfter(now)) return at
            }
            day = day.plusDays(1)
        }
        error("unreachable: a weekly reminder recurs within 8 days") // not-copy: an exception message, never shown
    }
}

/** What the everyday reminders she has on look like (iOS `schedule*` functions). */
object LifestyleReminders {
    /** Every reminder for the kinds she has on, from her timing. */
    fun plan(enabled: Set<String>, config: ReminderConfig): List<RepeatingReminder> = buildList {
        if (ReminderKinds.DAILY_CHECK_IN in enabled) addAll(checkIn(config))
        if (ReminderKinds.HYDRATION in enabled) addAll(hydration(config))
        if (ReminderKinds.MOVEMENT in enabled) addAll(movement(config))
        if (ReminderKinds.WIND_DOWN in enabled) addAll(windDown(config))
    }

    fun forKind(kind: String, config: ReminderConfig): List<RepeatingReminder> = when (kind) {
        ReminderKinds.DAILY_CHECK_IN -> checkIn(config)
        ReminderKinds.HYDRATION -> hydration(config)
        ReminderKinds.MOVEMENT -> movement(config)
        ReminderKinds.WIND_DOWN -> windDown(config)
        else -> emptyList()
    }

    /** Every id a kind could occupy, so a cancel clears the whole set whatever the timing was. */
    fun allIds(kind: String): List<String> = when (kind) {
        ReminderKinds.DAILY_CHECK_IN -> listOf(ReminderIds.CHECK_IN)
        ReminderKinds.HYDRATION -> (0..23).map(ReminderIds::hydration)
        ReminderKinds.MOVEMENT -> (1..7).map { ReminderIds.movement(it) } + ReminderIds.movement(null)
        ReminderKinds.WIND_DOWN -> listOf(ReminderIds.WIND_DOWN)
        else -> emptyList()
    }

    private fun checkIn(c: ReminderConfig) = listOf(
        RepeatingReminder(ReminderIds.CHECK_IN, ReminderKinds.DAILY_CHECK_IN, ReminderCopy.CHECK_IN_TITLE, ReminderCopy.CHECK_IN_BODY, c.checkInHour, c.checkInMinute),
    )

    /** Across the waking window only, so it goes quiet overnight. */
    private fun hydration(c: ReminderConfig): List<RepeatingReminder> {
        val step = maxOf(c.hydrationIntervalHours, 1)
        return (c.hydrationStartHour..c.hydrationEndHour step step).map { h ->
            RepeatingReminder(ReminderIds.hydration(h), ReminderKinds.HYDRATION, ReminderCopy.HYDRATION_TITLE, ReminderCopy.HYDRATION_BODY, h, 0)
        }
    }

    /** Monday to Friday by default, or every day. */
    private fun movement(c: ReminderConfig): List<RepeatingReminder> =
        if (c.movementWeekdaysOnly) {
            (2..6).map { d ->
                RepeatingReminder(ReminderIds.movement(d), ReminderKinds.MOVEMENT, ReminderCopy.MOVEMENT_TITLE, ReminderCopy.MOVEMENT_BODY, c.movementHour, c.movementMinute, d)
            }
        } else {
            listOf(RepeatingReminder(ReminderIds.movement(null), ReminderKinds.MOVEMENT, ReminderCopy.MOVEMENT_TITLE, ReminderCopy.MOVEMENT_BODY, c.movementHour, c.movementMinute))
        }

    private fun windDown(c: ReminderConfig) = listOf(
        RepeatingReminder(ReminderIds.WIND_DOWN, ReminderKinds.WIND_DOWN, ReminderCopy.WIND_DOWN_TITLE, ReminderCopy.WIND_DOWN_BODY, c.windDownHour, c.windDownMinute),
    )
}

/** A dated medication reminder, ready for the alarm layer. */
data class MedicationReminder(
    val id: String,
    val medicationId: String,
    val slotId: String,
    val dayKey: String,
    val day: LocalDate,
    val fireAt: ZonedDateTime,
    val title: String,
    val body: String,
    val autoLog: Boolean,
)

object MedicationReminders {
    /**
     * A medicine's reminders (iOS `rescheduleMedication`): its planned occurrences, each with
     * its iOS id, wording and fire time in her zone.
     */
    fun plan(
        medicationId: String,
        name: String,
        schedule: DoseSchedule,
        autoLog: Boolean,
        showName: Boolean,
        cycleHorizon: Int,
        loggedTodayWholeDay: Boolean,
        loggedTodaySlots: Set<String>,
        now: ZonedDateTime,
    ): List<MedicationReminder> {
        val (title, body) = ReminderPlanner.medicationReminderText(name, showName, autoLog)
        return ReminderPlanner.plannedOccurrences(schedule, cycleHorizon, loggedTodayWholeDay, loggedTodaySlots, now).map { o ->
            MedicationReminder(
                id = ReminderIds.medication(medicationId, o.slotId, o.dayKey),
                medicationId = medicationId.uppercase(),
                slotId = o.slotId,
                dayKey = o.dayKey,
                day = o.day,
                fireAt = o.day.atTime(o.hour, o.minute).atZone(now.zone),
                title = title,
                body = body,
                autoLog = autoLog,
            )
        }
    }

    /** The day a reminder's dose belongs to, from its key (the occurrence's day, not delivery: D10). */
    fun dayOf(dayKey: String): LocalDate? = runCatching {
        LocalDate.of(dayKey.substring(0, 4).toInt(), dayKey.substring(4, 6).toInt(), dayKey.substring(6, 8).toInt())
    }.getOrNull()
}

/** A reminder she can turn on, as the iOS Reminders screen lists it (`ReminderCatalog`). */
data class ReminderDef(val id: String, val label: String, val desc: String)

object ReminderCatalog {
    val all = listOf(
        ReminderDef(ReminderKinds.DAILY_CHECK_IN, "Daily check-in", "A gentle nudge to log how you're feeling"),
        ReminderDef(ReminderKinds.MEDICATION, "Medication reminders", "On the days each one is due"),
        ReminderDef(ReminderKinds.HYDRATION, "Hydration", "Stay on top of water through the day"),
        ReminderDef(ReminderKinds.MOVEMENT, "Movement", "A little daily activity"),
        ReminderDef(ReminderKinds.WIND_DOWN, "Wind-down", "An end-of-day nudge, if you want one"),
    )

    fun def(id: String): ReminderDef? = all.firstOrNull { it.id == id }
}
