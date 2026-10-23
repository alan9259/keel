package com.therecalibrationyears.keel.checkin

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.therecalibrationyears.keel.data.db.SymptomEntity
import com.therecalibrationyears.keel.designsystem.KeelThemeProvider
import com.therecalibrationyears.keel.domain.EnergyLevel
import com.therecalibrationyears.keel.domain.Mood
import com.therecalibrationyears.keel.domain.MoodPacks
import com.therecalibrationyears.keel.domain.SymptomCatalog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The check-in screen and the "more" picker, driven through the real composables. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CheckInScreenTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 10, 21)
    private val catalog = SymptomCatalog.builtIn.mapIndexed { i, e ->
        SymptomEntity("S$i", e.name, e.category.raw, isDefaultChip = e.isDefault, ownerID = "", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
    }

    private inner class Fake(var draft: CheckInDraft = CheckInDraft(), alcohol: Boolean = true) : CheckInActions {
        val symptomsFlow = MutableStateFlow(catalog)
        val sensitive = MutableStateFlow(false)
        var saved: Pair<CheckInRequest, CheckInDraft>? = null
        var deleted: String? = null
        val added = mutableListOf<Pair<String, String>>()
        val archived = mutableListOf<String>()
        override val activeSymptoms: Flow<List<SymptomEntity>> = symptomsFlow
        override val notesAlcohol: Flow<Boolean> = MutableStateFlow(alcohol)
        override suspend fun quickChipIds() = SymptomCatalog.defaultOrder.map { n -> catalog.first { it.name == n }.id }
        override suspend fun loadDraft(request: CheckInRequest) = draft
        override suspend fun save(request: CheckInRequest, draft: CheckInDraft) { saved = request to draft }
        override suspend fun delete(checkInId: String) { deleted = checkInId }
        override val voice = com.therecalibrationyears.keel.platform.SpeechRecognitionService(org.robolectric.RuntimeEnvironment.getApplication())
        override val picker = object : SymptomPickerActions {
            override val showsSensitive: Flow<Boolean> = sensitive
            override suspend fun showSensitive() { sensitive.value = true }
            override suspend fun addCustom(name: String, categoryRaw: String) { added += name to categoryRaw }
            override suspend fun archive(symptom: SymptomEntity) { archived += symptom.id }
            override suspend fun rename(symptom: SymptomEntity, name: String) = Unit
            override suspend fun move(symptom: SymptomEntity, categoryRaw: String) = Unit
        }
    }

    private var closed: Boolean? = null
    private var deletedShown = false

    private fun show(fake: Fake, request: CheckInRequest = CheckInRequest(Mood.GOOD, today)) = compose.setContent {
        KeelThemeProvider {
            CheckInScreen(request, today, ZoneId.of("Australia/Sydney"), MoodPacks.pack(null), fake,
                onClose = { closed = it }, onChangeMood = {}, onDeleted = { deletedShown = true })
        }
    }

    private fun tap(text: String) { compose.onNodeWithText(text).performScrollTo().performClick(); compose.waitForIdle() }

    @Test fun aNewEntrySavesWhatSheFilledIn() {
        val fake = Fake().also { show(it) }
        compose.onNodeWithText("New entry").assertIsDisplayed()
        compose.onNodeWithText("Today").assertIsDisplayed()
        tap("Charged")
        tap("Add hours")             // 7.5
        compose.onNodeWithContentDescription("One more").assertDoesNotExist() // alcohol not started yet
        compose.onAllNodesWithText("Clear").fetchSemanticsNodes().size.let { assertEquals(1, it) }
        tap("Add a number")          // 0
        compose.onNodeWithContentDescription("One more").performScrollTo().performClick()
        compose.onNodeWithContentDescription("One more").performClick()
        compose.onNodeWithContentDescription("One fewer").performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("A rough night")
        tap("Hot flushes"); tap("Hot flushes") // moderate
        compose.onNodeWithText("Save entry").performClick() // the fixed save bar, not in the scroll
        compose.waitForIdle()
        val (request, draft) = fake.saved!!
        assertEquals(Mood.GOOD, request.mood)
        assertEquals(EnergyLevel.CHARGED, draft.energy)
        assertEquals(7.5, draft.sleepHours!!, 0.0)
        assertEquals(1, draft.alcohol)
        assertEquals("A rough night", draft.notes)
        assertEquals(mapOf(catalog.first { it.name == "Hot flushes" }.id to 2), draft.selected)
        assertEquals(true, closed)
    }

    /** A night Health Connect has is shown, labelled, and can't be changed here (iOS `sleepFromHealth`). */
    @Test fun aNightFromHealthConnectIsReadOnly() {
        val fake = Fake(CheckInDraft(sleepHours = 6.5, sleepFromHealth = true))
        show(fake)
        compose.onNodeWithText("Health Connect").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("6.5 hrs").assertIsDisplayed()
        compose.onNodeWithText("Add hours").assertDoesNotExist()
        compose.onAllNodesWithText("Clear").fetchSemanticsNodes().let { assertEquals("no stepper for an imported night", 0, it.size) }
        compose.onNodeWithText("Save entry").performClick()
        compose.waitForIdle()
        assertEquals(true, fake.saved!!.second.sleepFromHealth)
    }

    @Test fun alcoholIsHiddenWhenSheTurnedItOff() {
        show(Fake(alcohol = false))
        compose.onNodeWithText("Alcohol").assertDoesNotExist()
        compose.onNodeWithText("Add a number").assertDoesNotExist()
    }

    @Test fun closingDoesNotSave() {
        val fake = Fake().also { show(it) }
        tap("Charged")
        compose.onNodeWithContentDescription("Close").performClick()
        assertNull(fake.saved)
        assertEquals(false, closed)
    }

    @Test fun editingPrefillsAndCanRemoveAfterConfirming() {
        val fake = Fake(CheckInDraft(energy = EnergyLevel.LOW, notes = "Kept", alcohol = 2))
        show(fake, CheckInRequest(Mood.LOW, today.minusDays(1), editingId = "C1"))
        compose.onNodeWithText("Edit entry").assertIsDisplayed()
        compose.onNodeWithText("Yesterday").assertIsDisplayed()
        compose.onNodeWithText("Kept").assertExists()
        tap("Remove entry")
        compose.onNodeWithText("Remove this entry?").assertIsDisplayed()
        compose.onNodeWithText("This removes the whole check-in for yesterday. It can't be undone.").assertIsDisplayed()
        compose.onAllNodesWithText("Remove entry")[1].performClick() // the dialog's button
        compose.waitForIdle()
        assertEquals("C1", fake.deleted)
        assertEquals(true, deletedShown)
    }

    /** The intimacy and bladder group stays folded until she opts in. */
    @Test fun moreOpensThePickerWithTheSensitiveGroupFolded() {
        val fake = Fake().also { show(it) }
        compose.onNodeWithContentDescription("More symptoms").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("More symptoms").assertExists()
        compose.onNodeWithText("Vaginal dryness").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show intimacy and bladder symptoms").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Vaginal dryness").performScrollTo().assertIsDisplayed()
        assertEquals(true, fake.sensitive.value)
    }

    /** Adding her own symptom creates it in that group, unselected. */
    @Test fun addYourOwnGoesIntoThatGroup() {
        val fake = Fake().also { show(it) }
        compose.onNodeWithContentDescription("More symptoms").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Add your own symptom to Sleep & rest").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Something else you're noticing…").assertExists()
        // The composer is the last text field (the diary sits behind the sheet).
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("Sleep talking")
        compose.onNodeWithContentDescription("Add symptom").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Sleep talking" to "sleep"), fake.added)
    }

    private fun openPickerInEditMode(fake: Fake) {
        show(fake)
        compose.onNodeWithContentDescription("More symptoms").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Edit symptoms").performClick()
        compose.waitForIdle()
    }

    /** Edit mode: her own symptom can be renamed or removed; a built-in can only be removed. */
    @Test fun editModeOffersRenameOnlyForHerOwnSymptoms() {
        val fake = Fake()
        fake.symptomsFlow.value = catalog + SymptomEntity("MINE", "Sleep talking", "sleep", isCustom = true, ownerID = "o", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        openPickerInEditMode(fake)
        compose.onNodeWithContentDescription("Finish editing symptoms").assertExists()
        compose.onNodeWithText("Press and hold a symptom to drag it into another group. Tap it to rename or remove it.").assertExists()
        compose.onNodeWithContentDescription("Options for Restless legs").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Remove from list").assertExists()
        compose.onNodeWithText("Rename").assertDoesNotExist() // a built-in keeps its wording
        compose.onNodeWithText("Remove from list").performClick()
        compose.waitForIdle()
        assertEquals(listOf(catalog.first { it.name == "Restless legs" }.id), fake.archived)

        compose.onNodeWithContentDescription("Options for Sleep talking").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Rename").assertExists() // her own symptom can be renamed
        compose.onNodeWithText("Remove from list").assertExists()
        // The rename dialog itself is checked on the emulator: Robolectric never idles with a
        // dialog over the open bottom sheet (the device stays at 0% CPU there).
    }

    /** Removing a symptom she had selected also takes it off this check-in. */
    @Test fun removingASelectedSymptomDeselectsIt() {
        val fake = Fake().also { show(it) }
        compose.onNodeWithText("Hot flushes").performScrollTo().performClick()
        compose.onNodeWithText("1 selected").assertExists()
        compose.onNodeWithContentDescription("More symptoms").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Edit symptoms").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Options for Hot flushes").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Remove from list").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Nothing selected yet").assertExists()
    }
}
