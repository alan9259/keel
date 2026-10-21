package com.therecalibrationyears.keel.domain

import com.therecalibrationyears.keel.domain.parity.roundHalfAwayFromZero
import java.time.Instant

/**
 * Port of iOS `EnergyLevel`: a five-point scale she taps, stored on the check-in as a
 * percentage (level × 20); 0 means she didn't pick one.
 */
enum class EnergyLevel(val level: Int, val label: String) {
    DRAINED(1, "Drained"), LOW(2, "Low"), OKAY(3, "Okay"), GOOD(4, "Good"), CHARGED(5, "Charged");

    val percent: Int get() = level * 20

    companion object {
        /** Stored when she saved a check-in without picking an energy level. */
        const val NOT_RECORDED = 0

        /** Nearest level for a stored percentage (halves round up, as iOS `.rounded()`). */
        fun fromPercent(percent: Int): EnergyLevel {
            val level = roundHalfAwayFromZero(percent / 20.0).toInt().coerceIn(1, 5)
            return entries.first { it.level == level }
        }

        /** The level she picked, or null when she didn't pick one (iOS `CheckIn.energyLevel`). */
        fun picked(storedPercent: Int): EnergyLevel? =
            if (storedPercent > NOT_RECORDED) fromPercent(storedPercent) else null
    }
}

/** Port of iOS `SymptomSeverity`: one tap mild, two moderate, three severe, a fourth clears. */
enum class SymptomSeverity(val level: Int, val label: String) {
    MILD(1, "Mild"), MODERATE(2, "Moderate"), SEVERE(3, "Severe");

    companion object {
        fun of(level: Int): SymptomSeverity? = entries.firstOrNull { it.level == level }

        /** The level after another tap (0 = unselected): 0 → 1 → 2 → 3 → 0. */
        fun nextLevel(after: Int): Int = if (after >= 3) 0 else after + 1
    }
}

/** The Home Energy card's summary (port of iOS `DashboardView.energyTrailing` and friends). */
object EnergySummary {
    data class Pick(val at: Instant, val level: EnergyLevel)

    /**
     * The level she picked most often, shown only once there are three or more entries that
     * week (fewer is too little to sum up); otherwise just the count.
     */
    fun trailing(picks: List<Pick>): String = when (picks.size) {
        0 -> "No data yet"
        1 -> "1 entry this week"
        2 -> "2 entries this week"
        else -> mostFrequent(picks)?.label ?: "No data yet"
    }

    /**
     * The level she picked most often; a tie goes to whichever tied level she picked most
     * recently. Never an average, so it is always a level she actually chose.
     */
    fun mostFrequent(picks: List<Pick>): EnergyLevel? {
        val count = picks.groupingBy { it.level }.eachCount()
        val latest = picks.groupBy { it.level }.mapValues { (_, p) -> p.maxOf { it.at } }
        return count.keys.maxWithOrNull(compareBy<EnergyLevel> { count.getValue(it) }.thenBy { latest.getValue(it) })
    }
}
