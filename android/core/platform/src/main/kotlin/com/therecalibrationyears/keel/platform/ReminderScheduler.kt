package com.therecalibrationyears.keel.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.therecalibrationyears.keel.domain.MedicationReminder
import com.therecalibrationyears.keel.domain.ReminderIds
import com.therecalibrationyears.keel.domain.RepeatingReminder
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What a fired reminder carries to the receiver: enough to post it, to log a dose against the
 * day it was for (D10), and to arm the next one of a repeating reminder.
 */
data class ReminderPayload(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val medicationId: String? = null,
    val slotId: String? = null,
    val dayKey: String? = null,
    val autoLog: Boolean = false,
    val hour: Int? = null,
    val minute: Int? = null,
    val weekday: Int? = null,
) {
    val isMedication: Boolean get() = medicationId != null

    fun toIntent(intent: Intent): Intent = intent.apply {
        putExtra(EXTRA_ID, id); putExtra(EXTRA_KIND, kind); putExtra(EXTRA_TITLE, title); putExtra(EXTRA_BODY, body)
        medicationId?.let { putExtra(EXTRA_MEDICATION, it) }
        slotId?.let { putExtra(EXTRA_SLOT, it) }
        dayKey?.let { putExtra(EXTRA_DAY, it) }
        putExtra(EXTRA_AUTO_LOG, autoLog)
        hour?.let { putExtra(EXTRA_HOUR, it) }
        minute?.let { putExtra(EXTRA_MINUTE, it) }
        weekday?.let { putExtra(EXTRA_WEEKDAY, it) }
    }

    /** The repeating reminder this was, to arm its next occurrence. */
    fun repeating(): RepeatingReminder? {
        if (isMedication) return null
        return RepeatingReminder(id, kind, title, body, hour ?: return null, minute ?: 0, weekday)
    }

    companion object {
        private const val EXTRA_ID = "keel.reminder.id"
        private const val EXTRA_KIND = "keel.reminder.kind"
        private const val EXTRA_TITLE = "keel.reminder.title"
        private const val EXTRA_BODY = "keel.reminder.body"
        private const val EXTRA_MEDICATION = "keel.reminder.medicationID"
        private const val EXTRA_SLOT = "keel.reminder.slot"
        private const val EXTRA_DAY = "keel.reminder.day"
        private const val EXTRA_AUTO_LOG = "keel.reminder.autoLog"
        private const val EXTRA_HOUR = "keel.reminder.hour"
        private const val EXTRA_MINUTE = "keel.reminder.minute"
        private const val EXTRA_WEEKDAY = "keel.reminder.weekday"

        fun from(intent: Intent): ReminderPayload? {
            val id = intent.getStringExtra(EXTRA_ID) ?: return null
            fun int(key: String) = if (intent.hasExtra(key)) intent.getIntExtra(key, 0) else null
            return ReminderPayload(
                id = id,
                kind = intent.getStringExtra(EXTRA_KIND).orEmpty(),
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                body = intent.getStringExtra(EXTRA_BODY).orEmpty(),
                medicationId = intent.getStringExtra(EXTRA_MEDICATION),
                slotId = intent.getStringExtra(EXTRA_SLOT),
                dayKey = intent.getStringExtra(EXTRA_DAY),
                autoLog = intent.getBooleanExtra(EXTRA_AUTO_LOG, false),
                hour = int(EXTRA_HOUR), minute = int(EXTRA_MINUTE), weekday = int(EXTRA_WEEKDAY),
            )
        }

        fun of(r: MedicationReminder) = ReminderPayload(
            id = r.id, kind = "medication", title = r.title, body = r.body,
            medicationId = r.medicationId, slotId = r.slotId, dayKey = r.dayKey, autoLog = r.autoLog,
        )

        fun of(r: RepeatingReminder) = ReminderPayload(
            id = r.id, kind = r.kind, title = r.title, body = r.body, hour = r.hour, minute = r.minute, weekday = r.weekday,
        )
    }
}

/** Scheduling seam, so the app's reminder logic is testable without the alarm service. */
interface ReminderScheduling {
    /** Ids of every reminder currently armed. */
    suspend fun scheduledIds(): Set<String>
    suspend fun schedule(payload: ReminderPayload, at: ZonedDateTime)
    suspend fun cancel(ids: Collection<String>)
    suspend fun cancelAll()

    suspend fun scheduleMedication(r: MedicationReminder) = schedule(ReminderPayload.of(r), r.fireAt)
    suspend fun scheduleRepeating(r: RepeatingReminder, now: ZonedDateTime) = schedule(ReminderPayload.of(r), r.nextFire(now))

    /** iOS `cancelMedicationReminders`: every one for a medicine, or one day, or one dose that day. */
    suspend fun cancelMedication(medicationId: String, dayKey: String? = null, slotId: String? = null) =
        cancel(scheduledIds().filter { ReminderIds.matchesMedication(it, medicationId, dayKey, slotId) })

    suspend fun cancelAllMedication() = cancel(scheduledIds().filter { it.startsWith(ReminderIds.MED_PREFIX) })
}

/**
 * Reminders on Android's alarm service (plan §1.7). Android can't list pending alarms, so the
 * ids armed are kept in a small device-only registry, which is how a prefix, day or dose can
 * be cancelled the way iOS cancels pending requests. Exact alarms when Android allows them
 * (never asked for); otherwise Android delivers close to the time, also in Doze.
 */
class AlarmReminderScheduler(
    private val context: Context,
    private val registry: DataStore<Preferences>,
    private val receiver: Class<out BroadcastReceiver>,
) : ReminderScheduling {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val lock = Mutex()

    override suspend fun scheduledIds(): Set<String> = registry.data.first()[IDS] ?: emptySet()

    override suspend fun schedule(payload: ReminderPayload, at: ZonedDateTime) = lock.withLock {
        val pending = pendingIntent(payload.id, payload, PendingIntent.FLAG_UPDATE_CURRENT) ?: return@withLock
        val millis = at.toInstant().toEpochMilli()
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
        registry.edit { it[IDS] = (it[IDS] ?: emptySet()) + payload.id }
        Unit
    }

    override suspend fun cancel(ids: Collection<String>) = lock.withLock {
        if (ids.isEmpty()) return@withLock
        ids.forEach { id ->
            pendingIntent(id, null, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
        }
        registry.edit { it[IDS] = (it[IDS] ?: emptySet()) - ids.toSet() }
        Unit
    }

    override suspend fun cancelAll() = cancel(scheduledIds())

    /** Called when a one-off reminder has fired, so the registry only lists what's still armed. */
    suspend fun fired(id: String) = lock.withLock {
        registry.edit { it[IDS] = (it[IDS] ?: emptySet()) - id }
        Unit
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /** One alarm per id: the id is the intent's data, so two reminders never replace each other. */
    private fun pendingIntent(id: String, payload: ReminderPayload?, flag: Int): PendingIntent? {
        val intent = Intent(context, receiver).setData(Uri.fromParts(SCHEME, id, null))
        payload?.toIntent(intent)
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    private companion object {
        const val SCHEME = "keel-reminder"
        val IDS = stringSetPreferencesKey("keel.scheduledReminders")
    }
}
