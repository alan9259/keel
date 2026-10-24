package com.therecalibrationyears.keel

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.therecalibrationyears.keel.data.ActivityRepository
import com.therecalibrationyears.keel.data.CheckInRepository
import com.therecalibrationyears.keel.data.CycleRepository
import com.therecalibrationyears.keel.data.DeviceContext
import com.therecalibrationyears.keel.data.GPSummaryService
import com.therecalibrationyears.keel.data.InsightRepository
import com.therecalibrationyears.keel.data.MedicationRepository
import com.therecalibrationyears.keel.data.LocalIdentity
import com.therecalibrationyears.keel.data.RecordEraser
import com.therecalibrationyears.keel.data.AppLockService
import com.therecalibrationyears.keel.data.KeystorePINStore
import com.therecalibrationyears.keel.platform.SystemBiometricAuthenticator
import com.therecalibrationyears.keel.data.SettingsStore
import com.therecalibrationyears.keel.gpsummary.GPSummaryFiles
import com.therecalibrationyears.keel.settings.AppInfo
import com.therecalibrationyears.keel.data.SymptomRepository
import com.therecalibrationyears.keel.data.TreatmentCatalogStore
import com.therecalibrationyears.keel.designsystem.KeelDateFormats
import com.therecalibrationyears.keel.data.UserRepository
import com.therecalibrationyears.keel.data.backup.BackupService
import com.therecalibrationyears.keel.data.db.HealthDatabase
import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.domain.HealthDataSource
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.platform.HealthConnectPermissions
import com.therecalibrationyears.keel.platform.HealthConnectSource
import com.therecalibrationyears.keel.data.HealthIngestor
import com.therecalibrationyears.keel.data.HealthRepository
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
    /** Imported health readings, kept apart from her records (plan §1.4). */
    private val healthDatabase = HealthDatabase.open(context)
    val clock: KeelClock = KeelClock.System

    val settings = SettingsStore(preferences)
    internal val identity = LocalIdentity(preferences)
    /** Internal so debug probes (src/debug) can write a profile; features go through the controller. */
    internal val users = UserRepository(
        database.userProfiles(), ownerId = identity::ownerId, clock = clock,
        device = DeviceContext.Android(context),
    )
    private val checkIns = CheckInRepository(database.checkIns(), identity::ownerId, clock)
    private val symptoms = SymptomRepository(database.symptoms(), database.checkIns(), settings, identity::ownerId, clock)
    // The locale's time words for the stored `timing` text, read at save time (12 or 24 hour).
    // Internal so debug seeds (src/debug) add medicines the way she does.
    internal val medications = MedicationRepository(database.medications(), identity::ownerId, clock) {
        KeelDateFormats(context, zone = clock.zone())
    }
    private val treatments = TreatmentCatalogStore(context)
    private val activities = ActivityRepository(database.activityLogs(), clock, identity::ownerId)
    private val cycle = CycleRepository(database.cycle(), identity::ownerId, clock)
    private val insights = InsightRepository(database.insights(), database.checkIns(), database.cycle(), identity::ownerId, clock)
    private val gpSummary = GPSummaryService(checkIns, medications, cycle, users, clock) { KeelDateFormats(context, zone = clock.zone()) }

    /** For debug probes (src/debug) that need resources, such as the GP summary's fonts. */
    internal val appContext: Context = context.applicationContext

    private val speech = SpeechRecognitionService(context)
    /** Health Connect, read only (plan §3). Internal so debug probes (src/debug) can read it directly. */
    internal val healthSource: HealthDataSource = HealthConnectSource(context, clock)
    /** Health Connect's permission screen; the activity on screen registers itself (MainActivity). */
    val healthPermissions = HealthConnectPermissions(context)

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
        clearPosted = { NotificationManagerCompat.from(context).cancelAll() },
        notificationsAllowed = { ReminderChannels.enabled(context) },
    )

    init {
        ReminderChannels.ensure(context)
    }

    private val appInfo: AppInfo = DeviceContext.Android(context).current().let { device ->
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        AppInfo(shortVersion = info.versionName.orEmpty(), appVersion = device.appVersion, osVersion = device.osVersion, deviceModel = device.deviceModel)
    }

    /** The PIN's salt and hash, Keystore-encrypted, in a file of their own. */
    private val secure = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { context.preferencesDataStoreFile("keel-secure") },
    )
    /** The biometric prompt; the activity on screen registers itself (MainActivity). */
    val biometrics = SystemBiometricAuthenticator(context, usePINLabel = "Enter PIN instead")
    private val lock = AppLockService(KeystorePINStore(secure), biometrics, preferences, clock)

    private val health = KeelHealth(
        healthSource, healthPermissions,
        HealthIngestor(healthDatabase, database, identity::ownerId, clock), HealthRepository(healthDatabase, clock),
        users, settings, clock, appScope,
    )

    val controller = KeelAppController(
        users, identity, settings, checkIns, symptoms, medications, activities, cycle, insights, gpSummary, speech, treatments, reminders, clock, appScope,
        records = RecordEraser(database, healthDatabase),
        backup = BackupService(database, healthDatabase, clock) { appInfo.shortVersion },
        lock = lock,
        appInfo = appInfo,
        clearSharedFiles = { GPSummaryFiles.clearShared(context) },
        health = health,
        manageHealthAccess = healthPermissions::openManageAccess,
    )
}
