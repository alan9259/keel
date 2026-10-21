package com.therecalibrationyears.keel.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Small rules of the check-in screen (ports from iOS `CheckInModal`). */
object CheckInRules {
    /**
     * A new entry is stamped with the time of day now, on the day being logged: the day
     * selector hands over a day, and without the time every entry that day would read as
     * the same moment and lose its order.
     */
    fun entryTimestamp(day: LocalDate, now: Instant, zone: ZoneId): Instant =
        day.atTime(now.atZone(zone).toLocalTime()).atZone(zone).toInstant()

    /** "Add hours" starts here. */
    const val SLEEP_START_HOURS = 7.5

    /** Sleep steps by half an hour, between half an hour and fourteen. */
    fun adjustSleep(current: Double?, delta: Double): Double = ((current ?: SLEEP_START_HOURS) + delta).coerceIn(0.5, 14.0)

    /** "7.5 hrs", or "8 hrs" for a whole number (iOS `sleepLabel`). */
    fun sleepLabel(hours: Double): String =
        "${if (hours % 1.0 == 0.0) hours.toInt().toString() else hours.toString()} hrs"

    /** Alcohol never goes below zero; zero is a real answer. */
    fun adjustAlcohol(current: Int?, delta: Int): Int = maxOf(0, (current ?: 0) + delta)
}
