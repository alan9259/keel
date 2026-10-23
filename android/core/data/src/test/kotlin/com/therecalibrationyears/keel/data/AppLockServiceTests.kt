package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.domain.AppLockRules
import com.therecalibrationyears.keel.domain.BiometricAuthenticating
import com.therecalibrationyears.keel.domain.BiometricOutcome
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A fake biometric check, so the lock logic is testable without a device or a person. */
class FakeAuthenticator(
    var canAuth: Boolean = true,
    var result: BiometricOutcome = BiometricOutcome.UNLOCKED,
    var canOwner: Boolean = true,
    var ownerResult: Boolean = true,
) : BiometricAuthenticating {
    val reasons = mutableListOf<String>()
    override fun canAuthenticate() = canAuth
    override suspend fun authenticate(reason: String): BiometricOutcome { reasons += reason; return result }
    override fun canAuthenticateOwner() = canOwner
    override suspend fun authenticateOwner(reason: String): Boolean { reasons += reason; return ownerResult }
}

/** In-memory PIN store, in place of the Keystore. */
class InMemoryPINStore : PINSecureStore {
    var stored: Pair<ByteArray, ByteArray>? = null
    override suspend fun hasPIN() = stored != null
    override suspend fun save(salt: ByteArray, hash: ByteArray) { stored = salt to hash }
    override suspend fun load() = stored
    override suspend fun clear() { stored = null }
}

