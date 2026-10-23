package com.therecalibrationyears.keel.platform

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.therecalibrationyears.keel.domain.BiometricAuthenticating
import com.therecalibrationyears.keel.domain.BiometricOutcome
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * The app lock's checks on Android (plan §1.3): the biometric prompt for the quick unlock
 * (`BIOMETRIC_WEAK`, so face unlock counts as it does on iOS), and the phone's own screen lock
 * for the forgot-PIN recovery (`BIOMETRIC_WEAK or DEVICE_CREDENTIAL`, supported from Android 9).
 * The prompt needs the activity on screen, which registers itself with [attach].
 */
class SystemBiometricAuthenticator(
    private val context: Context,
    /** The negative button on the biometric prompt (iOS "Enter PIN instead"). */
    private val usePINLabel: String,
) : BiometricAuthenticating {
    private var activity = WeakReference<FragmentActivity>(null)

    fun attach(activity: FragmentActivity) { this.activity = WeakReference(activity) }

    private val manager get() = BiometricManager.from(context)

    override fun canAuthenticate(): Boolean = manager.canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    override suspend fun authenticate(reason: String): BiometricOutcome = prompt(
        BiometricPrompt.PromptInfo.Builder().setTitle(reason).setAllowedAuthenticators(BIOMETRIC_WEAK).setNegativeButtonText(usePINLabel).build(),
    )

    override fun canAuthenticateOwner(): Boolean =
        canAuthenticate() || context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    override suspend fun authenticateOwner(reason: String): Boolean = prompt(
        BiometricPrompt.PromptInfo.Builder().setTitle(reason).setAllowedAuthenticators(OWNER).build(),
    ) == BiometricOutcome.UNLOCKED

    private suspend fun prompt(info: BiometricPrompt.PromptInfo): BiometricOutcome = withContext(Dispatchers.Main) {
        val host = activity.get() ?: return@withContext BiometricOutcome.CANCELLED
        suspendCancellableCoroutine { done ->
            val prompt = BiometricPrompt(
                host, ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (done.isActive) done.resume(BiometricOutcome.UNLOCKED)
                    }
                    // A failed match lets her try again on the same prompt; only an error ends it.
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        val outcome = if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) BiometricOutcome.USE_PIN else BiometricOutcome.CANCELLED
                        if (done.isActive) done.resume(outcome)
                    }
                },
            )
            done.invokeOnCancellation { prompt.cancelAuthentication() }
            prompt.authenticate(info)
        }
    }

    private companion object {
        /** Android 9 and 10 support device credential only alongside a biometric (androidx.biometric). */
        val OWNER = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }
}
