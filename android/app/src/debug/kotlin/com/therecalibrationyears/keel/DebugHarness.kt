package com.therecalibrationyears.keel

import android.content.Intent
import androidx.compose.runtime.Composable
import com.therecalibrationyears.keel.designsystem.ColourMode
import com.therecalibrationyears.keel.onboarding.OnboardingStep

/**
 * Debug-only launch hooks, the Android counterpart of the iOS `-uit*` launch arguments
 * (Keel/App/DebugHarness.swift). Drive them with intent extras, for example:
 *
 *   adb shell am start -n com.therecalibrationyears.keel/.MainActivity \
 *     --es uitScreen gallery --es uitTheme slate --ez uitDark true --ez uitScrollToBottom true
 *
 *   uitOnboarded true        skip onboarding, land in the app (iOS -uitOnboarded)
 *   uitForceOnboarding true  show onboarding even when already onboarded (iOS -uitForceOnboarding)
 *   uitOnboardStep <step>    start onboarding at welcome, right_place, create_account,
 *                            pathway, health or ready (iOS -uitOnboardPathway etc.)
 *   uitRoute <route>         push cycle, medications, patterns, more or gp_summary
 *   uitReset true            soft-delete check-ins and remove medicines first
 *   uitSeedCheckIn / uitSeedMed / uitSeedEnergyMix / uitSeedWeek true   sample records
 *
 * Never compiled into release builds (see src/release).
 */
object DebugHarness {
    var themeId: String? = null
        private set
    var colourMode: ColourMode? = null
        private set
    var screen: String? = null
        private set
    var scrollToBottom: Boolean = false
        private set
    var forcedOnboarded: Boolean = false
        private set
    var forceOnboarding: Boolean = false
        private set
    var onboardingStartStep: OnboardingStep? = null
        private set
    var initialRoute: MainRoute? = null
        private set
    private var seeds: List<String> = emptyList()

    fun apply(intent: Intent) {
        themeId = intent.getStringExtra("uitTheme")
        colourMode = when {
            intent.hasExtra("uitDark") -> if (intent.getBooleanExtra("uitDark", false)) ColourMode.DARK else ColourMode.LIGHT
            else -> null
        }
        screen = intent.getStringExtra("uitScreen")
        scrollToBottom = intent.getBooleanExtra("uitScrollToBottom", false)
        forcedOnboarded = intent.getBooleanExtra("uitOnboarded", false)
        forceOnboarding = intent.getBooleanExtra("uitForceOnboarding", false)
        onboardingStartStep = intent.getStringExtra("uitOnboardStep")?.let { raw ->
            OnboardingStep.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        }
        initialRoute = MainRoute.fromDebugName(intent.getStringExtra("uitRoute"))
        seeds = listOf("uitReset", "uitSeedMed", "uitSeedCheckIn", "uitSeedEnergyMix", "uitSeedWeek")
            .filter { intent.getBooleanExtra(it, false) }
    }

    /** Writes the requested sample records (in this order: reset, medicines, then entries). */
    suspend fun seed(container: AppContainer) {
        val s = DebugSeeds(container)
        seeds.forEach {
            when (it) {
                "uitReset" -> s.reset()
                "uitSeedMed" -> s.medications()
                "uitSeedCheckIn" -> s.checkIn()
                "uitSeedEnergyMix" -> s.energyMix()
                "uitSeedWeek" -> s.week()
            }
        }
        seeds = emptyList() // once per launch, not again on recreation
    }

    /** Draws a debug screen if one was requested; returns whether it did. */
    @Composable
    fun content(): Boolean = when (screen) {
        "gallery" -> { ComponentGallery(scrollToBottom); true }
        else -> false
    }
}
