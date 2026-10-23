package com.therecalibrationyears.keel.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onLast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.therecalibrationyears.keel.designsystem.KeelThemeProvider
import com.therecalibrationyears.keel.domain.AppLockRules
import com.therecalibrationyears.keel.domain.BiometricOutcome
import com.therecalibrationyears.keel.domain.LockState
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The lock's rules in memory, as `AppLockService` keeps them. */
private class FakeLock(
    enabled: Boolean = false,
    override val biometricsAvailable: Boolean = false,
    override val canRecoverWithScreenLock: Boolean = true,
    var ownerProves: Boolean = true,
    var biometricOutcome: BiometricOutcome = BiometricOutcome.CANCELLED,
) : AppLockActions {
    override val state = MutableStateFlow(LockState(isEnabled = enabled, isLocked = enabled))
    var pin: String? = if (enabled) "1234" else null
    var time: Instant = Instant.parse("2026-10-23T10:00:00Z")
    var biometricAsks = 0
    override fun useBiometrics(state: LockState) = state.biometricUnlockEnabled && biometricsAvailable
    override fun now() = time
    override suspend fun setPIN(pin: String): Boolean {
        if (!AppLockRules.isValidPIN(pin)) return false
        this.pin = pin
        state.value = state.value.copy(isEnabled = true, isLocked = false, failedAttempts = 0, lockedOutUntil = null)
        return true
    }
    override suspend fun disable() { pin = null; state.value = LockState() }
    override suspend fun setBiometricUnlock(on: Boolean) { state.value = state.value.copy(biometricUnlockEnabled = on) }
    override fun isLockedOut() = state.value.lockedOutUntil?.let { time.isBefore(it) } ?: false
    override suspend fun verifyPIN(pin: String): Boolean {
        if (isLockedOut()) return false
        if (pin == this.pin) { state.value = state.value.copy(isLocked = false, failedAttempts = 0); return true }
        val attempts = state.value.failedAttempts + 1
        state.value = state.value.copy(failedAttempts = attempts, lockedOutUntil = AppLockRules.lockoutAfterFailure(attempts, time) ?: state.value.lockedOutUntil)
        return false
    }
    override suspend fun unlockWithBiometrics(): BiometricOutcome { biometricAsks++; return biometricOutcome }
    override suspend fun authenticateOwner() = ownerProves
}

