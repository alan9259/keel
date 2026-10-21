package com.therecalibrationyears.keel.gpsummary

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.therecalibrationyears.keel.designsystem.KeelThemeProvider
import com.therecalibrationyears.keel.designsystem.LocalGoHome
import com.therecalibrationyears.keel.domain.GPAboutMe
import com.therecalibrationyears.keel.domain.GPMedCategory
import com.therecalibrationyears.keel.domain.GPPeriod
import com.therecalibrationyears.keel.domain.GPReviewMed
import com.therecalibrationyears.keel.domain.GPSummaryDocument
import com.therecalibrationyears.keel.domain.GPSummaryInputs
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeGPSummary(
    private val about: GPAboutMe = GPAboutMe(hasName = true),
    private val stats: List<com.therecalibrationyears.keel.domain.GPSymptomStat> = listOf(stat("Hot flushes", 9), stat("Joint pain", 2)),
) : GPSummaryActions {
    override val zone: ZoneId = ZoneOffset.UTC
    override fun now(): Instant = Instant.parse("2026-10-21T09:00:00Z")
    val asked = mutableListOf<GPSummaryInputs>()
    override suspend fun document(inputs: GPSummaryInputs): GPSummaryDocument {
        asked += inputs
        return sampleDocument(stats, checkInDays = 12)
    }
    override suspend fun reviewMeds() = listOf(GPReviewMed("M1", "Oestrogel", GPMedCategory.MHT))
    override suspend fun aboutMe() = about
}

/** Renders nothing real (PdfDocument is native; the renderer is checked on a device) and records shares. */
private class FakeOutput : GPSummaryOutput {
    val shared = mutableListOf<String>()
    override suspend fun render(document: GPSummaryDocument) = byteArrayOf(1, 2, 3)
    override suspend fun pages(pdf: ByteArray, widthPx: Int): List<ImageBitmap> = emptyList()
    override fun share(pdf: ByteArray, fileName: String) { shared += fileName }
}

/** The GP Visit Summary flow (iOS `GPSummaryFlowView`), driven as she would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class GPSummaryScreenTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var left = 0
    private var wentHome = 0

    private fun show(actions: FakeGPSummary = FakeGPSummary(), output: FakeOutput = FakeOutput(), step: GPSummaryStep = GPSummaryStep.PERIOD) {
        compose.setContent {
            KeelThemeProvider {
                CompositionLocalProvider(LocalGoHome provides { wentHome++ }) {
                    GPSummaryScreen(actions, onBack = { left++ }, initialStep = step, output = output)
                }
            }
        }
    }

    private fun systemBack() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun theFourStepsInOrderThenSharingTheDatedPdf() {
        val output = FakeOutput()
        show(output = output)
        compose.onNodeWithText("Choose a time period").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Review what you recorded").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Add what matters to you").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Preview").assertIsDisplayed()
        compose.onNodeWithText("This is exactly what will be shared.").assertIsDisplayed()
        compose.onNodeWithText("Generate and share").performClick()
        compose.waitForIdle()
        assertEquals(listOf("GP-Visit-Summary-2026-10-21.pdf"), output.shared)
    }

    @Test fun backStepsBackOnePageAndLeavesFromTheFirst() {
        show(step = GPSummaryStep.DETAILS)
        systemBack()
        compose.onNodeWithText("Review what you recorded").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Choose a time period").assertIsDisplayed()
        assertEquals(0, left)
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, left)
    }

    @Test fun homeAsksBeforeDiscardingPastTheFirstStep() {
        show()
        compose.onNodeWithContentDescription("Home").performClick()
        assertEquals(1, wentHome) // nothing chosen yet: straight home
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithText("Leave your GP summary?").assertIsDisplayed()
        compose.onNodeWithText("What you've added so far won't be kept.").assertIsDisplayed()
        compose.onNodeWithText("Keep going").performClick()
        assertEquals(1, wentHome)
        compose.onNodeWithText("Review what you recorded").assertIsDisplayed()
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithText("Leave").performClick()
        assertEquals(2, wentHome)
    }

    @Test fun aRemovedRowIsLeftOffWhatIsShared() {
        val actions = FakeGPSummary()
        show(actions, step = GPSummaryStep.REVIEW)
        compose.onNodeWithText("Treatments and supplements").assertIsDisplayed()
        val row = compose.onNodeWithContentDescription("Hot flushes")
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Included"))
        row.performClick()
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Removed"))
        compose.onNodeWithContentDescription("Oestrogel").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()
        val last = actions.asked.last()
        assertEquals(setOf("Hot flushes"), last.removedSymptomNames)
        assertEquals(setOf("M1"), last.removedMedIDs)
    }

    @Test fun herWordsAreHeldToTheLimitAndAboutMeOffersOnlyWhatSheRecorded() {
        show(step = GPSummaryStep.DETAILS)
        compose.onNodeWithContentDescription("Priority 1").performTextInput("a".repeat(130))
        compose.onNodeWithContentDescription("Priority 1").assertTextEquals("a".repeat(100))
        compose.onNodeWithText("Include my name").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Include my age").assertCountEquals(0)
        compose.onAllNodesWithText("Include my hysterectomy answer").assertCountEquals(0)
    }

    /** Regression: the details step opened at the review step's scroll position, below the priority fields. */
    @Test fun eachStepStartsAtItsTop() {
        show(FakeGPSummary(stats = (1..12).map { stat("Symptom $it", 13 - it) }), step = GPSummaryStep.REVIEW) // long enough to scroll
        compose.onNodeWithText("Your period dates and check-in counts are always included.").performScrollTo()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("What I most want help with today").assertIsDisplayed()
        compose.onNodeWithContentDescription("Priority 1").assertIsDisplayed()
    }

    @Test fun aCustomRangeShowsHerTwoDates() {
        val actions = FakeGPSummary()
        show(actions)
        compose.onNodeWithText("Custom range").performClick()
        compose.onNodeWithText("From").assertIsDisplayed()
        compose.onNodeWithText("To").assertIsDisplayed()
        compose.waitForIdle()
        assertTrue(actions.asked.any { it.period is GPPeriod.Custom })
    }
}
