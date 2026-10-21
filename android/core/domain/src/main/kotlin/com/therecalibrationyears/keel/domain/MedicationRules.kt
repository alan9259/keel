package com.therecalibrationyears.keel.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A medicine's schedule as stored (iOS `Medication` schedule columns): kept in parts so
 * they map onto a row in any backend. [MedicationSchedules] turns them into the
 * [DoseSchedule] that knows the rules, and back.
 */
data class ScheduleColumns(
    val scheduleKindRaw: String? = null,
    /** Bit 0 is Sunday through bit 6 Saturday. */
    val weekdaysMask: Int = 0,
    val cycleLength: Int = 28,
    val cyclePauseDays: Int = 7,
    val cycleAnchor: Instant? = null,
    val doseTimesJSON: String? = null,
    /** The single time older entries carried, read only as a fallback. */
    val doseTime: Instant? = null,
) {
    /** False while nothing has been set, so old entries keep behaving as they did. */
    val hasSchedule: Boolean get() = scheduleKindRaw != null
}

object MedicationSchedules {
    fun weekdays(mask: Int): Set<Int> = (1..7).filter { mask and (1 shl (it - 1)) != 0 }.toSet()
    fun mask(weekdays: Set<Int>): Int = weekdays.fold(0) { acc, d -> acc or (1 shl (d - 1)) }

    /** The stored parts as a schedule; nothing set reads as "as directed" (iOS getter). */
    fun read(columns: ScheduleColumns, zone: ZoneId): DoseSchedule = DoseSchedule(
        kind = DoseSchedule.Kind.fromRaw(columns.scheduleKindRaw) ?: DoseSchedule.Kind.AS_NEEDED,
        slots = slots(columns, zone),
        cycleLength = columns.cycleLength,
        pauseDays = columns.cyclePauseDays,
        anchor = columns.cycleAnchor?.atZone(zone)?.toLocalDate(),
    )

    /**
     * The schedule as stored parts. The flat columns stay filled so anything reading them,
     * and any older build, still sees the days and the first time of day ([now] gives the
     * date that time is carried on, as iOS's `DoseSlot.date`).
     */
    fun write(schedule: DoseSchedule, zone: ZoneId, now: Instant): ScheduleColumns = ScheduleColumns(
        scheduleKindRaw = schedule.kind.raw,
        weekdaysMask = mask(schedule.weekdays),
        cycleLength = schedule.cycleLength,
        cyclePauseDays = schedule.pauseDays,
        cycleAnchor = schedule.anchor?.atStartOfDay(zone)?.toInstant(),
        doseTimesJSON = DoseSlotsJson.encode(schedule.slots),
        doseTime = schedule.sortedSlots.firstOrNull { it.hasTime }?.let {
            now.atZone(zone).toLocalDate().atTime(it.hour ?: 0, it.minute ?: 0).atZone(zone).toInstant()
        },
    )

    /** Decoded doses, rebuilt from the older columns when there's no list yet. */
    private fun slots(columns: ScheduleColumns, zone: ZoneId): List<DoseSlot> {
        val stored = weekdays(columns.weekdaysMask)
        val decoded = DoseSlotsJson.decode(columns.doseTimesJSON)
            ?: return listOf(rebuiltSlot(stored, columns.doseTime, zone))
        // Entries written before days moved onto the dose carried them on the schedule.
        if (stored.isEmpty() || stored.size >= 7) return decoded
        return decoded.map { if (it.isEveryDay) it.copy(weekdays = stored) else it }
    }

    private fun rebuiltSlot(days: Set<Int>, doseTime: Instant?, zone: ZoneId): DoseSlot {
        val weekdays = days.ifEmpty { DoseSlot.ALL_DAYS }
        val time = doseTime?.atZone(zone) ?: return DoseSlot(weekdays = weekdays)
        return DoseSlot(weekdays = weekdays, hour = time.hour, minute = time.minute)
    }

