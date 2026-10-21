package com.therecalibrationyears.keel.home

import android.content.Context
import android.text.format.DateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Locale-aware date text, matching the iOS `.dateTime` formats: the same fields, in the
 * order and style her locale uses (e.g. "Tue 21 Oct" in Australia, "Tue, Oct 21" in the US).
 */
class HomeFormats(context: Context, private val locale: Locale = Locale.getDefault(), private val zone: ZoneId) {
    private fun skeleton(s: String) = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, s), locale)

    private val weekdayMonthDay = skeleton("EEEMMMd")
    private val weekdayDay = skeleton("EEEd")
    private val monthYear = skeleton("MMMMy")
    private val fullDate = skeleton("EEEEMMMMd")
    /** Hours and minutes in her 12/24-hour setting, as iOS `.hour().minute()`. */
    private val time = skeleton(if (DateFormat.is24HourFormat(context)) "Hm" else "hm")

    val firstDayOfWeek: DayOfWeek = WeekFields.of(locale).firstDayOfWeek

    fun weekdayMonthDay(day: LocalDate): String = weekdayMonthDay.format(day)
    fun weekdayDay(day: LocalDate): String = weekdayDay.format(day)
    fun monthYear(month: YearMonth): String = monthYear.format(month.atDay(1))
    fun fullDate(day: LocalDate): String = fullDate.format(day)
    fun time(at: Instant): String = time.format(at.atZone(zone))

    /** The single-letter weekday, as iOS `veryShortStandaloneWeekdaySymbols`. */
    fun letter(day: DayOfWeek): String = day.getDisplayName(TextStyle.NARROW_STANDALONE, locale)
}
