package com.therecalibrationyears.keel.domain

import com.therecalibrationyears.keel.domain.parity.formatOneDecimal

/** Port of iOS `DashboardView.sleepHoursText`: "8h" for a whole number, else "7.5h". */
object SleepFormat {
    fun hours(hours: Double): String =
        if (hours == Math.rint(hours)) "${hours.toInt()}h" else "${formatOneDecimal(hours)}h"
}
