package com.therecalibrationyears.keel

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.therecalibrationyears.keel.data.DeviceContext
import com.therecalibrationyears.keel.data.HealthIngestor
import com.therecalibrationyears.keel.data.HealthRepository
import com.therecalibrationyears.keel.data.LocalIdentity
import com.therecalibrationyears.keel.data.SettingsStore
import com.therecalibrationyears.keel.data.UserRepository
import com.therecalibrationyears.keel.data.db.HealthDatabase
import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.domain.HealthAvailability
import com.therecalibrationyears.keel.domain.HealthDataSource
import com.therecalibrationyears.keel.domain.HealthPermissionRequesting
import com.therecalibrationyears.keel.domain.HealthSnapshot
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.platform.HealthConnectReads
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.robolectric.RuntimeEnvironment

/**
 * Fake Health Connect: says what's allowed, counts reads, returns a canned snapshot, and can
 * hold a read open so a test can act (disconnect, tap Sync now) while a sync is in flight.
 * (Port of the iOS tests' `FakeHealthSource`.)
 */
internal class FakeHealthSource : HealthDataSource {
    @Volatile var availability = HealthAvailability.AVAILABLE
    @Volatile var requestable: Set<String> = HealthConnectReads.all.toSet()
    @Volatile var granted: Set<String> = emptySet()
    @Volatile var snapshotToReturn = HealthSnapshot()
    val snapshotReads = AtomicInteger()
    @Volatile var lastWindow: LocalDate? = null
    val revokes = AtomicInteger()
    @Volatile var holdNextRead = false
    @Volatile private var held: CompletableDeferred<Unit>? = null

    override suspend fun availability() = availability
    override suspend fun requestablePermissions() = if (availability == HealthAvailability.AVAILABLE) requestable else emptySet()
    override suspend fun grantedPermissions() = if (availability == HealthAvailability.AVAILABLE) granted else emptySet()
    override suspend fun readsHistory() = HealthConnectReads.HISTORY in grantedPermissions()
    override suspend fun revokeAll() { revokes.incrementAndGet(); granted = emptySet() }

    override suspend fun snapshot(lastDays: Int, flowWindowStart: LocalDate?): HealthSnapshot {
        snapshotReads.incrementAndGet()
        lastWindow = flowWindowStart
        if (holdNextRead) {
            holdNextRead = false
            CompletableDeferred<Unit>().also { held = it }.await()
        }
        // As the real source: the periods window only when periods are allowed.
        return snapshotToReturn.copy(flowWindowStart = flowWindowStart.takeIf { HealthConnectReads.MENSTRUATION in granted })
    }

    val isHoldingRead: Boolean get() = held != null
    fun releaseRead() { held?.complete(Unit); held = null }
}

/** Fake Health Connect permission screen: records each request and answers as told. */
internal class FakeHealthPermissions(private val source: FakeHealthSource) : HealthPermissionRequesting {
    val requests = mutableListOf<Set<String>>()
    var installsOpened = 0
    /** What she allows from what's asked; null: the screen couldn't be shown. */
    var answer: (Set<String>) -> Set<String>? = { it }

    override suspend fun request(permissions: Set<String>): Set<String>? {
        requests += permissions
        val allowed = answer(permissions) ?: return null
        source.granted = source.granted + allowed
        return allowed
    }

    override fun openInstall() { installsOpened++ }
}

/** Keel's health part over in-memory stores and the fakes, with a fixed Sydney clock. */
internal class HealthTestbed(dir: File, retryDelays: List<Duration> = emptyList()) {
    val zone: ZoneId = ZoneId.of("Australia/Sydney")
    @Volatile var now: Instant = Instant.parse("2026-10-21T01:00:00Z") // 12:00 in Sydney
    val clock = object : KeelClock {
        override fun now(): Instant = this@HealthTestbed.now
        override fun zone(): ZoneId = this@HealthTestbed.zone
    }
    val today: LocalDate get() = now.atZone(zone).toLocalDate()

    val main = MainDatabase.inMemory(RuntimeEnvironment.getApplication())
    val healthDb = HealthDatabase.inMemory(RuntimeEnvironment.getApplication())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { File(dir, "keel.preferences_pb") }
    val settings = SettingsStore(prefs)
    private val identity = LocalIdentity(prefs)
    val users = UserRepository(main.userProfiles(), identity::ownerId, clock) {
        DeviceContext("AU", "en_AU", "Australia/Sydney", "1.0.0 (1)", "Google Pixel 8", "Android 16 (API 36)")
    }
    val source = FakeHealthSource()
    val permissions = FakeHealthPermissions(source)
    val repository = HealthRepository(healthDb, clock)
    val health = KeelHealth(
        source, permissions, HealthIngestor(healthDb, main, identity::ownerId, clock), repository,
        users, settings, clock, scope, retryDelays,
    )

    /** Some steps for today: a read that brings data. */
    fun someData() = HealthSnapshot(activityAmounts = mapOf("steps" to mapOf(today to 8000.0)))

    suspend fun waitUntil(timeoutMs: Long = 3_000, condition: suspend () -> Boolean) {
        withTimeout(timeoutMs) { while (!condition()) delay(5) }
    }

    suspend fun waitForIdle() = waitUntil { !health.isSyncInFlight }

    /** Connect, then wait for the first import it starts to finish. */
    suspend fun connectAndSettle(): Boolean {
        val reads = source.snapshotReads.get()
        val connected = health.connect()
        if (connected) waitUntil { source.snapshotReads.get() > reads && !health.isSyncInFlight }
        return connected
    }

    /** She has answered Health Connect for everything Keel reads (iOS status `.unnecessary`). */
    suspend fun everythingAsked() = settings.addHealthAskedPermissions(source.requestable)

    fun close() {
        scope.cancel()
        main.close()
        healthDb.close()
    }
}
