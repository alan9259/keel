package com.therecalibrationyears.keel

import android.content.Intent
import androidx.compose.runtime.Composable
import com.therecalibrationyears.keel.designsystem.ColourMode
import com.therecalibrationyears.keel.onboarding.OnboardingStep

/** Release builds have no debug hooks: every override is off. */
object DebugHarness {
    val themeId: String? = null
    val colourMode: ColourMode? = null
    const val forcedOnboarded = false
    const val forceOnboarding = false
    val onboardingStartStep: OnboardingStep? = null

    @Suppress("UNUSED_PARAMETER")
    fun apply(intent: Intent) = Unit

    @Composable
    fun content(): Boolean = false
}
