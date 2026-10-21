package com.therecalibrationyears.keel.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.therecalibrationyears.keel.domain.ReminderCatalog

/**
 * One notification channel per kind of reminder, named as the iOS Reminders screen names them,
 * so she can quieten one kind in Android's settings without losing the others.
 */
object ReminderChannels {
    fun id(kind: String): String = "keel.$kind"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        ReminderCatalog.all.forEach { def ->
            manager.createNotificationChannel(
                NotificationChannel(id(def.id), def.label, NotificationManager.IMPORTANCE_DEFAULT).apply { description = def.desc },
            )
        }
    }

    /** Whether Android will show Keel's notifications at all (permission and app switch). */
    fun enabled(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()
}
