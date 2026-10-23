package com.therecalibrationyears.keel

import android.content.Intent
import com.therecalibrationyears.keel.gpsummary.GPSummaryStep
import com.therecalibrationyears.keel.domain.ActivityPeriod
import com.therecalibrationyears.keel.domain.KeelClock
import java.time.LocalDate
import androidx.compose.runtime.Composable
import com.therecalibrationyears.keel.designsystem.ColourMode
import com.therecalibrationyears.keel.onboarding.OnboardingStep

/** Release builds have no debug hooks: every override is off. */
object DebugHarness {
    val themeId: String? = null
    val colourMode: ColourMode? = null
    const val forcedOnboarded = false
    const val forceOnboarding = false
    const val forceHealthOffer = false
    val onboardingStartStep: OnboardingStep? = null
    val initialRoute: MainRoute? = null
    val activitiesPeriod: ActivityPeriod? = null
    val gpInitialStep: GPSummaryStep? = null

    @Suppress("UNUSED_PARAMETER")
    fun cycleSheetDay(clock: KeelClock): LocalDate? = null

    @Suppress("UNUSED_PARAMETER")
    suspend fun seed(container: AppContainer) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun apply(intent: Intent) = Unit

    @Composable
    fun content(): Boolean = false
}
