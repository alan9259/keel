package com.therecalibrationyears.keel.medications

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.designsystem.KeelThemeProvider
import com.therecalibrationyears.keel.domain.DoseSchedule
import com.therecalibrationyears.keel.domain.DoseUnit
import com.therecalibrationyears.keel.domain.TreatmentCatalog
import com.therecalibrationyears.keel.domain.TreatmentDraft
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeActions : MedicationsActions {
    override val active = MutableStateFlow<List<MedicationEntity>>(emptyList())
    override val stopped = MutableStateFlow<List<MedicationEntity>>(emptyList())
    override val catalog = TreatmentCatalog(
        1,
        listOf(
            TreatmentCatalog.Group("oestrogen", "treatment", "Oestrogen", "Oestrogen", defaultMethodRaw = "patch", items = listOf(TreatmentCatalog.Item("Oestrogen gel", methodRaw = "gel"))),
            TreatmentCatalog.Group("common", "supplement", "Supplements", "Common", items = listOf(TreatmentCatalog.Item("Magnesium"))),
        ),
    )
    override val zone: ZoneId = ZoneOffset.UTC
    override fun now(): Instant = Instant.parse("2026-10-21T10:00:00Z")
    val added = mutableListOf<TreatmentDraft>()
    override suspend fun add(draft: TreatmentDraft) { added += draft }
    override suspend fun update(id: String, draft: TreatmentDraft) = Unit
    override suspend fun remove(id: String) = Unit
    override suspend fun setTracked(id: String, tracked: Boolean) = Unit
}

/** The Medications screen and the add flow, driven as she would (Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class MedicationsScreenTests {
    @get:Rule val compose = createComposeRule()

    private fun show(actions: FakeActions) = compose.setContent {
        KeelThemeProvider { MedicationsScreen(actions, onBack = {}) }
    }

    @Test fun emptyStateIsHonest() {
        show(FakeActions())
        compose.onNodeWithText("Nothing here yet").assertIsDisplayed()
    }

    @Test fun pickingFromTheListAndSavingAddsItWithNothingPrefilled() {
        val actions = FakeActions()
        show(actions)
        compose.onNodeWithText("Add Medication or Supplement").performScrollTo().performClick()
        compose.onNodeWithText("What are you taking?").assertIsDisplayed()
        compose.onNodeWithText("Oestrogen gel").performClick()
        compose.onNodeWithText("Add details").assertIsDisplayed()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitForIdle()
        val draft = actions.added.single()
        assertEquals("Oestrogen gel", draft.name)
        assertNull(draft.doseAmount)
        assertNull(draft.doseUnit)
        assertEquals(DoseSchedule.Kind.WEEKLY, draft.schedule.kind)
    }

    @Test fun searchingForSomethingNotListedAddsItAsHerOwn() {
        val actions = FakeActions()
        show(actions)
        compose.onNodeWithText("Add Medication or Supplement").performScrollTo().performClick()
        compose.onNodeWithText("Supplements").performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Black cohosh")
        compose.onNodeWithText("Add “Black cohosh”").performClick()
        compose.onNodeWithText("Black cohosh").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("2")
        compose.onNodeWithContentDescription("Unit").performClick()
        compose.onAllNodesWithText("pumps").onFirst().performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitForIdle()
        val draft = actions.added.single()
        assertEquals("Black cohosh", draft.name)
        assertNull(draft.catalogGroupID)
        assertEquals(2.0, draft.doseAmount!!, 0.0)
        assertEquals(DoseUnit.PUMPS, draft.doseUnit)
        assertEquals("2 pumps", draft.doseText)
    }
}
