package com.therecalibrationyears.keel.designsystem

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
import com.therecalibrationyears.keel.domain.DoseSlot
import com.therecalibrationyears.keel.domain.ScheduleWords

/**
 * Locale-aware date text, matching the iOS `.dateTime` formats: the same fields, in the
 * order and style her locale uses (e.g. "Tue 21 Oct" in Australia, "Tue, Oct 21" in the US).
 */
class KeelDateFormats(context: Context, private val zone: ZoneId, private val locale: Locale = Locale.getDefault()) : ScheduleWords {
    private fun skeleton(s: String) = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, s), locale)

    private val weekdayMonthDay = skeleton("EEEMMMd")
    private val fullWeekdayMonthDay = skeleton("EEEEMMMd")
    private val weekdayDay = skeleton("EEEd")
    private val monthYear = skeleton("MMMMy")
    private val fullDate = skeleton("EEEEMMMMd")
    private val dayMonthYear = skeleton("yMMMd")
    private val monthDay = skeleton("MMMd")
    private val weekdayMonthDayYear = skeleton("yEEEMMMd")
    /** Hours and minutes in her 12/24-hour setting, as iOS `.hour().minute()`. */
    private val time = skeleton(if (DateFormat.is24HourFormat(context)) "Hm" else "hm")
    private val dateAndTime = skeleton(if (DateFormat.is24HourFormat(context)) "yMMMdHm" else "yMMMdhm")

    val firstDayOfWeek: DayOfWeek = WeekFields.of(locale).firstDayOfWeek

    /** iOS `.weekday(.abbreviated).month(.abbreviated).day()`. */
    fun weekdayMonthDay(day: LocalDate): String = weekdayMonthDay.format(day)
    /** iOS `.weekday(.wide).month(.abbreviated).day()`. */
    fun fullWeekdayMonthDay(day: LocalDate): String = fullWeekdayMonthDay.format(day)
    fun weekdayDay(day: LocalDate): String = weekdayDay.format(day)
    fun monthYear(month: YearMonth): String = monthYear.format(month.atDay(1))
    fun fullDate(day: LocalDate): String = fullDate.format(day)
    fun time(at: Instant): String = time.format(at.atZone(zone))
    /** iOS `.dateTime.day().month(.abbreviated).year()`, e.g. "12 Oct 2026". */
    fun dayMonthYear(day: LocalDate): String = dayMonthYear.format(day)

    /** "22 Oct 2026, 10:45 am" (iOS `.formatted(date: .abbreviated, time: .shortened)`). */
    fun dateAndTime(at: Instant): String = dateAndTime.format(at.atZone(zone))

    /** "21 Oct" (iOS `.dateTime.month(.abbreviated).day()`). */
    fun monthDay(day: LocalDate): String = monthDay.format(day)

    /** "Wed 21 Oct 2025" (a note from another year). */
    fun weekdayMonthDayYear(day: LocalDate): String = weekdayMonthDayYear.format(day)

    /** A dose's time, or "Any time" when she hasn't set one (iOS `DoseSlot.label`). */
    override fun time(slot: DoseSlot): String {
        val hour = slot.hour ?: return "Any time"
        return time.format(java.time.LocalTime.of(hour, slot.minute ?: 0))
    }

    /** iOS `shortWeekdaySymbols`, by iOS weekday number (1 is Sunday). */
    override fun shortWeekday(weekday: Int): String = dayOfWeek(weekday).getDisplayName(TextStyle.SHORT, locale)

    /** iOS `weekdaySymbols`, for TalkBack. */
    fun fullWeekday(weekday: Int): String = dayOfWeek(weekday).getDisplayName(TextStyle.FULL_STANDALONE, locale)

    /** iOS `veryShortWeekdaySymbols`. */
    fun weekdayLetter(weekday: Int): String = letter(dayOfWeek(weekday))

    override val firstWeekday: Int get() = firstDayOfWeek.value % 7 + 1

    private fun dayOfWeek(weekday: Int): DayOfWeek = DayOfWeek.of((weekday + 5) % 7 + 1)

    /** The single-letter weekday, as iOS `veryShortStandaloneWeekdaySymbols`. */
    fun letter(day: DayOfWeek): String = day.getDisplayName(TextStyle.NARROW_STANDALONE, locale)
}
