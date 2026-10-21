package com.therecalibrationyears.keel.domain

/** Port of iOS `Greeting`: time-aware, for Home. */
object Greeting {
    fun forHour(hour: Int): String = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
}
