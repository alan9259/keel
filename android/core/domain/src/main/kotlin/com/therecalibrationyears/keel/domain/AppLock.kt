package com.therecalibrationyears.keel.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant

/**
 * Hashing for the app lock PIN (port of iOS `PINHasher`). The PIN is never stored in the
 * clear: a random salt and a slow, salted hash, so the stored value can't be reversed and two
 * people with the same PIN don't share a hash. The same bytes as iOS for the same salt and PIN.
 */
object PINHasher {
    /** Iterated SHA-256 to add brute-force cost (a 4-digit PIN is a tiny keyspace; the real defences are the Keystore and the lockout). */
    const val ROUNDS = 100_000

    fun makeSalt(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(16).also(random::nextBytes)

    fun hash(pin: String, salt: ByteArray, rounds: Int = ROUNDS): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256") // not-copy: algorithm
        var data = salt + pin.toByteArray(Charsets.UTF_8)
        repeat(maxOf(rounds, 1)) { data = sha.digest(data) }
        return data
    }

    /** Constant-time compare, so verifying can't leak the hash through timing. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}

/** The app lock's fixed rules (port of the constants and pure parts of iOS `AppLockService`). */
object AppLockRules {
    const val PIN_LENGTH = 4
    /** Wrong PINs allowed before a timed lockout. */
    const val MAX_ATTEMPTS = 5
    val LOCKOUT: Duration = Duration.ofSeconds(60)
    /** Coming back within this long after leaving doesn't ask again. */
    val GRACE: Duration = Duration.ofSeconds(30)

    fun isValidPIN(pin: String) = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    /** Whether a return should lock again: only once the grace window has passed. */
    fun shouldLock(now: Instant, backgroundedAt: Instant?, grace: Duration = GRACE): Boolean =
        backgroundedAt != null && Duration.between(backgroundedAt, now) >= grace

    /** After a wrong PIN: a lockout starts on every fifth failure in a row. */
    fun lockoutAfterFailure(failedAttempts: Int, now: Instant): Instant? =
        if (failedAttempts % MAX_ATTEMPTS == 0) now.plus(LOCKOUT) else null

    /** Whole seconds left, rounded up ("Try again in 60s"). */
    fun secondsLeft(lockedOutUntil: Instant?, now: Instant): Int {
        if (lockedOutUntil == null || !now.isBefore(lockedOutUntil)) return 0
        val millis = Duration.between(now, lockedOutUntil).toMillis()
        return ((millis + 999) / 1000).toInt()
    }
}

/**
 * How a biometric prompt ended. Android's prompt has its own "Enter PIN instead" button, so
 * Keel can go straight to its PIN pad rather than ask again.
 */
enum class BiometricOutcome { UNLOCKED, USE_PIN, CANCELLED }

/** The phone's biometric and screen-lock checks (iOS `BiometricAuthenticating`), behind an interface for tests. */
interface BiometricAuthenticating {
    /** A biometric is enrolled and can unlock Keel. */
    fun canAuthenticate(): Boolean
    /** The biometric prompt. Keel's own PIN is the fallback, so biometrics only. */
    suspend fun authenticate(reason: String): BiometricOutcome
    /** The phone can confirm its owner (a biometric or its screen lock): the forgot-PIN recovery. */
    fun canAuthenticateOwner(): Boolean
    suspend fun authenticateOwner(reason: String): Boolean
}

/** The lock as the app sees it. */
data class LockState(
    val isEnabled: Boolean = false,
    /** The unlock screen is up. Only ever true while [isEnabled]. */
    val isLocked: Boolean = false,
    /** Her preference to unlock with biometrics (on by default); used only where a biometric exists. */
    val biometricUnlockEnabled: Boolean = true,
    val failedAttempts: Int = 0,
    val lockedOutUntil: Instant? = null,
)
