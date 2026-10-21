package com.therecalibrationyears.keel.insights

import com.therecalibrationyears.keel.domain.ActivityAggregation
import com.therecalibrationyears.keel.domain.ActivityPeriod
import com.therecalibrationyears.keel.domain.DayAmount
import com.therecalibrationyears.keel.domain.EatingLog
import com.therecalibrationyears.keel.domain.EnergyLevel
import com.therecalibrationyears.keel.domain.MergedActivity
import com.therecalibrationyears.keel.domain.Mood
import com.therecalibrationyears.keel.domain.MoodPack
import com.therecalibrationyears.keel.domain.VitalTrend
import com.therecalibrationyears.keel.domain.parity.formatOneDecimal
import com.therecalibrationyears.keel.domain.parity.roundHalfAwayFromZero
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** A reading imported from Health Connect (iOS `HealthSample`): one type, one day. */
data class HealthReading(val typeId: String, val day: LocalDate, val value: Double)

/** How she felt on a day, from her check-in. */
data class DayFeeling(val day: LocalDate, val mood: Mood, val energy: EnergyLevel?)

/** What the Activities screen shows, read live by the app. */
data class ActivitiesData(
    /** Her own entries, any activity (sleep, water, eating answers). */
    val manual: List<DayAmount> = emptyList(),
    /** Imported daily amounts (steps, exercise, sleep). */
    val imported: List<DayAmount> = emptyList(),
    /** Imported readings (vitals, energy, distance, flights). */
    val readings: List<HealthReading> = emptyList(),
    /** Her check-ins, newest first. */
    val feelings: List<DayFeeling> = emptyList(),
    /** Health Connect items she has switched off. */
    val disabledItems: Set<String> = emptySet(),
    val healthConnected: Boolean = false,
    val moodPack: MoodPack? = null,
)

/** One metric tile (port of the iOS `Metric`). */
data class HealthMetric(
    val id: String,
    val label: String,
    /** The iOS SF Symbol name. */
    val symbol: String,
    val unit: String,
    val source: Source,
    /** One decimal (sleep hours) rather than whole numbers. */
    val decimal: Boolean,
    /** A running count sums over a week or month; a level averages. */
    val aggregate: ActivityAggregation.Mode,
    /** The Health Connect switch it belongs to, when not its own id. */
    val syncItemId: String? = null,
    /** Body readings show the value only, never an up or down arrow. */
    val isVital: Boolean = false,
) {
    enum class Source { ACTIVITY, READING }
}

/** Today against her own last week: direction only, never a judgement. */
enum class TrendArrow { UP, DOWN, SAME }

/**
 * The Activities screen's rules (port of iOS `ActivitiesView`'s derived values), pure so
 * they're unit-tested: the window, which tiles show, values with her own entries winning, the
 * gentle trend arrow, roll-ups, vitals, and the "You logged" counts.
 */
