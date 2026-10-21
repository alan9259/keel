package com.therecalibrationyears.keel.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Day arithmetic for Home's strips and month calendar (ports of `DashboardView` helpers). */
object CalendarGrid {
    /** The last seven days, oldest first, ending today. */
    fun last7(today: LocalDate): List<LocalDate> = (6 downTo 0).map { today.minusDays(it.toLong()) }

    /** The week's days in display order, starting from the locale's first day. */
    fun weekOrder(firstDay: DayOfWeek): List<DayOfWeek> = (0L..6L).map { firstDay.plus(it) }

    /** A month as grid cells: null for the blanks before day 1, then each day. */
    fun monthCells(month: YearMonth, firstDay: DayOfWeek): List<LocalDate?> {
        val first = month.atDay(1)
        val leading = (first.dayOfWeek.value - firstDay.value + 7) % 7
        return List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    }
}
