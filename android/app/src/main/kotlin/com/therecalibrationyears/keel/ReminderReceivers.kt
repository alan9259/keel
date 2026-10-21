package com.therecalibrationyears.keel

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.therecalibrationyears.keel.platform.ReminderChannels
import com.therecalibrationyears.keel.platform.ReminderPayload
import kotlinx.coroutines.launch

private val Context.container: AppContainer get() = (applicationContext as KeelApplication).container

/** Runs [work] off the main thread and keeps the receiver alive until it's done. */
private fun BroadcastReceiver.finishAsync(context: Context, work: suspend () -> Unit) {
    val pending = goAsync()
    context.container.receiverScope.launch {
        try { work() } finally { pending.finish() }
    }
}

/** A reminder's time came: arm the next one (everyday reminders) and show it. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val payload = ReminderPayload.from(intent) ?: return
        finishAsync(context) {
            val c = context.container
            if (c.controller.reminders.onReminderFired(payload, c.scheduler::fired)) ReminderPoster.post(context, payload)
        }
    }
}

/** "Mark taken" and "Always mark taken" on a medication reminder (iOS `NotificationCoordinator`). */
class MedicationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val payload = ReminderPayload.from(intent) ?: return
        val medicationId = payload.medicationId ?: return
        NotificationManagerCompat.from(context).cancel(payload.id, 0)
        finishAsync(context) {
            val controller = context.container.controller
            val day = controller.reminders.doseDay(payload)
            when (intent.action) {
                ReminderPoster.ACTION_ALWAYS_TAKEN -> controller.enableMedicationAutoLog(medicationId, payload.slotId, day)
                else -> controller.markMedicationTaken(medicationId, payload.slotId, day)
            }
        }
    }
}

/**
 * Android drops alarms on a reboot, and a clock or time zone change or an update can leave
 * them at the wrong moment: re-arm everything from her records and settings.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        finishAsync(context) { context.container.controller.rearmReminders() }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED, "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}

/** Builds and shows a reminder. Wording comes from the payload, fixed when it was laid out. */
object ReminderPoster {
    const val ACTION_MARK_TAKEN = "com.therecalibrationyears.keel.MARK_TAKEN"
    const val ACTION_ALWAYS_TAKEN = "com.therecalibrationyears.keel.ALWAYS_MARK_TAKEN"
    /** A tap on a medication reminder's body: opens Keel and marks the dose taken (D20). */
    const val ACTION_TAP_MEDICATION = "com.therecalibrationyears.keel.TAP_MEDICATION_REMINDER"

    fun post(context: Context, payload: ReminderPayload) {
        if (!ReminderChannels.enabled(context)) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val builder = NotificationCompat.Builder(context, ReminderChannels.id(payload.kind))
            .setSmallIcon(com.therecalibrationyears.keel.platform.R.drawable.ic_stat_keel)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(tapIntent(context, payload))
        // Auto-logged medicines record themselves, so their reminder carries no buttons.
        if (payload.isMedication && !payload.autoLog) {
            builder.addAction(0, "Mark taken", actionIntent(context, payload, ACTION_MARK_TAKEN))
            builder.addAction(0, "Always mark taken", actionIntent(context, payload, ACTION_ALWAYS_TAKEN))
        }
        NotificationManagerCompat.from(context).notify(payload.id, 0, builder.build())
    }

    private fun tapIntent(context: Context, payload: ReminderPayload): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.fromParts("keel-reminder", payload.id, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (payload.isMedication) payload.toIntent(intent.setAction(ACTION_TAP_MEDICATION))
        return PendingIntent.getActivity(context, payload.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun actionIntent(context: Context, payload: ReminderPayload, action: String): PendingIntent {
        val intent = payload.toIntent(
            Intent(context, MedicationActionReceiver::class.java).setAction(action).setData(Uri.fromParts("keel-reminder", payload.id, null)),
        )
        return PendingIntent.getBroadcast(context, (action + payload.id).hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
