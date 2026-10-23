package com.therecalibrationyears.keel.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.therecalibrationyears.keel.domain.AppLockRules
import com.therecalibrationyears.keel.domain.BiometricAuthenticating
import com.therecalibrationyears.keel.domain.BiometricOutcome
import com.therecalibrationyears.keel.domain.LockState
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.domain.PINHasher
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Where the PIN's salt and hash live (iOS `PINSecureStore`), so the lock is testable without the Keystore. */
interface PINSecureStore {
    suspend fun hasPIN(): Boolean
    suspend fun save(salt: ByteArray, hash: ByteArray)
    suspend fun load(): Pair<ByteArray, ByteArray>?
    suspend fun clear()
}

/**
 * The app lock (port of iOS `AppLockService`): a 4-digit Keel PIN, with biometrics as the
 * quick unlock on top. Off by default; setting a PIN turns it on. While on, Keel starts locked
 * on a cold launch and locks again when she comes back after the grace window. Five wrong PINs
 * in a row start a one-minute lockout.
 */
class AppLockService(
    private val store: PINSecureStore,
    private val biometrics: BiometricAuthenticating,
    private val prefs: DataStore<Preferences>,
    private val clock: KeelClock,
) {
    private val writes = Mutex()
    private val current = MutableStateFlow<LockState?>(null)

    /** Null until [load] has read the saved state (the launch screen waits for it). */
    val state: StateFlow<LockState?> = current.asStateFlow()
    private var backgroundedAt: Instant? = null

    private fun value() = current.value ?: LockState()

    /**
     * Read the saved state. The lock is on only when she turned it on AND a PIN is stored; a
     * PIN without the flag is tidied away. Locked on a cold launch when on.
     */
    suspend fun load() = writes.withLock {
        val p = prefs.data.first()
        val flag = p[ENABLED] ?: false
        if (!flag && store.hasPIN()) store.clear()
        val enabled = flag && store.hasPIN()
        current.value = LockState(
            isEnabled = enabled,
            isLocked = enabled,
            biometricUnlockEnabled = p[BIOMETRICS] ?: true,
            failedAttempts = p[ATTEMPTS] ?: 0,
            lockedOutUntil = p[LOCKED_OUT_UNTIL]?.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        )
    }

    /** Whether biometrics can be offered here (one is enrolled). The PIN works regardless. */
    val biometricsAvailable: Boolean get() = biometrics.canAuthenticate()

    /** Whether to lead with biometrics: her preference and a biometric being there. */
    fun useBiometrics(state: LockState = value()) = state.biometricUnlockEnabled && biometricsAvailable

    suspend fun setBiometricUnlock(on: Boolean) {
        current.update { (it ?: LockState()).copy(biometricUnlockEnabled = on) }
        prefs.edit { it[BIOMETRICS] = on }
    }

    /** Create or change the PIN, which turns the lock on. False for anything but 4 digits. */
    suspend fun setPIN(pin: String): Boolean {
        if (!AppLockRules.isValidPIN(pin)) return false
        val salt = PINHasher.makeSalt()
        val hash = withContext(Dispatchers.Default) { PINHasher.hash(pin, salt) }
        writes.withLock {
            store.save(salt, hash)
            prefs.edit { it[ENABLED] = true }
            current.update { (it ?: LockState()).copy(isEnabled = true, isLocked = false) }
            resetAttempts()
        }
        return true
    }

    /** Turn the lock off and forget the PIN (she is inside the app, or proved it's her phone). */
    suspend fun disable() = writes.withLock {
        store.clear()
        prefs.edit { it[ENABLED] = false }
        current.update { (it ?: LockState()).copy(isEnabled = false, isLocked = false) }
        resetAttempts()
    }

    fun isLockedOut(now: Instant = clock.now()): Boolean = value().lockedOutUntil?.let { now.isBefore(it) } ?: false

    /** Check a typed PIN: honours the lockout and counts failures toward it. */
    suspend fun verifyPIN(pin: String, now: Instant = clock.now()): Boolean {
        if (isLockedOut(now)) return false
        val (salt, hash) = store.load() ?: return false
        val candidate = withContext(Dispatchers.Default) { PINHasher.hash(pin, salt) }
        return writes.withLock {
            if (PINHasher.constantTimeEquals(candidate, hash)) {
                current.update { it?.copy(isLocked = false) }
                resetAttempts()
                true
            } else {
                val attempts = value().failedAttempts + 1
                val until = AppLockRules.lockoutAfterFailure(attempts, now) ?: value().lockedOutUntil
                current.update { it?.copy(failedAttempts = attempts, lockedOutUntil = until) }
                persistAttempts(attempts, until)
                false
            }
        }
    }

    /** The forgot-PIN recovery is possible: the phone has a screen lock or a biometric. */
    val canRecoverWithScreenLock: Boolean get() = biometrics.canAuthenticateOwner()

    /** Forgot PIN: prove it's her phone. Her data is untouched; she then sets a new PIN or turns the lock off. */
    suspend fun authenticateOwner(reason: String): Boolean =
        biometrics.canAuthenticateOwner() && biometrics.authenticateOwner(reason)

    /** Try biometrics. */
    suspend fun unlockWithBiometrics(reason: String): BiometricOutcome {
        if (!value().isLocked || !useBiometrics()) return BiometricOutcome.CANCELLED
        val outcome = biometrics.authenticate(reason)
        if (outcome == BiometricOutcome.UNLOCKED) writes.withLock {
            current.update { it?.copy(isLocked = false) }
            resetAttempts()
        }
        return outcome
    }

    /** Keel left the screen: note when, so a return after the grace window locks. */
    fun markBackgrounded(now: Instant = clock.now()) {
        if (value().isEnabled) backgroundedAt = now
    }

    /** Keel came back: lock if she was away past the grace window. */
    fun applyForeground(now: Instant = clock.now()) {
        val left = backgroundedAt
        backgroundedAt = null
        if (!value().isEnabled) return
        if (AppLockRules.shouldLock(now, left)) current.update { it?.copy(isLocked = true) }
    }

    private suspend fun resetAttempts() {
        current.update { it?.copy(failedAttempts = 0, lockedOutUntil = null) }
        persistAttempts(0, null)
    }

    private suspend fun persistAttempts(attempts: Int, until: Instant?) = prefs.edit {
        it[ATTEMPTS] = attempts
        it[LOCKED_OUT_UNTIL] = until?.toEpochMilli() ?: 0L
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("keel.appLockEnabled")
        val BIOMETRICS = booleanPreferencesKey("keel.appLockBiometrics")
        val ATTEMPTS = intPreferencesKey("keel.pinFailedAttempts")
        val LOCKED_OUT_UNTIL = longPreferencesKey("keel.pinLockedOutUntil")
    }
}
