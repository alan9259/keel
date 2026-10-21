package com.therecalibrationyears.keel.domain

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * "Looking back" (port of iOS `MonthSummary`): a fixed-wording summary of the current month,
 * built from her own check-ins. No generated text and no interpretation: how many days she
 * checked in, the symptoms she logged most often (with real day counts), and what she wrote.
 * `java.time` dates are always the Gregorian calendar, which iOS has to force.
 */
data class MonthSummary(
    /** The calendar month this covers, in her zone. */
    val month: YearMonth,
    /** Distinct days she checked in on this month. */
    val daysCheckedIn: Int,
    /** Most-logged symptoms this month, by distinct days, then name. At most [TOP_LIMIT]. */
    val topSymptoms: List<SymptomCount>,
    /** Her notes from this month, newest first. */
    val notes: List<Note>,
) {
    data class Entry(val id: String, val date: Instant, val symptoms: List<String>, val notes: String?)
    data class SymptomCount(val name: String, val days: Int)
    data class Note(val id: String, val date: Instant, val text: String)

    /** "October so far" (English month names, as the rest of Keel's copy). */
    val heading: String get() = "${month.month.getDisplayName(TextStyle.FULL_STANDALONE, EN_AU)} so far"

    val checkInLine: String
        get() = if (daysCheckedIn == 0) "No check-ins yet this month." else "You checked in on ${days(daysCheckedIn)} this month."

    companion object {
        const val TOP_LIMIT = 5
        /** How many notes Looking back shows before "See all notes". */
        const val NOTE_PREVIEW_LIMIT = 3
        private val EN_AU = Locale.forLanguageTag("en-AU")

        fun build(entries: List<Entry>, now: Instant, zone: ZoneId): MonthSummary {
            val month = YearMonth.from(now.atZone(zone))
            val inMonth = entries.filter { YearMonth.from(it.date.atZone(zone)) == month }
            val symptomDays = mutableMapOf<String, MutableSet<java.time.LocalDate>>()
            inMonth.forEach { e -> e.symptoms.forEach { symptomDays.getOrPut(it) { mutableSetOf() } += e.date.atZone(zone).toLocalDate() } }
            val top = symptomDays.map { (name, days) -> SymptomCount(name, days.size) }
                .sortedWith(compareByDescending<SymptomCount> { it.days }.thenBy { it.name })
                .take(TOP_LIMIT)
            return MonthSummary(
                month = month,
                daysCheckedIn = inMonth.map { it.date.atZone(zone).toLocalDate() }.toSet().size,
                topSymptoms = top,
                notes = notes(inMonth),
            )
        }

        /** Non-empty notes, trimmed, newest first. */
        fun notes(entries: List<Entry>): List<Note> = entries
            .mapNotNull { e -> e.notes?.trim()?.takeIf { it.isNotEmpty() }?.let { Note(e.id, e.date, it) } }
            .sortedByDescending { it.date }

        fun days(n: Int): String = if (n == 1) "1 day" else "$n days"
    }
}

/** A stored insight card (port of iOS `Insight`), derived from a [PatternFinding]. */
data class Insight(
    val title: String,
    val detail: String,
    val timeframe: String,
    /** The iOS SF Symbol name for the leading icon. */
    val iconKey: String,
    val accent: InsightAccent,
)
