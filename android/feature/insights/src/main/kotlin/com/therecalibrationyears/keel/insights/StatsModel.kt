package com.therecalibrationyears.keel.insights

import com.therecalibrationyears.keel.domain.DayAmount
import com.therecalibrationyears.keel.domain.EnergyLevel
import com.therecalibrationyears.keel.domain.MergedActivity
import com.therecalibrationyears.keel.domain.Mood
import com.therecalibrationyears.keel.domain.StatsDayCounts
import com.therecalibrationyears.keel.domain.StatsPeriod
import com.therecalibrationyears.keel.domain.SymptomTally
import com.therecalibrationyears.keel.domain.VitalTrend
import com.therecalibrationyears.keel.domain.parity.formatOneDecimal
import com.therecalibrationyears.keel.domain.parity.roundHalfAwayFromZero
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/** One check-in as Stats reads it. */
data class StatsEntry(val day: LocalDate, val mood: Mood, val energy: EnergyLevel?, val storedEnergy: Int, val symptoms: List<String>)

/** What the Stats screen shows, read live by the app. */
data class StatsData(
    val entries: List<StatsEntry> = emptyList(),
    /** Medicines she is taking (live, not stopped). */
    val activeMedicineCount: Int = 0,
    val anyAutoLogged: Boolean = false,
    /** Days with a medicine recorded as taken. */
    val takenDays: List<LocalDate> = emptyList(),
    /** Her own sleep entries and imported nights. */
    val manualSleep: List<DayAmount> = emptyList(),
    val importedSleep: List<DayAmount> = emptyList(),
    /** Imported readings (vitals). */
    val readings: List<HealthReading> = emptyList(),
)

/**
 * The Stats screen's numbers and sentences (port of iOS `ReportsView`), pure so they're
 * unit-tested. Everything counts days, never entries; every figure is her own record.
 */
class StatsModel(val data: StatsData, val today: LocalDate, val period: StatsPeriod, private val locale: Locale = Locale.getDefault()) {
    /** The first day of the window ("the last N days", today included). */
    val since: LocalDate = today.minusDays((period.days - 1).toLong())
    val window: List<StatsEntry> = data.entries.filter { it.day >= since }
    val dayCounts: StatsDayCounts = StatsDayCounts.of(window.map { it.day to it.symptoms.isNotEmpty() })
    /** "week", "month", "3 months", for sentences. */
    private val periodWord = period.label.lowercase()

    val checkInLine: String get() = "You checked in on ${dayCounts.checkInDays} of the last ${period.days} days."

    // Energy in her own words, never a percentage; only entries where she picked one.
    private val energyEntries = window.filter { it.energy != null }
    val averageEnergy: String
        get() = if (energyEntries.isEmpty()) "None yet" else EnergyLevel.fromPercent(energyEntries.sumOf { it.storedEnergy } / energyEntries.size).label

    /** Distinct days in the window with any medicine recorded as taken. */
    val medicineDays: Int get() = data.takenDays.filter { it >= since }.toSet().size

    /** The four tiles: label, value, caption. */
    val tiles: List<Triple<String, String, String>>
        get() = listOf(
            Triple("Check-ins", "${dayCounts.checkInDays}", "of ${period.days} days"),
            Triple("Avg energy", averageEnergy, "across entries"),
            Triple("No symptoms logged", "${dayCounts.noSymptomDays}", "of ${dayCounts.checkInDays} days logged"),
            Triple("Medicines", "$medicineDays", "taken of ${period.days} days"),
        )

    /** Share of entries for each mood, in the iOS order (difficult to great). */
    fun moodFraction(mood: Mood): Double = if (window.isEmpty()) 0.0 else window.count { it.mood == mood }.toDouble() / window.size
    fun moodLabel(mood: Mood): String = "${mood.label} ${(moodFraction(mood) * 100).toInt()}%"

    /** Symptom days from her check-ins (and any archived `symptom.*` readings), most days first. */
    val topSymptoms: List<Pair<String, Int>>
        get() = tally.ranked()

    private val tally: SymptomTally
        get() = SymptomTally().apply {
            window.forEach { e -> e.symptoms.forEach { add(it, e.day) } }
            data.readings.filter { it.day >= since }.forEach { r -> SymptomTally.name(r.typeId)?.let { add(it, r.day) } }
        }

