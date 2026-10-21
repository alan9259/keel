package com.therecalibrationyears.keel

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.therecalibrationyears.keel.data.ActivityRepository
import com.therecalibrationyears.keel.data.CheckInRepository
import com.therecalibrationyears.keel.data.CycleRepository
import com.therecalibrationyears.keel.data.DeviceContext
import com.therecalibrationyears.keel.data.InsightRepository
import com.therecalibrationyears.keel.data.MedicationRepository
import com.therecalibrationyears.keel.data.LocalIdentity
import com.therecalibrationyears.keel.data.SettingsStore
import com.therecalibrationyears.keel.data.SymptomRepository
import com.therecalibrationyears.keel.data.TreatmentCatalogStore
import com.therecalibrationyears.keel.designsystem.KeelDateFormats
import com.therecalibrationyears.keel.data.UserRepository
import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.platform.AlarmReminderScheduler
import com.therecalibrationyears.keel.platform.ExactAlarms
import com.therecalibrationyears.keel.platform.ReminderChannels
import com.therecalibrationyears.keel.platform.SpeechRecognitionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Builds every long-lived object once, at launch (the composition root, as the iOS
 * `KeelApp` + `AppEnvironment` init). One database and one preferences file per process.
 */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val preferences = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { context.preferencesDataStoreFile("keel") },
    )
    /** Internal so debug seeds (src/debug) can write sample rows; features go through repositories. */
    internal val database = MainDatabase.open(context)
    val clock: KeelClock = KeelClock.System

    val settings = SettingsStore(preferences)
    internal val identity = LocalIdentity(preferences)
    private val users = UserRepository(
        database.userProfiles(), ownerId = identity::ownerId, clock = clock,
        device = DeviceContext.Android(context),
    )
    private val checkIns = CheckInRepository(database.checkIns(), identity::ownerId, clock)
    private val symptoms = SymptomRepository(database.symptoms(), database.checkIns(), settings, identity::ownerId, clock)
    // The locale's time words for the stored `timing` text, read at save time (12 or 24 hour).
    private val medications = MedicationRepository(database.medications(), identity::ownerId, clock) {
        KeelDateFormats(context, zone = clock.zone())
    }
    private val treatments = TreatmentCatalogStore(context)
    private val activities = ActivityRepository(database.activityLogs(), clock, identity::ownerId)
    private val cycle = CycleRepository(database.cycle(), identity::ownerId, clock)
    private val insights = InsightRepository(database.insights(), database.checkIns(), database.cycle(), identity::ownerId, clock)

    private val speech = SpeechRecognitionService(context)

    /** Work started by broadcast receivers (alarms, reminder buttons, reboot). */
    val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Armed reminders are device state, kept apart from her settings and records. */
    private val reminderRegistry = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { context.preferencesDataStoreFile("keel-reminders") },
    )
    val scheduler = AlarmReminderScheduler(context, reminderRegistry, ReminderAlarmReceiver::class.java)
    private val reminders = KeelReminders(
        settings, medications, scheduler, clock,
        exactAlarmsAllowed = { ExactAlarms.allowed(context) },
        notificationsAllowed = { ReminderChannels.enabled(context) },
    )

    init {
        ReminderChannels.ensure(context)
    }

    val controller = KeelAppController(
        users, identity, settings, checkIns, symptoms, medications, activities, cycle, insights, speech, treatments, reminders, clock, appScope,
    )
}
