package com.therecalibrationyears.keel.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.domain.Pathway

/** The six onboarding screens, in order (iOS `OnboardingFlow.Step`). */
enum class OnboardingStep { WELCOME, RIGHT_PLACE, CREATE_ACCOUNT, PATHWAY, HEALTH, READY }

/** What onboarding needs from the app, so this module never touches storage itself. */
interface OnboardingActions {
    /** Establish her local identity and save her first name (blank: Keel calls her "there"). */
    suspend fun saveFirstName(name: String)

    suspend fun setPathway(pathway: Pathway)

    /** Ask for read access to her health data; returns whether she is now connected. */
    suspend fun connectHealth(): Boolean
}

/**
 * Coordinates the onboarding screens (port of iOS `OnboardingFlow`): each slides in from
 * the trailing edge. Android's back gesture returns to the previous screen.
 */
@Composable
fun OnboardingFlow(
    actions: OnboardingActions,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    startStep: OnboardingStep = OnboardingStep.WELCOME,
) {
    val theme = LocalKeelTheme.current
    var step by rememberSaveable { mutableStateOf(startStep) }
    var completed by rememberSaveable { mutableStateOf(false) }

    // Moves on from `from` only if that is still the current screen, so a double tap (or a
    // slow save finishing after she went back) can never skip a screen.
    fun advance(from: OnboardingStep) {
        if (step != from) return
        val next = OnboardingStep.entries.getOrNull(from.ordinal + 1)
        if (next != null) {
            step = next
        } else if (!completed) {
            completed = true
            onComplete()
        }
    }

    BackHandler(enabled = step != OnboardingStep.WELCOME) {
        step = OnboardingStep.entries[step.ordinal - 1]
    }

    AnimatedContent(
        targetState = step,
        modifier = modifier.fillMaxSize().background(theme.background),
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val time = tween<Float>(300)
            (slideInHorizontally(tween(300)) { if (forward) it else -it } + fadeIn(time))
                .togetherWith(slideOutHorizontally(tween(300)) { if (forward) -it else it } + fadeOut(time))
        },
        label = "onboardingStep",
    ) { current ->
        when (current) {
            OnboardingStep.WELCOME -> WelcomeScreen { advance(current) }
            OnboardingStep.RIGHT_PLACE -> RightPlaceScreen { advance(current) }
            OnboardingStep.CREATE_ACCOUNT -> CreateAccountScreen(actions::saveFirstName) { advance(current) }
            OnboardingStep.PATHWAY -> PathwayScreen(actions::setPathway) { advance(current) }
            OnboardingStep.HEALTH -> HealthScreen(actions::connectHealth) { advance(current) }
            OnboardingStep.READY -> ReadyScreen { advance(current) }
        }
    }
}
