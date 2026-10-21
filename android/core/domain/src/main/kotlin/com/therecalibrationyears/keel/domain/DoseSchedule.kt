package com.therecalibrationyears.keel.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** iOS Calendar weekday for a day: 1 is Sunday through 7 is Saturday (plan §1.6). */
fun LocalDate.iosWeekday(): Int = dayOfWeek.value % 7 + 1

/**
 * One dose: the days it's taken, and optionally the time of day (port of iOS `DoseSlot`).
 * Hour and minute rather than an instant, so 8:00 am is 8:00 am wherever she is. Empty
 * weekdays count as every day, so an incomplete pattern never quietly hides an entry.
 */
data class DoseSlot(
    /** Upper-case UUID, as iOS writes it. */
    val id: String = UUID.randomUUID().toString().uppercase(),
    val weekdays: Set<Int> = ALL_DAYS,
    val hour: Int? = null,
    val minute: Int? = null,
) {
    val hasTime: Boolean get() = hour != null
    val isEveryDay: Boolean get() = weekdays.isEmpty() || weekdays.size == 7
    fun appliesTo(weekday: Int): Boolean = weekdays.isEmpty() || weekday in weekdays

    /** Sorts by clock time; timeless doses come last. */
    val minutesIntoDay: Int get() = hour?.let { it * 60 + (minute ?: 0) } ?: Int.MAX_VALUE

    companion object {
        val ALL_DAYS: Set<Int> = (1..7).toSet()
    }
}

/** Locale-dependent words a schedule summary needs (times and weekday names). */
interface ScheduleWords {
    /** "8:00 am", or "Any time" for a dose with no time (iOS `DoseSlot.label`). */
    fun time(slot: DoseSlot): String
    /** Short weekday name for an iOS weekday number, e.g. "Mon". */
    fun shortWeekday(weekday: Int): String
    /** The iOS weekday her week starts on (1 Sunday, 2 Monday). */
    val firstWeekday: Int
}

/**
 * When something is taken (port of iOS `DoseSchedule`): a weekly pattern, a repeating cycle
 * with a pause at the end (how cyclic HRT is written on a script), or "as directed".
 */
