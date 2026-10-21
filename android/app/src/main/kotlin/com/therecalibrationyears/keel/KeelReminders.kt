package com.therecalibrationyears.keel

import com.therecalibrationyears.keel.data.LoggedDose
import com.therecalibrationyears.keel.data.MedicationRepository
import com.therecalibrationyears.keel.data.SettingsStore
import com.therecalibrationyears.keel.data.schedule
import com.therecalibrationyears.keel.domain.ExactTiming
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.domain.LifestyleReminders
import com.therecalibrationyears.keel.domain.MedicationReminders
import com.therecalibrationyears.keel.domain.ReminderIds
import com.therecalibrationyears.keel.domain.ReminderKinds
import com.therecalibrationyears.keel.domain.ReminderPlanner
import com.therecalibrationyears.keel.platform.ReminderPayload
import com.therecalibrationyears.keel.platform.ReminderScheduling
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Laying out and answering reminders (port of the reminder parts of iOS `AppEnvironment` and
 * `NotificationCoordinator`, same method names). Medication reminders are dated one-offs laid
 * out ahead, so a single day's dose can be skipped once it's logged; everyday reminders arm
 * their next time and re-arm when they fire. Nothing here ever asks for permission: that
 * happens only after "Continue" on the reminders explanation, or when she turns one on.
 */