/** Port of KeelTests/AppLockServiceTests.swift (the pure grace and lockout rules are in core/domain). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLockServiceTests {
    @get:Rule val tmp = TemporaryFolder()
    private val clock = TestClock(Instant.parse("2026-10-23T10:00:00Z"))
    private val prefs by lazy { tempDataStore(tmp.root) }

    private fun service(store: InMemoryPINStore = InMemoryPINStore(), fake: FakeAuthenticator = FakeAuthenticator()) =
        AppLockService(store, fake, prefs, clock).also { runBlocking { it.load() } }

    /** A cold launch after a PIN "1234" was set: same store and preferences, a new instance. */
    private fun lockedService(fake: FakeAuthenticator = FakeAuthenticator()): AppLockService {
        val store = InMemoryPINStore()
        runBlocking { service(store, fake).setPIN("1234") }
        return service(store, fake)
    }

    private val AppLockService.now get() = state.value!!

    @Test fun disabledAndUnlockedByDefault() {
        val svc = service()
        assertFalse(svc.now.isEnabled)
        assertFalse(svc.now.isLocked)
    }

    @Test fun setPINEnablesAndUnlocks() = runBlocking {
        val svc = service()
        assertTrue(svc.setPIN("1234"))
        assertTrue(svc.now.isEnabled)
        assertFalse(svc.now.isLocked)
    }

    @Test fun setPINRejectsWrongLengthOrNonDigits() = runBlocking {
        val svc = service()
        assertFalse(svc.setPIN("123"))
        assertFalse(svc.setPIN("12345"))
        assertFalse(svc.setPIN("12a4"))
        assertFalse(svc.now.isEnabled)
    }

    @Test fun coldLaunchStartsLockedWhenPINSet() {
        val svc = lockedService()
        assertTrue(svc.now.isEnabled)
        assertTrue(svc.now.isLocked)
    }

    /** A PIN stored without the flag (an earlier install's leftovers on iOS) never locks, and is tidied away. */
    @Test fun stalePINWithoutEnabledFlagDoesNotLock() = runBlocking {
        val store = InMemoryPINStore().apply { stored = ByteArray(16) to ByteArray(32) }
        val svc = service(store)
        assertFalse(svc.now.isEnabled)
        assertFalse(svc.now.isLocked)
        assertFalse(store.hasPIN())
    }

    @Test fun verifyCorrectPINUnlocks() = runBlocking {
        val svc = lockedService()
        assertTrue(svc.verifyPIN("1234"))
        assertFalse(svc.now.isLocked)
    }

    @Test fun verifyWrongPINFailsAndStaysLocked() = runBlocking {
        val svc = lockedService()
        assertFalse(svc.verifyPIN("0000"))
        assertTrue(svc.now.isLocked)
        assertEquals(1, svc.now.failedAttempts)
    }

    @Test fun lockoutAfterMaxAttempts() = runBlocking {
        val svc = lockedService()
        repeat(AppLockRules.MAX_ATTEMPTS) { svc.verifyPIN("0000") }
        assertTrue(svc.isLockedOut())
        // Even the right PIN is refused during the lockout.
        assertFalse(svc.verifyPIN("1234"))
        assertTrue(svc.now.isLocked)
        // After it, the right PIN works.
        clock.advanceSeconds(61)
        assertFalse(svc.isLockedOut())
        assertTrue(svc.verifyPIN("1234"))
    }

    /** The count and the lockout survive a relaunch, so closing Keel doesn't reset them. */
    @Test fun aLockoutSurvivesARelaunch() = runBlocking {
        val store = InMemoryPINStore()
        val fake = FakeAuthenticator()
        service(store, fake).setPIN("1234")
        val first = service(store, fake)
        repeat(AppLockRules.MAX_ATTEMPTS) { first.verifyPIN("0000") }
        val relaunched = service(store, fake)
        assertTrue(relaunched.isLockedOut())
        assertEquals(5, relaunched.now.failedAttempts)
    }

    @Test fun biometricUnlock() = runBlocking {
        val fake = FakeAuthenticator()
        val svc = lockedService(fake)
        assertEquals(BiometricOutcome.UNLOCKED, svc.unlockWithBiometrics("Unlock Keel"))
        assertFalse(svc.now.isLocked)
        assertEquals(listOf("Unlock Keel"), fake.reasons)
    }

    @Test fun biometricUnlockFailsWhenUnavailable() = runBlocking {
        val svc = lockedService(FakeAuthenticator(canAuth = false))
        assertEquals(BiometricOutcome.CANCELLED, svc.unlockWithBiometrics("Unlock Keel"))
        assertTrue(svc.now.isLocked)
    }

    /** Android only: "Enter PIN instead" on the system prompt leaves Keel locked and asks for the PIN pad. */
    @Test fun choosingThePinOnThePromptStaysLocked() = runBlocking {
        val svc = lockedService(FakeAuthenticator(result = BiometricOutcome.USE_PIN))
        assertEquals(BiometricOutcome.USE_PIN, svc.unlockWithBiometrics("Unlock Keel"))
        assertTrue(svc.now.isLocked)
    }

    @Test fun biometricUnlockDefaultsOnAndReflectsAvailability() {
        val svc = service(fake = FakeAuthenticator(canAuth = true))
        assertTrue(svc.now.biometricUnlockEnabled)
        assertTrue(svc.useBiometrics())
    }

    @Test fun useBiometricsFalseWhenTurnedOff() = runBlocking {
        val svc = service()
        svc.setBiometricUnlock(false)
        assertFalse(svc.useBiometrics())
    }

    @Test fun useBiometricsFalseWhenNoBiometricAvailable() =
        assertFalse(service(fake = FakeAuthenticator(canAuth = false)).useBiometrics())

    @Test fun biometricUnlockRefusedWhenPreferenceOff() = runBlocking {
        val fake = FakeAuthenticator()
        val svc = lockedService(fake)
        svc.setBiometricUnlock(false)
        assertEquals(BiometricOutcome.CANCELLED, svc.unlockWithBiometrics("Unlock Keel"))
        assertTrue(svc.now.isLocked)
        assertTrue("never prompted", fake.reasons.isEmpty())
    }

    @Test fun canRecoverReflectsDeviceOwnerAuth() {
        assertTrue(service(fake = FakeAuthenticator(canOwner = true)).canRecoverWithScreenLock)
        assertFalse(service(fake = FakeAuthenticator(canOwner = false)).canRecoverWithScreenLock)
    }

    @Test fun authenticateOwnerSucceedsWithTheScreenLock() = runBlocking {
        assertTrue(service(fake = FakeAuthenticator(canOwner = true, ownerResult = true)).authenticateOwner("reason"))
    }

    @Test fun authenticateOwnerFailsWithoutDeviceAuth() = runBlocking {
        val fake = FakeAuthenticator(canOwner = false, ownerResult = true)
        assertFalse(service(fake = fake).authenticateOwner("reason"))
        assertTrue("never prompted", fake.reasons.isEmpty())
    }

    @Test fun disableClearsPIN() = runBlocking {
        val store = InMemoryPINStore()
        val svc = service(store)
        svc.setPIN("1234")
        svc.disable()
        assertFalse(svc.now.isEnabled)
        assertFalse(store.hasPIN())
        assertFalse(svc.now.isLocked)
        assertFalse(service(store).now.isEnabled) // and stays off after a relaunch
    }

    @Test fun quickReturnWithinGraceDoesNotLock() = runBlocking {
        val svc = service()
        svc.setPIN("1234")
        svc.markBackgrounded(clock.now())
        svc.applyForeground(clock.now().plusSeconds(10))
        assertFalse(svc.now.isLocked)
    }

    @Test fun returnAfterGraceLocks() = runBlocking {
        val svc = service()
        svc.setPIN("1234")
        svc.markBackgrounded(clock.now())
        svc.applyForeground(clock.now().plus(AppLockRules.GRACE).plusSeconds(1))
        assertTrue(svc.now.isLocked)
    }

    /** With the lock off, leaving and coming back never locks. */
    @Test fun nothingLocksWhileOff() {
        val svc = service()
        svc.markBackgrounded(clock.now())
        svc.applyForeground(clock.now().plusSeconds(600))
        assertFalse(svc.now.isLocked)
    }
}
