package com.therecalibrationyears.keel.domain

import java.time.LocalDate

/**
 * Symptom "days reported" (port of iOS `SymptomTally`): distinct days per symptom, so the same
 * symptom on the same day from two sources counts once. Day-set based and pure.
 */
class SymptomTally {
    private val days = mutableMapOf<String, MutableSet<LocalDate>>()

    /** Distinct days each symptom was reported, keyed by its name. */
    val daysByName: Map<String, Set<LocalDate>> get() = days

    fun add(name: String, day: LocalDate) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        days.getOrPut(trimmed) { mutableSetOf() } += day
    }

    /** Most days first; ties by name, so the order is stable. */
    fun ranked(): List<Pair<String, Int>> =
        days.map { (name, d) -> name to d.size }.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

    /** Distinct days on which any of [names] was reported (a mixed day counts once). */
    fun days(anyOf: Set<String>): Int = unionDays(anyOf).size

    fun unionDays(anyOf: Set<String>): Set<LocalDate> = days.filterKeys { it in anyOf }.values.flatten().toSet()

    /** Distinct days hot flushes or night sweats were reported. */
    val vasomotorDays: Int get() = days(VASOMOTOR)

    companion object {
        /** The vasomotor symptoms clinicians weigh most. */
        val VASOMOTOR = setOf("Hot flushes", "Night sweats")

        /** The archived `symptom.*` type id for a symptom (lowercased, spaces to underscores). */
        fun healthTypeId(name: String): String = "symptom." + name.lowercase().replace(" ", "_")

        /** The display name for an archived `symptom.*` type id, or null for any other type. */
        fun name(healthTypeId: String): String? {
            if (!healthTypeId.startsWith("symptom.")) return null
            val raw = healthTypeId.removePrefix("symptom.").replace("_", " ")
            if (raw.isEmpty()) return null
            return raw.first().uppercase() + raw.drop(1)
        }
    }
}

/**
 * Stats counts are in days, never entries (port of iOS `StatsDayCounts`), so the "You checked
 * in on X of the last N days" sentence and the cards agree. "No symptoms logged" means nothing
 * she logged that day had a symptom: it describes the record, not her health.
 */
data class StatsDayCounts(val checkInDays: Int, val noSymptomDays: Int) {
    companion object {
        fun of(entries: List<Pair<LocalDate, Boolean>>): StatsDayCounts {
            val dayHasSymptom = mutableMapOf<LocalDate, Boolean>()
            entries.forEach { (day, has) -> dayHasSymptom[day] = (dayHasSymptom[day] ?: false) || has }
            return StatsDayCounts(dayHasSymptom.size, dayHasSymptom.values.count { !it })
        }
    }
}

/** The Stats screen's periods (port of iOS `ReportsView.Period`). */
enum class StatsPeriod(val label: String, val days: Int) { WEEK("Week", 7), MONTH("Month", 30), QUARTER("3 Months", 90) }