class KeelReminders(
    private val settings: SettingsStore,
    private val medications: MedicationRepository,
    private val scheduler: ReminderScheduling,
    private val clock: KeelClock,
    /** Whether Android delivers reminders on time (exact alarms); see [shouldOfferExactTiming]. */
    private val exactAlarmsAllowed: () -> Boolean = { true },
    /** Whether Android will show Keel's notifications (permission and app switch). */
    private val notificationsAllowed: () -> Boolean,
) {
    private val lock = Mutex()

    private fun now() = clock.now().atZone(clock.zone())
    private fun today(): LocalDate = now().toLocalDate()

    /** Everything, in the iOS launch order (bootstrap, a reboot or a clock change). */
    suspend fun refreshAll(autoLog: suspend () -> List<LoggedDose>) {
        refreshLifestyleReminders()
        // Auto-log first, so the scheduler sees today's doses as logged and skips them.
        autoLog()
        refreshMedicationReminders()
    }

    /**
     * Lay every medicine's reminders out again. Off with the master switch or the "Medication
     * reminders" toggle (Android honours the toggle; iOS ignores it, plan D2). Reminders for
     * medicines no longer on her list are cancelled.
     */
    suspend fun refreshMedicationReminders() = lock.withLock {
        val on = settings.pushNotifications.first() && ReminderKinds.MEDICATION in settings.enabledReminderIds.first()
        if (!on) {
            scheduler.cancelAllMedication()
            return@withLock
        }
        val now = now()
        val today = now.toLocalDate()
        val meds = medications.active()
        val horizon = ReminderPlanner.cycleHorizon(meds.size)
        val showName = settings.medicineNamesInReminders.first()
        val planned = meds.flatMap { med ->
            val logged = medications.loggedSlots(med, today)
            MedicationReminders.plan(
                medicationId = med.id, name = med.name, schedule = med.schedule(clock.zone()), autoLog = med.autoLogDoses,
                showName = showName, cycleHorizon = horizon,
                loggedTodayWholeDay = null in logged, loggedTodaySlots = logged.filterNotNull().toSet(), now = now,
            )
        }
        val wanted = planned.map { it.id }.toSet()
        scheduler.cancel(scheduler.scheduledIds().filter { it.startsWith(ReminderIds.MED_PREFIX) && it !in wanted })
        planned.forEach { scheduler.scheduleMedication(it) }
    }

    /**
     * Re-arm the everyday reminders she has on, and clear the ones she hasn't. Only once
     * Android will show them: this runs at launch, and must never lead to a prompt.
     */
    suspend fun refreshLifestyleReminders() = lock.withLock {
        if (!settings.pushNotifications.first() || !notificationsAllowed()) return@withLock
        val enabled = settings.enabledReminderIds.first()
        val config = settings.reminderConfig.first()
        val now = now()
        ReminderKinds.LIFESTYLE.forEach { kind ->
            val plan = if (kind in enabled) LifestyleReminders.forKind(kind, config) else emptyList()
            val keep = plan.map { it.id }.toSet()
            scheduler.cancel(LifestyleReminders.allIds(kind).filter { it !in keep })
            plan.forEach { scheduler.scheduleRepeating(it, now) }
        }
    }

    /** Reschedule one everyday reminder from her current timing (the Reminders screen). */
    suspend fun rescheduleLifestyleReminder(kind: String) = lock.withLock {
        scheduler.cancel(LifestyleReminders.allIds(kind))
        if (!settings.pushNotifications.first()) return@withLock
        val now = now()
        LifestyleReminders.forKind(kind, settings.reminderConfig.first()).forEach { scheduler.scheduleRepeating(it, now) }
    }

    suspend fun cancelLifestyleReminder(kind: String) = scheduler.cancel(LifestyleReminders.allIds(kind))

    /** She removed a medicine: none of its reminders can come round again. */
    suspend fun cancelMedicationReminders(medicationId: String) = scheduler.cancelMedication(medicationId)

    /** She logged a dose (or the whole day when [slot] is null): no nudge for it that day. */
    suspend fun cancelMedicationReminders(medicationId: String, day: LocalDate, slot: String?) =
        scheduler.cancelMedication(medicationId, ReminderPlanner.dayKey(day), slot)

    /** The master switch (Settings): off clears every reminder; on lays out the ones she has on. */
    suspend fun setPushNotificationsEnabled(on: Boolean) {
        settings.setPushNotifications(on)
        if (on) {
            refreshLifestyleReminders()
            refreshMedicationReminders()
        } else {
            scheduler.cancelAll()
        }
    }

    /**
     * Her answer to the reminders explanation (iOS `recordNotificationExplainerAnswer`).
     * Continue is the only button (pre-permission rule), so in practice this is always yes.
     */
    suspend fun recordNotificationExplainerAnswer(allow: Boolean) {
        settings.setNotificationExplainerPending(false)
        if (allow) settings.setPushNotifications(true) else setPushNotificationsEnabled(false)
    }

    /**
     * A reminder's time came (the alarm receiver). An everyday one arms its next time first.
     * Returns whether to show it: not if reminders were switched off since, or the medicine
     * is gone.
     */
    suspend fun onReminderFired(payload: ReminderPayload, fired: suspend (String) -> Unit): Boolean {
        if (!settings.pushNotifications.first()) return false
        if (payload.isMedication) {
            fired(payload.id)
            val id = payload.medicationId ?: return false
            return ReminderKinds.MEDICATION in settings.enabledReminderIds.first() && medications.active(id) != null
        }
        if (payload.kind !in settings.enabledReminderIds.first()) return false
        payload.repeating()?.let { scheduler.scheduleRepeating(it, now()) }
        return true
    }

    /**
     * She has just saved [medicationId] (Android only, D25): explain, once, how to get its
     * reminders on time, if Android would otherwise deliver them late. Not for a medicine with
     * nothing armed (as needed, no time, stopped, or medication reminders off).
     */
    suspend fun shouldOfferExactTiming(medicationId: String): Boolean = ExactTiming.shouldOffer(
        exactAllowed = exactAlarmsAllowed(),
        alreadyOffered = settings.exactTimingOffered.first(),
        notificationsAllowed = notificationsAllowed(),
        medicineHasReminders = scheduler.scheduledIds().any { ReminderIds.matchesMedication(it, medicationId) },
    )

    /** She has read the explanation (its Continue opens Android's page); it isn't shown again. */
    suspend fun recordExactTimingOffered() = settings.setExactTimingOffered(true)

    /** The day a medication reminder's dose belongs to (the occurrence, not when she tapped: D10). */
    fun doseDay(payload: ReminderPayload): LocalDate = payload.dayKey?.let(MedicationReminders::dayOf) ?: today()
}
