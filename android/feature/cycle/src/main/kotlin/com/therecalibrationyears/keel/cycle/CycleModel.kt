package com.therecalibrationyears.keel.cycle

import com.therecalibrationyears.keel.domain.CalendarGrid
import com.therecalibrationyears.keel.domain.CycleDay
import com.therecalibrationyears.keel.domain.CycleDayMark
import com.therecalibrationyears.keel.domain.CycleEntryType
import com.therecalibrationyears.keel.domain.CyclePhase
import com.therecalibrationyears.keel.domain.CycleStats
import com.therecalibrationyears.keel.domain.CycleTimeline
import com.therecalibrationyears.keel.domain.FlowLevel
import com.therecalibrationyears.keel.domain.PatternFinding
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** What the Cycle screen shows, read live by the app. */
data class CycleData(
    /** Her own logged days (type and level). */
    val days: List<Pair<LocalDate, CycleDay>> = emptyList(),
    /** Imported period days: shown, labelled, never used for an estimate. */
    val imported: Map<LocalDate, FlowLevel> = emptyMap(),
    /** A real cycle-length observation from the pattern engine, or none. */
    val finding: PatternFinding? = null,
)

/**
 * The Cycle screen's rules (port of `CycleTrackingView`'s derived values), pure so they're
 * unit-tested: the stats over the last 400 days, the timeline's days, the marks for what's on
 * screen (her own days winning over imported ones), and the status and history lines.
 */
class CycleModel(val data: CycleData, val today: LocalDate, val month: YearMonth) {
    val stats: CycleStats = CycleStats.fromEntries(data.days.filter { (day, _) -> day >= today.minusDays(400) && day <= today })
    val estimate: ClosedRange<LocalDate>? = stats.estimatedWindow()
    val railDays: List<LocalDate> = CycleTimeline.days(CycleTimeline.railBounds(stats.lastStart, estimate?.endInclusive, today))
    val hasAnyPeriod: Boolean get() = stats.lastStart != null
    val phase: CyclePhase get() = stats.phase(today)

    /** The timeline and the month shown, a day either side (iOS `visibleRange`). */
    private val visible: ClosedRange<LocalDate> =
        minOf(railDays.first(), month.atDay(1)).minusDays(1)..maxOf(railDays.last(), month.plusMonths(1).atDay(1))

    /** Period marks for the days on screen: hers, plus imported ones where she hasn't logged. */
    val marks: Map<LocalDate, CycleDayMark> = CycleDayMark.merge(
        manual = data.days.filter { (day, d) -> day in visible && d.type != CycleEntryType.PERIOD_END }.associate { (day, d) -> day to d.level },
        imported = data.imported.filterKeys { it in visible },
    )
    val hasAnyImported: Boolean get() = marks.values.any { it.imported }

    fun inEstimate(day: LocalDate): Boolean = estimate?.contains(day) == true && day > today

    /** "Day 12 of your cycle." and, when there's a pattern, the estimate. */
    fun statusLine(monthDay: (LocalDate) -> String): String {
        val dayN = stats.cycleDay(today) ?: return "Log a period to start your cycle."
        var line = "Day $dayN of your cycle."
        val window = estimate
        if (window != null) {
            line += " Next period likely ${monthDay(window.start)} to ${monthDay(window.endInclusive)}."
        } else if (stats.typicalRange != null) {
            line += " Your recent cycles have varied, so no estimate yet."
        }
        return line
    }

    /** Her typical range in a sentence, once there are two cycles. */
    val historyLine: String?
        get() {
            val range = stats.typicalRange ?: return null
            return if (range.first == range.last) {
                "About ${range.first} days between periods."
            } else {
                "Lately your cycles have run ${range.first} to ${range.last} days."
            }
        }

    /** Recent cycle lengths, newest first. */
    val recentLengths: List<Int> get() = stats.recentLengths.reversed()

    /** The month grid can't go past this month. */
    val isCurrentOrFutureMonth: Boolean get() = month >= YearMonth.from(today)

    fun monthCells(firstDay: DayOfWeek): List<LocalDate?> = CalendarGrid.monthCells(month, firstDay)

    /** What TalkBack reads for a day ([date] is the day in words). */
    fun accessibilityLabel(day: LocalDate, date: String, inEstimate: Boolean): String {
        val mark = marks[day]
        if (mark != null) {
            val source = if (mark.imported) ", from Health Connect" else ""
            return "$date, ${mark.level.label}$source"
        }
        if (day == today) return "$date, today"
        if (inEstimate) return "$date, estimated period"
        return date
    }

    companion object {
        /** Her own days fill by heaviness; imported ones are lighter (and outlined on screen). */
        fun fillOpacity(mark: CycleDayMark): Float = if (mark.imported) fillOpacity(mark.level) * 0.35f else fillOpacity(mark.level)

        fun fillOpacity(level: FlowLevel): Float = when (level) {
            FlowLevel.SPOTTING -> 0.38f
            FlowLevel.LIGHT -> 0.6f
            FlowLevel.MEDIUM, FlowLevel.UNSPECIFIED -> 0.9f
            FlowLevel.HEAVY -> 1f
        }

        /** Dark enough that the day number goes on-fill. */
        fun isDarkFill(mark: CycleDayMark): Boolean = fillOpacity(mark) >= 0.7f
    }
}