class ActivitiesModel(
    val data: ActivitiesData,
    val today: LocalDate,
    val period: ActivityPeriod,
    val anchor: LocalDate,
    val firstDay: DayOfWeek,
) {
    val selectedDay: LocalDate get() = anchor
    val visibleDays: List<LocalDate> = ActivityAggregation.days(ActivityAggregation.interval(period, anchor, firstDay))
    /** Days in the window that have happened, for "X of N days". */
    val elapsedDays: List<LocalDate> = visibleDays.filter { it <= today }
    val isCurrentPeriod: Boolean get() = ActivityAggregation.isCurrent(anchor, period, firstDay, today)

    fun value(metric: HealthMetric, day: LocalDate): Double? = when (metric.source) {
        // Her own entry wins; otherwise the imported value.
        HealthMetric.Source.ACTIVITY -> MergedActivity.amount(metric.id, day, data.manual, data.imported)
        HealthMetric.Source.READING -> data.readings.firstOrNull { it.typeId == metric.id && it.day == day }?.value
    }

    fun series(metric: HealthMetric): Map<LocalDate, Double> = when (metric.source) {
        HealthMetric.Source.ACTIVITY -> MergedActivity.byDay(metric.id, data.manual, data.imported)
        HealthMetric.Source.READING -> data.readings.filter { it.typeId == metric.id }.associate { it.day to it.value }
    }

    /** Everything she still imports, plus anything switched off that has data on screen. */
    val shownMetrics: List<HealthMetric>
        get() = METRICS.filter { m -> (m.syncItemId ?: m.id) !in data.disabledItems || visibleDays.any { value(m, it) != null } }

    /** Today against the last seven days, once there are three; within 10% reads as the same. */
    fun trend(metric: HealthMetric, value: Double?): TrendArrow? {
        if (value == null || metric.isVital) return null
        val start = selectedDay.minusDays(7)
        val past = when (metric.source) {
            HealthMetric.Source.ACTIVITY -> series(metric).filter { (day, v) -> day >= start && day < selectedDay && v > 0 }.values.toList()
            HealthMetric.Source.READING -> data.readings.filter { it.typeId == metric.id && it.day >= start && it.day < selectedDay }.map { it.value }
        }
        if (past.size < 3) return null
        val avg = past.average()
        if (avg <= 0) return null
        return when {
            value >= avg * 1.1 -> TrendArrow.UP
            value <= avg * 0.9 -> TrendArrow.DOWN
            else -> TrendArrow.SAME
        }
    }

    fun rollup(metric: HealthMetric): ActivityAggregation.Rollup = ActivityAggregation.rollup(series(metric), visibleDays, metric.aggregate)

    /** How she felt on the day shown (her latest check-in that day). */
    val feeling: DayFeeling? get() = data.feelings.firstOrNull { it.day == selectedDay }

    /** One imported vital over the last three weeks. */
    fun vitalTrend(typeId: String): VitalTrend =
        VitalTrend(data.readings.filter { it.typeId == typeId && it.day >= today.minusDays(VITAL_WINDOW.toLong()) }.map { VitalTrend.Point(it.day, it.value) })

    val restingHeartRate: VitalTrend get() = vitalTrend("restingHeartRate")
    /** RMSSD, the Health Connect measure (plan D13). */
    val heartRateVariability: VitalTrend get() = vitalTrend("hrvRmssd")
    val weight: VitalTrend get() = vitalTrend("bodyMass")
    val skinTemperature: VitalTrend get() = vitalTrend("wristTemperature")

    /** Weight shows from two readings, the rest from three. */
    val hasVitals: Boolean
        get() = restingHeartRate.count >= 3 || heartRateVariability.count >= 3 || weight.count >= 2 || skinTemperature.count >= 3

    /** Plain context, with a GP nudge only when her resting heart rate is climbing. */
    val bodyNote: String
        get() {
            var line = "Recent readings from Health Connect, shown as they were recorded."
            if (restingHeartRate.direction == VitalTrend.Direction.UP) {
                line += " If your resting heart rate keeps climbing, it's worth a mention to your GP."
            }
            return line
        }

    /** Days in the window she checked in, and logged water, of those that have happened. */
    val checkInDays: Int get() = data.feelings.map { it.day }.toSet().count { it in elapsedDays }
    val waterDays: Int get() = data.manual.filter { it.activityId == "water" && it.amount > 0 }.map { it.day }.toSet().count { it in elapsedDays }
    val elapsedCount: Int get() = maxOf(elapsedDays.size, 1)

    fun eatingState(itemId: String): Boolean? = EatingLog.state(itemId, selectedDay, data.manual)

    /** Her own amount for a counted activity on the day shown. */
    fun amount(activityId: String): Double = data.manual.firstOrNull { it.activityId == activityId && it.day == selectedDay }?.amount ?: 0.0

    /** "Today", "Yesterday", a day; "This week" or a range; "This month" or a month. */
    fun periodLabel(fullDay: (LocalDate) -> String, dayMonth: (LocalDate) -> String, monthYear: (YearMonth) -> String): String = when (period) {
        ActivityPeriod.DAY -> when (selectedDay) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> fullDay(selectedDay)
        }
        ActivityPeriod.WEEK -> if (isCurrentPeriod) "This week" else "${dayMonth(visibleDays.first())} to ${dayMonth(visibleDays.last())}"
        ActivityPeriod.MONTH -> if (isCurrentPeriod) "This month" else monthYear(YearMonth.from(anchor))
    }

    companion object {
        /** How far back the vitals read. */
        const val VITAL_WINDOW = 21

        /** The metrics Health Connect fills in (iOS `allHealthMetrics`, same ids and order). */
        val METRICS = listOf(
            HealthMetric("steps", "Steps", "figure.walk", "steps", HealthMetric.Source.ACTIVITY, false, ActivityAggregation.Mode.TOTAL),
            HealthMetric("exercise", "Exercise", "flame.fill", "min", HealthMetric.Source.ACTIVITY, false, ActivityAggregation.Mode.TOTAL),
            HealthMetric("activeEnergy", "Active energy", "bolt.fill", "kcal", HealthMetric.Source.READING, false, ActivityAggregation.Mode.TOTAL),
            HealthMetric("distance", "Distance", "figure.walk.motion", "km", HealthMetric.Source.READING, true, ActivityAggregation.Mode.TOTAL),
            HealthMetric("flights", "Flights", "stairs", "", HealthMetric.Source.READING, false, ActivityAggregation.Mode.TOTAL),
            HealthMetric("sleep", "Sleep", "moon.fill", "hrs", HealthMetric.Source.ACTIVITY, true, ActivityAggregation.Mode.AVERAGE),
            HealthMetric("heartRate", "Heart rate", "heart.fill", "bpm", HealthMetric.Source.READING, false, ActivityAggregation.Mode.AVERAGE, "heartVitals", isVital = true),
            HealthMetric("respiratoryRate", "Respiratory rate", "lungs.fill", "br/min", HealthMetric.Source.READING, true, ActivityAggregation.Mode.AVERAGE, "heartVitals", isVital = true),
            HealthMetric("oxygenSaturation", "Blood oxygen", "drop.circle.fill", "%", HealthMetric.Source.READING, false, ActivityAggregation.Mode.AVERAGE, "heartVitals", isVital = true),
            HealthMetric("bodyTemperature", "Body temperature", "thermometer.medium", "°C", HealthMetric.Source.READING, true, ActivityAggregation.Mode.AVERAGE, "bodyTemperature", isVital = true),
        )

        /** Whole numbers, one decimal where it has one, grouped from 1,000 (iOS `format`). */
        fun format(v: Double, decimal: Boolean, locale: Locale = Locale.getDefault()): String {
            if (decimal && v != roundHalfAwayFromZero(v)) return formatOneDecimal(v)
            if (v >= 1000) return NumberFormat.getNumberInstance(locale).format(v)
            return roundHalfAwayFromZero(v).toLong().toString()
        }
    }
}