/** App lock, PIN setup and the lock screen (iOS `AppLockSettingsView`, `PINSetupView`, `LockGate`), driven as she would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class AppLockScreensTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * Type on the top PIN pad (with "Forgot PIN?" the new PIN's pad sits over the lock's), then let
     * the screen settle: the check waits a moment for the last dot, on the test's own clock.
     */
    private fun type(pin: String) {
        pin.forEach { compose.onAllNodesWithText(it.toString()).onLast().performClick() }
        compose.waitForIdle()
    }

    private fun showGate(lock: FakeLock) = compose.setContent {
        KeelThemeProvider {
            val state by lock.state.collectAsStateWithLifecycle()
            if (state.isLocked) LockGate(lock, state)
        }
    }

    @Test fun turningItOnSetsAPinTwice() {
        val lock = FakeLock()
        compose.setContent { KeelThemeProvider { AppLockScreen(lock, onBack = {}) } }
        compose.onNodeWithText("Unlock with a 4-digit PIN").assertIsDisplayed() // no biometric here
        compose.onAllNodesWithText("App lock")[1].assertIsOff().performClick()
        compose.onNodeWithText("Create a PIN").assertIsDisplayed()
        type("2468")
        compose.onNodeWithText("Confirm your PIN").assertIsDisplayed()
        type("2468")
        assertEquals("2468", lock.pin)
        compose.onNodeWithText("Change PIN").assertIsDisplayed()
        compose.onNodeWithText("Use biometric unlock").assertDoesNotExist() // no biometric enrolled
    }

    @Test fun aMismatchStartsAgain() {
        val lock = FakeLock()
        compose.setContent { KeelThemeProvider { PINSetup(lock, onComplete = {}, onCancel = {}) } }
        type("1111")
        compose.waitUntil(3_000) { compose.onAllNodesWithText("Confirm your PIN").fetchSemanticsNodes().isNotEmpty() }
        type("2222")
        compose.waitUntil(3_000) { compose.onAllNodesWithText("Those didn't match. Let's try again.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Create a PIN").assertIsDisplayed()
        assertEquals(null, lock.pin)
    }

    @Test fun withABiometricSheCanChooseIt() {
        val lock = FakeLock(enabled = true, biometricsAvailable = true).apply { state.value = state.value.copy(isLocked = false) }
        compose.setContent { KeelThemeProvider { AppLockScreen(lock, onBack = {}) } }
        compose.onNodeWithText("Unlock with biometrics or a PIN").assertIsDisplayed()
        compose.onNodeWithText("Use biometric unlock").assertIsOn().performClick()
        compose.waitForIdle()
        assertFalse(lock.state.value.biometricUnlockEnabled)
    }

    @Test fun turningItOffForgetsThePin() {
        val lock = FakeLock(enabled = true).apply { state.value = state.value.copy(isLocked = false) }
        compose.setContent { KeelThemeProvider { AppLockScreen(lock, onBack = {}) } }
        compose.onAllNodesWithText("App lock")[1].assertIsOn().performClick()
        compose.waitForIdle()
        assertFalse(lock.state.value.isEnabled)
        assertEquals(null, lock.pin)
    }

    @Test fun theRightPinUnlocks() {
        val lock = FakeLock(enabled = true)
        showGate(lock)
        compose.onNodeWithText("Keel is locked").assertIsDisplayed()
        compose.onNodeWithText("Enter your PIN to unlock.").assertIsDisplayed()
        type("1234")
        assertFalse(lock.state.value.isLocked)
        compose.onNodeWithText("Keel is locked").assertDoesNotExist()
    }

    @Test fun aWrongPinSaysSoAndFiveStartALockout() {
        val lock = FakeLock(enabled = true)
        showGate(lock)
        type("0000")
        compose.onNodeWithText("Incorrect PIN. Try again.").assertIsDisplayed()
        compose.onNodeWithContentDescription("0 of 4 digits entered").assertExists()
        repeat(4) { type("0000") }
        assertEquals(5, lock.state.value.failedAttempts)
        compose.onNodeWithText("Too many attempts. Try again in 60s.").assertIsDisplayed()
        compose.onNodeWithText("Too many attempts.").assertIsDisplayed()
        // The pad is off during the lockout.
        compose.onNodeWithText("1").performClick()
        compose.onNodeWithContentDescription("0 of 4 digits entered").assertExists()
    }

    @Test fun biometricsLeadAndThePinIsOneTapAway() {
        val lock = FakeLock(enabled = true, biometricsAvailable = true)
        showGate(lock)
        compose.waitUntil(3_000) { lock.biometricAsks == 1 } // asked straight away
        compose.onNodeWithText("Unlock with biometrics.").assertIsDisplayed()
        compose.onNodeWithText("1").assertDoesNotExist()
        compose.onNodeWithText("Enter PIN instead").performClick()
        compose.onNodeWithText("Use biometrics, or enter your PIN.").assertIsDisplayed()
        compose.onNodeWithText("Use biometrics").performClick()
        compose.waitUntil(3_000) { lock.biometricAsks == 2 }
    }

    /** Android only: "Enter PIN instead" on the system prompt goes straight to Keel's pad, not a second ask. */
    @Test fun choosingThePinOnThePromptOpensThePad() {
        val lock = FakeLock(enabled = true, biometricsAvailable = true, biometricOutcome = BiometricOutcome.USE_PIN)
        showGate(lock)
        compose.waitUntil(3_000) { compose.onAllNodesWithText("Use biometrics, or enter your PIN.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1").assertIsDisplayed()
        compose.onNodeWithText("Enter PIN instead").assertDoesNotExist()
    }

    @Test fun forgotPinWithoutAScreenLockExplains() {
        val lock = FakeLock(enabled = true, canRecoverWithScreenLock = false)
        showGate(lock)
        compose.onNodeWithText("Forgot PIN?").performClick()
        compose.waitUntil(3_000) { compose.onAllNodesWithText("Reset your PIN").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Set a screen lock for your phone", substring = true).assertIsDisplayed()
    }

    /** She proved it's her phone: a new PIN unlocks; cancelling turns the lock off. Her data is untouched either way. */
    @Test fun forgotPinThenANewOne() {
        val lock = FakeLock(enabled = true)
        showGate(lock)
        compose.onNodeWithText("Forgot PIN?").performClick()
        compose.waitUntil(3_000) { compose.onAllNodesWithText("Create a PIN").fetchSemanticsNodes().isNotEmpty() }
        type("9753")
        compose.onNodeWithText("Confirm your PIN").assertIsDisplayed()
        type("9753")
        assertFalse(lock.state.value.isLocked)
        assertEquals("9753", lock.pin)
        assertTrue(lock.state.value.isEnabled)
    }

    @Test fun forgotPinThenCancelTurnsTheLockOff() {
        val lock = FakeLock(enabled = true)
        showGate(lock)
        compose.onNodeWithText("Forgot PIN?").performClick()
        compose.waitUntil(3_000) { compose.onAllNodesWithText("Create a PIN").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(3_000) { !lock.state.value.isEnabled }
        assertFalse(lock.state.value.isLocked)
    }
}
