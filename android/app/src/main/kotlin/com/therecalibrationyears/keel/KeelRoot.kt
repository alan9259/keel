package com.therecalibrationyears.keel

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.therecalibrationyears.keel.domain.RootRouting
import com.therecalibrationyears.keel.onboarding.OnboardingFlow
import com.therecalibrationyears.keel.onboarding.OnboardingStep
import kotlinx.coroutines.launch

/** Decides between onboarding and the main app (port of iOS `RootView`). */
@Composable
fun KeelRoot(controller: KeelAppController) {
    val onboarded by controller.hasCompletedOnboarding.collectAsStateWithLifecycle()
    // Not read yet: the launch screen is still up.
    val hasOnboarded = onboarded ?: return
    // She finished onboarding in this session. Kept apart from the saved record so that
    // finishing always enters the app, even under the force-onboarding debug flag.
    var completedThisLaunch by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

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