data class DoseSchedule(
    val kind: Kind = Kind.WEEKLY,
    val slots: List<DoseSlot> = listOf(DoseSlot()),
    /** The whole cycle in days, pause included. */
    val cycleLength: Int = 28,
    /** Days of pause at the end of each cycle. */
    val pauseDays: Int = 7,
    /** The day that counts as day 1 of the cycle. */
    val anchor: LocalDate? = null,
) {
    enum class Kind(val raw: String, val label: String) {
        WEEKLY("weekly", "Weekly"), CYCLE("cycle", "Cycle"), AS_NEEDED("asNeeded", "As directed");

        companion object {
            fun fromRaw(raw: String?): Kind? = entries.firstOrNull { it.raw == raw }
        }
    }

    val activeDays: Int get() = maxOf(cycleLength - pauseDays, 0)
    val sortedSlots: List<DoseSlot> get() = slots.sortedBy { it.minutesIntoDay }

    /** Every weekday any dose falls on. */
    val weekdays: Set<Int>
        get() = slots.flatMap { if (it.weekdays.isEmpty()) DoseSlot.ALL_DAYS else it.weekdays }.toSet().ifEmpty { DoseSlot.ALL_DAYS }

    /**
     * Whether it's due on a day. Anything without a usable pattern answers yes: better to
     * show an entry she can skip than to hide one she needed.
     */
    fun isDue(day: LocalDate): Boolean = when (kind) {
        Kind.AS_NEEDED -> true
        Kind.WEEKLY -> slots.isEmpty() || slots.any { it.appliesTo(day.iosWeekday()) }
        Kind.CYCLE -> if (cycleLength <= 0 || activeDays <= 0) true else cycleDay(day)?.let { it <= activeDays } ?: true
    }

    /** The doses due on a day, in clock order (a cycle's doses all fall on its active days). */
    fun dueSlots(day: LocalDate): List<DoseSlot> {
        if (!isDue(day)) return emptyList()
        if (kind != Kind.WEEKLY) return sortedSlots
        return sortedSlots.filter { it.appliesTo(day.iosWeekday()) }
    }

    /** 1-based day within the cycle (also before the anchor), or null when there's nothing to count. */
    fun cycleDay(day: LocalDate): Int? {
        if (kind != Kind.CYCLE || cycleLength <= 0) return null
        val diff = ChronoUnit.DAYS.between(anchor ?: day, day)
        return (((diff % cycleLength) + cycleLength) % cycleLength).toInt() + 1
    }

    /** The next day it's due after [day], within a bounded window. */
    fun nextDue(day: LocalDate, within: Int = 90): LocalDate? =
        (1..within).asSequence().map { day.plusDays(it.toLong()) }.firstOrNull(::isDue)

    /** Upcoming days a dose falls due, [from] included when it qualifies. */
    fun upcomingDueDates(slot: DoseSlot, from: LocalDate, count: Int): List<LocalDate> {
        if (count <= 0) return emptyList()
        val found = mutableListOf<LocalDate>()
        var cursor = from
        var remaining = count * maxOf(cycleLength, 7) + count // bounded: a never-due schedule can't spin
        while (found.size < count && remaining > 0) {
            if (isDue(cursor) && (kind == Kind.CYCLE || slot.appliesTo(cursor.iosWeekday()))) found += cursor
            cursor = cursor.plusDays(1)
            remaining--
        }
        return found
    }

    /** "Every day at 8:00 am and 8:00 pm", or "Every day at 8:00 am, Sat and Sun also at 10:00 am". */
    fun summary(words: ScheduleWords): String {
        val timesSuffix = sortedSlots.filter { it.hasTime }.map(words::time).let { if (it.isEmpty()) "" else " at ${list(it)}" }
        when (kind) {
            Kind.AS_NEEDED -> return "As directed" + timesSuffix
            Kind.CYCLE -> return "$activeDays days on, $pauseDays off" + timesSuffix
            Kind.WEEKLY -> Unit
        }
        val base = phrase(weekdays, words)
        val timed = sortedSlots.filter { it.hasTime }
        if (timed.isEmpty()) return base
        fun days(s: DoseSlot) = if (s.weekdays.isEmpty()) DoseSlot.ALL_DAYS else s.weekdays
        val covering = timed.filter { days(it) == weekdays }
        val narrower = timed.filter { days(it) != weekdays }
        var text = if (covering.isEmpty()) base else "$base at ${list(covering.map(words::time))}"
        narrower.forEach { text += ", ${phrase(days(it), words)} also at ${words.time(it)}" }
        return text
    }

    companion object {
        val everyDay = DoseSchedule()

        /** "Mon, Wed and Fri", collapsing the sets that have their own name. */
        fun phrase(days: Set<Int>, words: ScheduleWords): String = when {
            days.isEmpty() || days.size == 7 -> "Every day"
            days == setOf(2, 3, 4, 5, 6) -> "Weekdays"
            days == setOf(1, 7) -> "Weekends"
            else -> list(orderedWeekdays(words.firstWeekday).filter { it in days }.map(words::shortWeekday))
        }

        /** "a", "a and b", "a, b and c". */
        fun list(items: List<String>): String =
            if (items.size <= 1) items.firstOrNull().orEmpty() else items.dropLast(1).joinToString(", ") + " and " + items.last()

        /** Weekdays in her own week order, so the row starts where her calendar starts. */
        fun orderedWeekdays(firstWeekday: Int): List<Int> = (0..6).map { (firstWeekday - 1 + it) % 7 + 1 }
    }
}

/**
 * The stored dose list (`Medication.doseTimesJSON`), field for field with iOS:
 * `[{"id":"UPPER","weekdays":[1..7],"hour":8,"minute":0}]`. Read tolerantly: a missing id
 * gets a new one, missing weekdays mean every day, missing hour means no time.
 */
object DoseSlotsJson {
    fun encode(slots: List<DoseSlot>): String = buildJsonArray {
        slots.forEach { s ->
            add(
                buildJsonObject {
                    put("id", s.id)
                    put("weekdays", JsonArray(s.weekdays.sorted().map { JsonPrimitive(it) }))
                    s.hour?.let { put("hour", it) }
                    s.minute?.let { put("minute", it) }
                },
            )
        }
    }.toString()

    fun decode(json: String?): List<DoseSlot>? = runCatching {
        if (json.isNullOrBlank()) return null
        Json.parseToJsonElement(json).jsonArray.map { e ->
            val o: JsonObject = e.jsonObject
            DoseSlot(
                id = o["id"]?.jsonPrimitive?.content?.uppercase() ?: UUID.randomUUID().toString().uppercase(),
                weekdays = o["weekdays"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }?.toSet() ?: DoseSlot.ALL_DAYS,
                hour = o["hour"]?.jsonPrimitive?.intOrNull,
                minute = o["minute"]?.jsonPrimitive?.intOrNull,
            )
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
