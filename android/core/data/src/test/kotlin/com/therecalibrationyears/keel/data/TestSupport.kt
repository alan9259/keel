package com.therecalibrationyears.keel.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.domain.KeelClock
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.robolectric.RuntimeEnvironment

/** A clock tests move by hand, in UTC (as `TestStore.utcCalendar` on iOS). */
class TestClock(var instant: Instant = Instant.parse("2026-08-21T09:00:00Z")) : KeelClock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = ZoneOffset.UTC
    fun advanceSeconds(s: Long) { instant = instant.plusSeconds(s) }
}

val testDevice = DeviceContext(
    region = "AU", localeID = "en_AU", timeZoneID = "Australia/Sydney",
    appVersion = "1.0.0 (1)", deviceModel = "Google Pixel 8", osVersion = "Android 16 (API 36)",
)

fun inMemoryDatabase(): MainDatabase = MainDatabase.inMemory(RuntimeEnvironment.getApplication())

fun tempDataStore(dir: File): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) { File(dir, "keel-${System.nanoTime()}.preferences_pb") }

fun userRepository(db: MainDatabase, clock: KeelClock = TestClock(), ownerId: String = "local-TEST") =
    UserRepository(db.userProfiles(), ownerId = { ownerId }, clock = clock, device = { testDevice })
