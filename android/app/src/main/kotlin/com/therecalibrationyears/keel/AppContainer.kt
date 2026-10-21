package com.therecalibrationyears.keel

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.therecalibrationyears.keel.data.DeviceContext
import com.therecalibrationyears.keel.data.LocalIdentity
import com.therecalibrationyears.keel.data.SettingsStore
import com.therecalibrationyears.keel.data.UserRepository
import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.domain.KeelClock
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
    private val database = MainDatabase.open(context)

    val settings = SettingsStore(preferences)
    private val identity = LocalIdentity(preferences)
    private val users = UserRepository(
        database.userProfiles(), ownerId = identity::ownerId, clock = KeelClock.System,
        device = DeviceContext.Android(context),
    )

    val controller = KeelAppController(users, identity, settings, appScope)
}