    /**
     * Carry an entry made before schedules existed onto one (iOS `migrateLegacySchedules`):
     * "Daily" is every day, twice weekly is Mon and Thu (hers to move), weekly is the day she
     * started, "Cyclic" is the standard 28 day cycle with a 7 day pause from the day she
     * started, and a day-part becomes a time she can move.
     */
    fun legacySchedule(frequencyRaw: String?, timeOfDayRaw: String?, startDay: LocalDate): DoseSchedule {
        var kind = DoseSchedule.Kind.WEEKLY
        var anchor: LocalDate? = null
        var days = DoseSlot.ALL_DAYS
        when (DoseFrequency.fromRaw(frequencyRaw)) {
            DoseFrequency.DAILY, null -> Unit
            DoseFrequency.TWICE_WEEKLY -> days = setOf(2, 5)
            DoseFrequency.WEEKLY -> days = setOf(startDay.iosWeekday())
            DoseFrequency.CYCLIC -> { kind = DoseSchedule.Kind.CYCLE; anchor = startDay }
            DoseFrequency.AS_DIRECTED -> kind = DoseSchedule.Kind.AS_NEEDED
        }
        val hour = when (TimeOfDay.fromRaw(timeOfDayRaw)) {
            TimeOfDay.MORNING -> 9
            TimeOfDay.AFTERNOON -> 13
            TimeOfDay.EVENING -> 20
            null -> null
        }
        return DoseSchedule(
            kind = kind,
            slots = listOf(DoseSlot(weekdays = days, hour = hour, minute = hour?.let { 0 })),
            anchor = anchor,
        )
    }

    /**
     * "Oral micronised progesterone (e.g. Prometrium)" to "Oral micronised progesterone".
     * Only a trailing "(e.g. ...)" is removed; anything else she typed is kept as is.
     */
    fun nameWithoutExample(name: String): String {
        val match = EXAMPLE.find(name) ?: return name
        val cleaned = name.removeRange(match.range)
        return cleaned.ifEmpty { name }
    }

    private val EXAMPLE = Regex("""\s*\(e\.g\.[^()]*\)\s*$""", RegexOption.IGNORE_CASE)

    /** "400mg · Every day at 8:00 am", skipping whatever she left blank (iOS `detailLine`). */
    fun detailLine(isCompounded: Boolean, dosage: String, scheduleSummary: String?, timing: String): String {
        val parts = mutableListOf<String>()
        if (isCompounded) parts += "Compounded" else if (dosage.isNotEmpty()) parts += dosage
        if (scheduleSummary != null) parts += scheduleSummary else if (timing.isNotEmpty()) parts += timing
        return parts.joinToString(" · ")
    }

    /**
     * Today's doses to log for an auto-logged medicine (iOS `autoLogTodaysDueDoses`): each
     * due dose whose time has arrived, by its slot id, never a whole-day log and never a
     * still-future dose. An untimed dose counts as soon as the day is due.
     */
    fun autoLogSlots(schedule: DoseSchedule, now: ZonedDateTime, alreadyTaken: (String) -> Boolean): List<String> {
        val nowMinutes = now.hour * 60 + now.minute
        return schedule.dueSlots(now.toLocalDate())
            .filter { !it.hasTime || it.minutesIntoDay <= nowMinutes }
            .map { it.id }
            .filterNot(alreadyTaken)
    }
}

/**
 * What she entered on the add or edit form (iOS `TreatmentDraft`). Every field beyond the
 * name is optional: a partial entry beats none. Dates are only ever ones she gave.
 */
data class TreatmentDraft(
    val name: String,
    val kind: TreatmentKind,
    val catalogGroupID: String? = null,
    val method: MedicationMethod? = null,
    /** Never pre-filled for her. */
    val doseAmount: Double? = null,
    val doseUnit: DoseUnit? = null,
    val schedule: DoseSchedule = DoseSchedule.everyDay,
    /** Free text from entries made before the structured fields existed. */
    val legacyDosage: String = "",
    val legacyTiming: String = "",
    val date: Instant? = null,
    val doseChangedAt: Instant? = null,
    val isActive: Boolean = true,
    val stoppedAt: Instant? = null,
    val note: String = "",
    val isOffLabel: Boolean = false,
    val isCompounded: Boolean = false,
    val autoLogDoses: Boolean = false,
) {
    /** "400mg", or whatever free text an older entry carried. */
    val doseText: String
        get() = if (doseAmount != null && doseUnit != null) doseUnit.format(doseAmount) else legacyDosage

    /** The first dose time, kept on the model so reports and the GP summary read one field. */
    fun timingText(words: ScheduleWords): String =
        schedule.sortedSlots.firstOrNull { it.hasTime }?.let(words::time) ?: legacyTiming

    /** Only a treatment carries a method, so a value can't linger after it's re-filed. */
    val effectiveMethod: MedicationMethod? get() = if (kind == TreatmentKind.TREATMENT) method else null
}
