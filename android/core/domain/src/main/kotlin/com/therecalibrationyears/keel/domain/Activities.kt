package com.therecalibrationyears.keel.domain

import com.therecalibrationyears.keel.domain.parity.roundHalfAwayFromZero
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** The Day / Week / Month view on the Activities screen (port of iOS `ActivityPeriod`). */
enum class ActivityPeriod(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month") }

/**
 * Date windows and roll-ups for the Activities period views (port of iOS
 * `ActivityAggregation`), pure so the window maths and totals are unit-tested. Weeks start on
 * her locale's first day, as iOS's `Calendar.current`.
 */
object ActivityAggregation {
    /** The days a period covers, anchored on [date]. */
    fun interval(period: ActivityPeriod, date: LocalDate, firstDay: DayOfWeek): ClosedRange<LocalDate> = when (period) {
        ActivityPeriod.DAY -> date..date
        ActivityPeriod.WEEK -> {
            val start = date.with(TemporalAdjusters.previousOrSame(firstDay))
            start..start.plusDays(6)
        }
        ActivityPeriod.MONTH -> YearMonth.from(date).let { it.atDay(1)..it.atEndOfMonth() }
    }

    /** Each day in a window, in order. */
    fun days(range: ClosedRange<LocalDate>): List<LocalDate> =
        generateSequence(range.start) { it.plusDays(1) }.takeWhile { it <= range.endInclusive }.toList()

    /** One period earlier or later, never into a period after the one containing [notAfter]. */
    fun shift(anchor: LocalDate, delta: Int, period: ActivityPeriod, firstDay: DayOfWeek, notAfter: LocalDate): LocalDate {
        val moved = when (period) {
            ActivityPeriod.DAY -> anchor.plusDays(delta.toLong())
            ActivityPeriod.WEEK -> anchor.plusWeeks(delta.toLong())
            ActivityPeriod.MONTH -> anchor.plusMonths(delta.toLong())
        }
        return if (interval(period, moved, firstDay).start > interval(period, notAfter, firstDay).start) anchor else moved
    }

    /** Whether [anchor]'s period is the one containing [today] ("next" is then disabled). */
    fun isCurrent(anchor: LocalDate, period: ActivityPeriod, firstDay: DayOfWeek, today: LocalDate): Boolean =
        interval(period, anchor, firstDay).start == interval(period, today, firstDay).start

    enum class Mode { TOTAL, AVERAGE }

    data class Rollup(
        /** Null when no day in range had data. */
        val summary: Double?,
        /** One value per day (0 where a day had none), for the bar chart. */
        val perDay: List<Double>,
        val daysWithData: Int,
    )

    /** A total sums the days with data; an average is the mean of those days only. */
    fun rollup(byDay: Map<LocalDate, Double>, days: List<LocalDate>, mode: Mode): Rollup {
        val values = days.map { byDay[it] }
        val present = values.filterNotNull()
        val summary = when {
            present.isEmpty() -> null
            mode == Mode.TOTAL -> present.sum()
            else -> present.sum() / present.size
        }
        return Rollup(summary, values.map { it ?: 0.0 }, present.size)
    }
}

/** A lifestyle activity she can log daily (port of iOS `ActivityDef`). */
data class ActivityDef(
    val id: String,
    /** The iOS SF Symbol name. */
    val symbol: String,
    val label: String,
    /** Unit for a counted activity (e.g. "glasses"); null for a done toggle. */
    val unit: String?,
    val step: Double,
    val goal: Double?,
)

/** Port of iOS `ActivityCatalog` (ids are the stored `activityID`s). */
object ActivityCatalog {
    val all = listOf(
        ActivityDef("steps", "figure.walk", "Steps / Walk", null, 1.0, null),
        ActivityDef("exercise", "figure.strengthtraining.traditional", "Exercise", null, 1.0, null),
        ActivityDef("water", "drop.fill", "Water intake", "glasses", 1.0, 8.0),
        ActivityDef("sleep", "moon.fill", "Sleep", "hrs", 0.5, 8.0),
        ActivityDef("meditation", "wind", "Meditation / Breathwork", null, 1.0, null),
        ActivityDef("eating", "fork.knife", "Healthy eating", null, 1.0, null),
    )

    fun def(id: String): ActivityDef? = all.firstOrNull { it.id == id }
}

/** One thing she can note about her eating, as a plain yes or no (port of iOS `EatingItem`). */
data class EatingItem(val id: String, val label: String)

/** Port of iOS `EatingCatalog`. Nothing is scored, counted or labelled good or bad. */
object EatingCatalog {
    val nourishment = listOf(
        EatingItem("eat.protein", "Protein"),
        EatingItem("eat.veg", "Fruit & veg"),
        EatingItem("eat.calcium", "Calcium-rich food"),
        EatingItem("eat.wholegrains", "Wholegrains & fibre"),
    )

    /** Things that can nudge hot flushes, sleep and palpitations for some women ("notice", never "avoid"). */
    val triggers = listOf(
        EatingItem("eat.alcohol", "Alcohol"),
        EatingItem("eat.caffeine", "Caffeine"),
        EatingItem("eat.spicy", "Spicy food"),
    )

    val all: List<EatingItem> get() = nourishment + triggers
}

/**
 * Tri-state eating answers over the activity log (port of iOS `EatingLog`): yes is a row
 * of 1, no a row of 0 (an explicit "didn't"), and not answered is no row at all, so a day she
 * never answered is never counted as a clean no.
 */
object EatingLog {
    fun state(id: String, day: LocalDate, logs: List<DayAmount>): Boolean? =
        logs.firstOrNull { it.activityId == id && it.day == day }?.let { it.amount > 0.5 }
}

/**
 * A read of one imported vital over recent days (port of iOS `VitalTrend`): a recent average,
 * a direction, and the values for a chart. Every number is her own data.
 */
data class VitalTrend(val points: List<Point>) {
    data class Point(val day: LocalDate, val value: Double)

    /** Values in day order, for the chart. */
    val values: List<Double> get() = points.sortedBy { it.day }.map { it.value }
    val count: Int get() = points.size

    /** Rounded recent average (half away from zero, as Swift), or null when there's nothing. */
    val average: Int? get() = if (points.isEmpty()) null else roundHalfAwayFromZero(points.sumOf { it.value } / points.size).toInt()

    enum class Direction { UP, STEADY, DOWN }

    /** Recent half vs earlier half, only with six or more days and a shift of 5% or more. */
    val direction: Direction?
        get() {
            val v = values
            if (v.size < 6) return null
            val half = v.size / 2
            val earlier = v.take(half).average()
            val recent = v.drop(half).average()
            if (earlier <= 0) return null
            return when {
                recent >= earlier * 1.05 -> Direction.UP
                recent <= earlier * 0.95 -> Direction.DOWN
                else -> Direction.STEADY
            }
        }
}