    /** Ten symptoms a page; more swipe across. */
    val symptomPages: List<List<Pair<String, Int>>> get() = topSymptoms.chunked(SYMPTOMS_PER_PAGE)

    fun symptomDays(n: Int): String = "$n ${if (n == 1) "day" else "days"}"

    /** Hot flushes or night sweats, the frequency that most drives a treatment conversation. */
    val vasomotorLine: String?
        get() {
            val days = tally.vasomotorDays
            if (days <= 0) return null
            val perWeek = days * 7.0 / period.days
            val rate = if (perWeek >= 1) "about ${NumberFormat.getIntegerInstance(locale).format(roundHalfAwayFromZero(perWeek))} a week" else "less than one a week"
            return "Hot flushes or night sweats on $days of the last ${period.days} days ($rate)."
        }

    /** "Recorded as taken", since auto-log can mark a dose without her confirming it. */
    val medicinesLine: String
        get() {
            val suffix = if (data.anyAutoLogged) ", including days auto-logged" else ""
            return "Recorded as taken on $medicineDays of ${period.days} days$suffix."
        }
    val medicinesCountLine: String get() = "${data.activeMedicineCount} tracked over the $periodWord."

    /** The nights the sleep chart covers (up to two weeks), oldest first. */
    val sleepDays: List<LocalDate> = (minOf(period.days, 14) - 1 downTo 0).map { today.minusDays(it.toLong()) }
    val sleepSeries: List<Double> = sleepDays.map { MergedActivity.amount("sleep", it, data.manualSleep, data.importedSleep) ?: 0.0 }
    val hasSleep: Boolean get() = sleepSeries.any { it > 0 }
    val sleepAverage: Double get() = sleepSeries.filter { it > 0 }.let { if (it.isEmpty()) 0.0 else it.average() }

    /** "7.2h" (iOS `%.1fh`). */
    fun hours(h: Double): String = "${formatOneDecimal(h)}h"

    /** The tapped night, or a hint until she taps one. */
    fun sleepDetail(selected: Int?, dayWords: (LocalDate) -> String): String {
        if (selected == null || selected !in sleepDays.indices || sleepSeries[selected] <= 0) return "Tap a bar for that night's hours."
        val day = sleepDays[selected]
        val label = if (day == today) "Last night" else dayWords(day)
        return "$label: ${hours(sleepSeries[selected])}"
    }

    private fun vitalTrend(typeId: String) = VitalTrend(data.readings.filter { it.typeId == typeId && it.day >= since }.map { VitalTrend.Point(it.day, it.value) })
    val restingHeartRate: VitalTrend get() = vitalTrend("restingHeartRate")
    /** RMSSD, the Health Connect measure (plan D13). */
    val heartRateVariability: VitalTrend get() = vitalTrend("hrvRmssd")
    val weight: VitalTrend get() = vitalTrend("bodyMass")

    /** "122/78" once she has a couple of cuff readings. */
    val bloodPressure: String?
        get() {
            val sys = vitalTrend("bloodPressureSystolic")
            val dia = vitalTrend("bloodPressureDiastolic")
            if (sys.count < 2 || dia.count < 2) return null
            return "${sys.average}/${dia.average}"
        }

    val hasVitals: Boolean
        get() = restingHeartRate.count >= 3 || heartRateVariability.count >= 3 || weight.count >= 2 || bloodPressure != null

    /** "58 to 70", or null with a single value. */
    fun range(trend: VitalTrend): String? {
        val lo = trend.values.minOrNull() ?: return null
        val hi = trend.values.maxOrNull() ?: return null
        if (lo == hi) return null
        return "${roundHalfAwayFromZero(lo).toLong()} to ${roundHalfAwayFromZero(hi).toLong()}"
    }

    val vitalsFooter: String get() = "Averages and ranges over the $periodWord, from Health Connect. Keel shows these as they were recorded."
    val vitalsEmpty: String
        get() = "No vitals from Health Connect in this $periodWord yet. Resting heart rate, heart rate variability, weight and blood pressure appear here once there's data."

    companion object {
        const val SYMPTOMS_PER_PAGE = 10
    }
}
