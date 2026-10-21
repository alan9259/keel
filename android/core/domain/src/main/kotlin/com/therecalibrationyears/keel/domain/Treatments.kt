package com.therecalibrationyears.keel.domain

/** Delivery method (port of iOS `MedicationMethod`); raw values are stored. */
enum class MedicationMethod(val raw: String, val label: String) {
    PATCH("patch", "Patch"),
    GEL("gel", "Gel"),
    SPRAY("spray", "Spray"),
    TABLET("tablet", "Tablet (oral)"),
    CAPSULE("capsule", "Capsule"),
    VAGINAL("vaginal", "Vaginal cream, pessary or ring"),
    IUD("iud", "IUD or IUS"),
    CREAM("cream", "Cream (topical)"),
    OTHER("other", "Other");

    companion object {
        fun fromRaw(raw: String?): MedicationMethod? = entries.firstOrNull { it.raw == raw }
    }
}

/**
 * A prescribed treatment or something she added to her own stack (port of iOS
 * `TreatmentKind`). Only ever used to group the list, never to rank or judge.
 */
enum class TreatmentKind(val raw: String, val label: String, val shortLabel: String) {
    TREATMENT("treatment", "Treatments", "Prescriptions"),
    SUPPLEMENT("supplement", "Supplements", "Supplements");

    companion object {
        /** iOS falls back to supplement. */
        fun fromRaw(raw: String?): TreatmentKind = entries.firstOrNull { it.raw == raw } ?: SUPPLEMENT
    }
}

/** Unit for a dose (port of iOS `DoseUnit`). Nothing is ever pre-selected for her. */
enum class DoseUnit(val raw: String, val label: String) {
    MG("mg", "mg"), MCG("mcg", "mcg"), G("g", "g"), IU("iu", "IU"), ML("ml", "ml"),
    PUMPS("pumps", "pumps"), PERCENT("percent", "%");

    /** "400mg" reads as one word; "2000 IU" and "2 pumps" don't. Pumps singularise. */
    fun format(amount: Double): String {
        val number = if (amount == Math.rint(amount)) amount.toLong().toString() else amount.toString()
        return when (this) {
            IU -> "$number $label"
            PUMPS -> "$number pump${if (amount == 1.0) "" else "s"}"
            else -> "$number$label"
        }
    }

    companion object {
        fun fromRaw(raw: String?): DoseUnit? = entries.firstOrNull { it.raw == raw }
    }
}

/** When in the day she takes it. Superseded by schedules; read by the legacy migration (iOS `TimeOfDay`). */
enum class TimeOfDay(val raw: String, val label: String) {
    MORNING("morning", "Morning"), AFTERNOON("afternoon", "Afternoon"), EVENING("evening", "Evening");

    companion object {
        fun fromRaw(raw: String?): TimeOfDay? = entries.firstOrNull { it.raw == raw }
    }
}

/** How often she took something, before schedules existed (iOS `DoseFrequency`). Legacy only. */
enum class DoseFrequency(val raw: String, val label: String) {
    DAILY("daily", "Daily"),
    TWICE_WEEKLY("twiceWeekly", "Twice weekly"),
    WEEKLY("weekly", "Weekly"),
    CYCLIC("cyclic", "Cyclic (part of the month)"),
    AS_DIRECTED("asDirected", "As directed");

    val shortLabel: String get() = if (this == CYCLIC) "Cyclic" else label

    companion object {
        fun fromRaw(raw: String?): DoseFrequency? = entries.firstOrNull { it.raw == raw }
    }
}
