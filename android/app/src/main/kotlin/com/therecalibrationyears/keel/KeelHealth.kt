package com.therecalibrationyears.keel

import com.therecalibrationyears.keel.data.HealthIngestor
import com.therecalibrationyears.keel.data.HealthRepository
import com.therecalibrationyears.keel.data.SettingsStore
import com.therecalibrationyears.keel.data.UserRepository
import com.therecalibrationyears.keel.domain.HealthAvailability
import com.therecalibrationyears.keel.domain.HealthDataSource
import com.therecalibrationyears.keel.domain.HealthFlow
import com.therecalibrationyears.keel.domain.HealthOffer
import com.therecalibrationyears.keel.domain.HealthPermissionRequesting
import com.therecalibrationyears.keel.domain.HealthRequestStatus
import com.therecalibrationyears.keel.domain.HealthSync
import com.therecalibrationyears.keel.domain.HealthSyncCatalog
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.platform.HealthConnectReads
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Health Connect for the app (port of the health part of iOS `AppEnvironment`): connecting,
 * the launch offer, and the sync that brings a year of her data into the separate health store.
 * Nothing here asks Android for anything until she taps Continue or Connect: a sync only runs
 * once she is connected, and only reads what she already allowed.
 */
class KeelHealth(
    private val source: HealthDataSource,
    private val permissions: HealthPermissionRequesting,
    private val ingestor: HealthIngestor,
    /** What has been imported, for the screens that show it beside her own record. */
    val imported: HealthRepository,
    private val users: UserRepository,
    private val settings: SettingsStore,
    private val clock: KeelClock,
    private val scope: CoroutineScope,
    /** After connecting, reads that came back empty are tried again at these delays (iOS 3, 10, 30 s). */
    private val retryDelays: List<Duration> = listOf(Duration.ofSeconds(3), Duration.ofSeconds(10), Duration.ofSeconds(30)),
) {
    private val state = Mutex()
    /** When a sync last brought data in (the 30-minute throttle); an empty read doesn't count. */
    private var lastSyncAt: Instant? = null
    /** One sync at a time (bootstrap and coming to the foreground can both start one). */
    @Volatile private var syncing = false
    /** A forced sync ("Sync now", an item back on, a connect retry) that arrived mid-sync runs after it. */
    private var pendingForced = false

    /** True while a sync is reading or importing. For tests, which wait for it. */
    val isSyncInFlight: Boolean get() = syncing

    /** Whether she has connected (her profile's flag), re-read at each step so a Disconnect mid-sync counts. */
    suspend fun isConnected(): Boolean = users.currentProfile()?.healthConnected == true

    val hasImportedData: Flow<Boolean> = imported.observeHasImportedData()
    suspend fun hasImportedData(): Boolean = imported.hasImportedData()

    suspend fun availability(): HealthAvailability = source.availability()

    /** Would asking show her anything new? Unknown when Health Connect isn't there (plan §3.6). */
    suspend fun requestStatus(): HealthRequestStatus {
        val available = source.availability() == HealthAvailability.AVAILABLE
        if (!available) return HealthRequestStatus.UNKNOWN
        return HealthSync.requestStatus(true, settings.healthAskedPermissions.first(), source.requestablePermissions())
    }

    /**
     * Connect from the onboarding step, the offer or the Health Connect screen (iOS
     * `connectAppleHealth`): Health Connect's own screen asks, and she's connected when she
     * allowed anything Keel reads; then the first import, retried while it comes back empty.
     * Health Connect missing or out of date (Android 9 to 13) opens its Play Store page instead.
     */
    suspend fun connect(): Boolean {
        when (source.availability()) {
            HealthAvailability.UNAVAILABLE -> return false
            HealthAvailability.UPDATE_REQUIRED -> { permissions.openInstall(); return false }
            HealthAvailability.AVAILABLE -> Unit
        }
        val requestable = source.requestablePermissions()
        permissions.request(requestable) ?: return isConnected() // no screen to ask from: nothing changes
        settings.addHealthAskedPermissions(requestable)
        val granted = source.grantedPermissions()
        noteMenstruationGrant(granted)
        val connected = granted.any { it in requestable }
        users.setHealthConnected(connected)
        if (connected) {
            sync(force = true)
            // Reads can come back empty just after the first grant; try again a few times,
            // stopping once anything has arrived (or she disconnected).
            scope.launch {
                for (wait in retryDelays) {
                    delay(wait.toMillis())
                    if (imported.hasImportedData() || !isConnected()) return@launch
                    sync(force = true)
                }
            }
        }
        return connected
    }

    /**
     * Disconnect (plan D4): Keel's access in Health Connect is revoked, and no more is read.
     * What was imported stays until she removes it. She has been asked already, so no launch
     * offer follows.
     */
    suspend fun disconnect() {
        users.setHealthConnected(false)
        source.revokeAll()
        settings.clearHealthFirstMenstruationGrantDay()
    }

    /** The launch offer (iOS `checkHealthAccessOnLaunch`): only after onboarding, once each. */
    suspend fun checkAccessOnLaunch(hasOnboarded: Boolean): HealthOffer {
        if (!hasOnboarded) return HealthOffer.NONE
        val status = requestStatus()
        // Asked about everything Keel reads: a later new read gets its own offer.
        if (status == HealthRequestStatus.UNNECESSARY && settings.healthReconnectOfferAnswered.first()) {
            settings.setHealthReconnectOfferAnswered(false)
        }
        return HealthSync.offer(
            status, hasOnboarded = true, connected = isConnected(),
            connectAnswered = settings.healthConnectOfferDeclined.first(),
            reconnectAnswered = settings.healthReconnectOfferAnswered.first(),
        )
    }

    /** She answered an offer (Continue counts, even if Health Connect then couldn't ask): not made again. */
    suspend fun answerOffer(offer: HealthOffer) {
        when (offer) {
            HealthOffer.CONNECT -> settings.setHealthConnectOfferDeclined(true)
            HealthOffer.RECONNECT -> settings.setHealthReconnectOfferAnswered(true)
            HealthOffer.NONE -> Unit
        }
    }

    /** Switch one item's import on (and bring it in now) or off (what's imported stays). */
    suspend fun setSyncing(itemId: String, on: Boolean) {
        val disabled = settings.disabledHealthItemIds.first()
        settings.setDisabledHealthItemIds(if (on) disabled - itemId else disabled + itemId)
        if (on) sync(force = true)
    }

    /** "Remove imported Health Connect data": the number of rows removed. */
    suspend fun removeImported(): Int = ingestor.purgeAllImportedHealthData()

    /** Launch clean-up of imports Keel no longer keeps (they only arrive in an old iPhone backup). */
    suspend fun purgeDiscontinued() = ingestor.purgeDiscontinuedHealthImports()

    /** "Delete all my data": forget the throttle and her item choices; the next person starts fresh. */
    suspend fun resetSyncState() {
        state.withLock { lastSyncAt = null; pendingForced = false }
        settings.setDisabledHealthItemIds(emptySet())
    }

    /** Revoke Keel's access, for "Delete all my data" (plan D4). */
    suspend fun revokeAll() = source.revokeAll()

    /** Start a sync (iOS `syncHealthData`); returns at once. */
    fun sync(force: Boolean = false) {
        scope.launch { runSync(force) }
    }

    /** One sync, start to finish, then the forced one that waited for it. For tests. */
    suspend fun runSync(force: Boolean) {
        val start = state.withLock {
            when {
                !HealthSync.shouldRun(clock.now(), lastSyncAt, force, isConnected()) -> false
                syncing -> { if (force) pendingForced = true; false } // coalesce; a forced one waits its turn
                else -> { syncing = true; true }
            }
        }
        if (!start) return
        try {
            read()
        } finally {
            val again = state.withLock {
                syncing = false
                pendingForced.also { pendingForced = false }
            }
            if (again) runSync(force = true) // re-checks connected first
        }
    }

    private suspend fun read() {
        // Removing period days she deleted relies on an empty read meaning "none", which holds
        // only once she has answered for everything Keel reads.
        val status = requestStatus()
        val granted = source.grantedPermissions()
        noteMenstruationGrant(granted)
        val window = if (status == HealthRequestStatus.UNNECESSARY) flowWindowStart(granted) else null
        val snapshot = source.snapshot(HealthSync.IMPORT_DAYS, window)
        // The year-long read takes a while: if she disconnected meanwhile, keep none of it.
        if (!isConnected()) return
        ingestor.ingest(HealthSyncCatalog.filter(snapshot, settings.disabledHealthItemIds.first()))
        // Only a read that brought something starts the 30-minute wait.
        if (!snapshot.isEmpty) state.withLock { lastSyncAt = clock.now() }
    }

    private fun today(): LocalDate = clock.now().atZone(clock.zone()).toLocalDate()

    private suspend fun flowWindowStart(granted: Set<String>): LocalDate = HealthFlow.windowStart(
        today(), historyGranted = HealthConnectReads.HISTORY in granted,
        firstMenstruationGrant = settings.healthFirstMenstruationGrantDay.first()?.let(LocalDate::ofEpochDay),
    )

    /** Without the history permission, Health Connect reads back to 30 days before periods were first allowed. */
    private suspend fun noteMenstruationGrant(granted: Set<String>) {
        if (HealthConnectReads.MENSTRUATION in granted) settings.setHealthFirstMenstruationGrantDay(today().toEpochDay())
    }
}
