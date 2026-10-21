package com.therecalibrationyears.keel.domain

import java.time.LocalDate

/** One day's amount for one activity (sleep hours, steps...), from either source. */
data class DayAmount(val activityId: String, val day: LocalDate, val amount: Double)

/**
 * Port of iOS `MergedActivity`: her own entries (`ActivityLog`) united with imported ones
 * (Health Connect, kept in the separate health store). For any activity and day a value
 * she entered herself wins; otherwise the imported value. Amounts of 0 count as none.
 */
object MergedActivity {
    fun amount(activityId: String, day: LocalDate, manual: List<DayAmount>, imported: List<DayAmount>): Double? =
        manual.firstOrNull { it.activityId == activityId && it.day == day && it.amount > 0 }?.amount
            ?: imported.firstOrNull { it.activityId == activityId && it.day == day && it.amount > 0 }?.amount

    fun byDay(activityId: String, manual: List<DayAmount>, imported: List<DayAmount>): Map<LocalDate, Double> {
        val out = mutableMapOf<LocalDate, Double>()
        imported.filter { it.activityId == activityId && it.amount > 0 }.forEach { out[it.day] = it.amount }
        manual.filter { it.activityId == activityId && it.amount > 0 }.forEach { out[it.day] = it.amount } // hers wins
        return out
    }
}
