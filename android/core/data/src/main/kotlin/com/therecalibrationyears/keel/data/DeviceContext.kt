package com.therecalibrationyears.keel.data

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.time.ZoneId
import java.util.Locale

/**
 * Non-identifying environment context (port of iOS `DeviceContext`): recorded on her
 * profile to tailor content (AU vs NZ support lines), schedule reminders in her time zone
 * and help support. It describes the app's environment, not the person: no advertising
 * id, no fingerprinting.
 */
data class DeviceContext(
    val region: String?,
    val localeID: String,
    val timeZoneID: String,
    val appVersion: String,
    val deviceModel: String,
    val osVersion: String,
) {
    fun interface Source {
        fun current(): DeviceContext
    }

    class Android(private val context: Context) : Source {
        override fun current(): DeviceContext {
            val locale = Locale.getDefault()
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            return DeviceContext(
                region = locale.country.ifEmpty { null },
                // iOS `Locale.identifier` form, e.g. "en_AU".
                localeID = if (locale.country.isEmpty()) locale.language else "${locale.language}_${locale.country}",
                timeZoneID = ZoneId.systemDefault().id,
                appVersion = "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})",
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", // not-copy: support context, never shown
            )
        }
    }
}
