package com.therecalibrationyears.keel.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.therecalibrationyears.keel.designsystem.KeelThemeProvider
import com.therecalibrationyears.keel.domain.Pathway
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The onboarding flow, driven through its real screens (iOS covers these by hand and in UI runs). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi") // a Pixel 8-sized phone
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingFlowTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class RecordingActions : OnboardingActions {
        val names = mutableListOf<String>()
        val pathways = mutableListOf<Pathway>()
        var healthRequests = 0
        override suspend fun saveFirstName(name: String) { names += name }
        override suspend fun setPathway(pathway: Pathway) { pathways += pathway }
        override suspend fun connectHealth(): Boolean { healthRequests++; return false }
    }

    private val actions = RecordingActions()
    private var completions = 0

    private fun show(start: OnboardingStep = OnboardingStep.WELCOME) = compose.setContent {
        KeelThemeProvider { OnboardingFlow(actions, onComplete = { completions++ }, startStep = start) }
    }

    private fun see(text: String) = compose.onNodeWithText(text).assertExists()
    private fun tap(text: String) { compose.onNodeWithText(text).performClick(); compose.waitForIdle() }

    @Test fun walksTheSixScreensInOrderAndSavesHerAnswers() {
        show()
        tap("Let's begin")
        see("You're in the right place.")
        tap("Continue")
        see("Let's set you up")
        compose.onNode(hasSetTextAction()).performTextInput("  Mischa  ")
        tap("Continue")
        see("How are you managing things at the moment?")
        tap("Working with a doctor")
        tap("Continue")
        see("Bring in what you already record.")
        tap("Continue")
        see("Let's start your record.")
        tap("Start with Keel")

        assertEquals(listOf("Mischa"), actions.names) // trimmed
        assertEquals(listOf(Pathway.MEDICAL), actions.pathways)
        assertEquals(1, actions.healthRequests)
        assertEquals(1, completions)
    }

    /** First name is optional: a blank one is passed on as blank (the app calls her "there"). */
    @Test fun aBlankNameStillMovesOn() {
        show(OnboardingStep.CREATE_ACCOUNT)
        tap("Continue")
        see("How are you managing things at the moment?")
        assertEquals(listOf(""), actions.names)
    }

    /** She taps to choose: nothing is pre-selected, and Continue waits for her answer. */
    @Test fun noPathwayIsPreselectedAndContinueWaits() {
        show(OnboardingStep.PATHWAY)
        Pathway.entries.forEach { compose.onNodeWithText(it.title).assertIsNotSelected() }
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        tap("Still working it out")
        compose.onNodeWithText("Continue").assertIsEnabled()
    }

    /** App Review 5.1.1(iv), applied here: before the permission request there is only Continue. */
    @Test fun theHealthStepOffersOnlyContinue() {
        show(OnboardingStep.HEALTH)
        compose.onAllNodesWithText("Continue").assertCountEquals(1)
        listOf("Skip", "Skip for now", "Not now", "Maybe later").forEach {
            compose.onAllNodesWithText(it, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }

    /** Regression guard: a quick double tap must not skip the next screen. */
    @Test fun aDoubleTapMovesOnlyOneScreen() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Let's begin").performClick()
        compose.onNodeWithText("Let's begin").performClick() // still on screen mid-transition
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        see("You're in the right place.")
        compose.onAllNodesWithText("Let's set you up").assertCountEquals(0)
    }

    @Test fun startWithKeelCompletesOnce() {
        show(OnboardingStep.READY)
        compose.onNodeWithText("Start with Keel").performClick()
        compose.onNodeWithText("Start with Keel").performClick()
        compose.waitForIdle()
        assertEquals(1, completions)
    }

    /** Android's back gesture returns to the previous screen. */
    @Test fun backReturnsToThePreviousScreen() {
        show(OnboardingStep.PATHWAY)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        see("Let's set you up")
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals(n: Int) {
    assertEquals(n, fetchSemanticsNodes().size)
}
