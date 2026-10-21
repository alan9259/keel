package com.therecalibrationyears.keel.platform

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Exact alarms (plan §1.7, D25). Android 12+ lets her turn them off for an app, and Android
 * 14+ starts new installs with them off; without them Android may deliver a reminder up to an
 * hour late. Granting them sends `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, and the
 * reschedule receiver re-arms everything on time.
 */
object ExactAlarms {
    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Android's "Alarms & reminders" page for Keel (or Keel's app info, if a phone lacks it). */
    fun openSettings(context: Context) {
        val uri = Uri.fromParts("package", context.packageName, null)
        val page = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, uri)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
        }
        try {
            context.startActivity(page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
