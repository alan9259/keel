package com.therecalibrationyears.keel

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.domain.RootRouting
import com.therecalibrationyears.keel.onboarding.OnboardingFlow
import com.therecalibrationyears.keel.onboarding.OnboardingStep
import com.therecalibrationyears.keel.settings.LockGate
import com.therecalibrationyears.keel.settings.PrivacyCover
import kotlinx.coroutines.launch

/** The widest Keel's screens grow: a large phone's width. */
private val MAX_CONTENT_WIDTH = 600.dp

/**
 * On a tablet or unfolded phone, Keel keeps a phone-width column in the middle, as the
 * iPhone-only iOS app does on an iPad: Android 16 ignores the portrait lock on large screens,
 * and lines a whole tablet wide are hard to read. On a phone it changes nothing.
 */
@Composable
internal fun PhoneWidth(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(LocalKeelTheme.current.background), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxSize(), content = content)
    }
}

/** Decides between onboarding and the main app (port of iOS `RootView`). */
@Composable
fun KeelRoot(controller: KeelAppController, inFront: Boolean = true) {
    val onboarded by controller.hasCompletedOnboarding.collectAsStateWithLifecycle()
    val lock by controller.lock.state.collectAsStateWithLifecycle()
    // Not read yet: the launch screen is still up.
    val hasOnboarded = onboarded ?: return
    // She finished onboarding in this session. Kept apart from the saved record so that
    // finishing always enters the app, even under the force-onboarding debug flag.
    var completedThisLaunch by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    PhoneWidth {
    // Tapping outside a text field puts the keyboard away (iOS dismissesKeyboardOnTapOutside).
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }) {
        val showMain = RootRouting.shouldShowMain(
            completedThisLaunch = completedThisLaunch,
            hasOnboarded = hasOnboarded,
            forcedOnboarded = DebugHarness.forcedOnboarded,
            forceOnboarding = DebugHarness.forceOnboarding,
        )
        if (showMain) {
            MainShell(controller)
        } else {
            OnboardingFlow(
                actions = controller,
                startStep = DebugHarness.onboardingStartStep ?: OnboardingStep.WELCOME,
                onComplete = {
                    scope.launch {
                        controller.markOnboarded()
                        completedThisLaunch = true
                        controller.completeOnboarding()
                    }
                },
            )
        }
    }
    }
    // Keel isn't in front (the app switcher, a system prompt): cover it while the lock is on.
    lock?.let { if (it.isEnabled && !it.isLocked && !inFront) PrivacyCover(controller.lock.useBiometrics(it)) }
    // Above everything (its own window), so nothing behind it shows until she unlocks (iOS LockGate).
    lock?.takeIf { it.isLocked }?.let { LockGate(controller.lockActions, it) }
}
