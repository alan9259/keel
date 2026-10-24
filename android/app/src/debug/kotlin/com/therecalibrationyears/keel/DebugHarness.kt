package com.therecalibrationyears.keel

import android.content.Intent
import com.therecalibrationyears.keel.gpsummary.GPSummaryStep
import com.therecalibrationyears.keel.domain.ActivityPeriod
import com.therecalibrationyears.keel.domain.KeelClock
import java.time.LocalDate
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
 *   uitRoute <route>         push cycle, medications, patterns, more, gp_summary or reminders
 *   uitReset true            soft-delete check-ins and remove medicines first
 *   uitSeedCheckIn / uitSeedMed / uitSeedEnergyMix / uitSeedWeek true   sample records
 *   uitReminderExplainer true  queue the "Gentle reminders" explanation (iOS -uitReminderExplainer)
 *   uitRemindSoon true       a medicine due in 2 minutes and the check-in in 3, to watch them fire
 *   uitGPStep <step>         the GP Visit Summary opens at review, details or preview (iOS -uitGPReview etc.)
 *   uitGPSummary true        seed a 12-week picture and write its GP summary PDF to files/keel-gp-summary.pdf,
 *                            logging KEEL_GPSUMMARY lines (iOS -uitGPSummary)
 *   uitHealthOffer true      Home offers "Connect to Health Connect?" (iOS -uitHealthOffer)
 *   uitHealthSeed true       write a known week into Health Connect (DebugHealth lists the values)
 *   uitHealthProbe true      log what Keel reads from Health Connect as KEEL_HEALTH lines
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
    /** `--es uitActivitiesPeriod week` (or month): Activities opens on that view. */
    var activitiesPeriod: ActivityPeriod? = null
        private set

    /** `--es uitGPStep review` (or details, preview): the GP Visit Summary opens at that step. */
    var gpInitialStep: GPSummaryStep? = null
        private set

    /** `--ez uitHealthOffer true`: Home offers "Connect to Health Connect?" whatever the state (iOS -uitHealthOffer). */
    var forceHealthOffer: Boolean = false
        private set

    /** `--ez uitCycleSheet true`: the Cycle screen opens the log sheet for two days ago. */
    private var cycleSheet = false

    fun cycleSheetDay(clock: KeelClock): LocalDate? =
        if (cycleSheet) clock.now().atZone(clock.zone()).toLocalDate().minusDays(2) else null

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
        cycleSheet = intent.getBooleanExtra("uitCycleSheet", false)
        forceHealthOffer = intent.getBooleanExtra("uitHealthOffer", false)
        gpInitialStep = intent.getStringExtra("uitGPStep")?.let { raw -> GPSummaryStep.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
        activitiesPeriod = intent.getStringExtra("uitActivitiesPeriod")?.let { raw -> ActivityPeriod.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
        seeds = listOf("uitReset", "uitSeedMed", "uitSeedCheckIn", "uitSeedEnergyMix", "uitSeedWeek", "uitReminderExplainer", "uitExactTimingAgain", "uitRemindSoon", "uitRemindSoonAutoLog", "uitTopUpNow", "uitSeedCycle", "uitGPSummary", "uitHealthSeed", "uitHealthProbe")
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
                "uitReminderExplainer" -> container.settings.setNotificationExplainerPending(true)
                "uitExactTimingAgain" -> container.settings.setExactTimingOffered(false) // "Reminders on time" can show again
                "uitRemindSoon" -> s.remindSoon()
                "uitRemindSoonAutoLog" -> s.remindSoonAutoLog()
                // The daily reminder top-up, once, now (WorkManager won't run the daily job early).
                "uitTopUpNow" -> androidx.work.WorkManager.getInstance(container.appContext)
                    .enqueue(androidx.work.OneTimeWorkRequestBuilder<ReminderTopUpWorker>().build())
                "uitSeedCycle" -> s.cycle()
                "uitGPSummary" -> s.gpSummaryProbe()
                "uitHealthSeed" -> DebugHealth(container).seed()
                "uitHealthProbe" -> DebugHealth(container).probe()
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
