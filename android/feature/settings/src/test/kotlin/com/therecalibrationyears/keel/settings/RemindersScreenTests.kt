package com.therecalibrationyears.keel.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.therecalibrationyears.keel.designsystem.KeelThemeProvider
import com.therecalibrationyears.keel.domain.ReminderConfig
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeReminders : RemindersActions {
    override val enabledIds = MutableStateFlow(setOf("dailyCheckIn", "medication"))
    override val config = MutableStateFlow(ReminderConfig())
    override val notificationPermissionAsked = MutableStateFlow(false)
    val calls = mutableListOf<Pair<String, Boolean>>()
    override suspend fun markNotificationPermissionAsked() { notificationPermissionAsked.value = true }
    override suspend fun setEnabled(id: String, on: Boolean) {
        calls += id to on
        enabledIds.value = if (on) enabledIds.value + id else enabledIds.value - id
    }
    override suspend fun setConfig(config: ReminderConfig) { this.config.value = config }
}

/** The Reminders screen (iOS `RemindersView`), driven as she would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class RemindersScreenTests {
    @get:Rule val compose = createComposeRule()

    private fun show(actions: FakeReminders) = compose.setContent { KeelThemeProvider { RemindersScreen(actions, onBack = {}) } }

    @Test fun theDefaultsAreOnAndMedicationTimesAreSetPerItem() {
        show(FakeReminders())
        compose.onNodeWithText("Gentle nudges, on your terms").assertIsDisplayed()
        compose.onNodeWithText("Per item").assertIsDisplayed()
        compose.onNodeWithText("Each entry").assertIsDisplayed()
    }

    @Test fun turningOneOnShowsItsTimingAndTurningItOffClearsIt() {
        val actions = FakeReminders()
        show(actions)
        compose.onNodeWithText("Movement").performScrollTo().performClick() // notifications already allowed
        compose.waitForIdle()
        assertEquals("movement" to true, actions.calls.last())
        compose.onNodeWithText("Every day").performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(actions.config.value.movementWeekdaysOnly)
        compose.onNodeWithText("Movement").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("movement" to false, actions.calls.last())
    }

    @Test fun hydrationStepsByTheHourBetweenOneAndSix() {
        val actions = FakeReminders()
        actions.enabledIds.value = actions.enabledIds.value + "hydration"
        show(actions)
        compose.onNodeWithText("Every 2 hours").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Decrease").performClick()
        compose.waitForIdle()
        assertEquals(1, actions.config.value.hydrationIntervalHours)
        compose.onNodeWithText("Every 1 hour").assertIsDisplayed()
        compose.onNodeWithContentDescription("Decrease").performClick() // already at one: stays
        compose.waitForIdle()
        assertEquals(1, actions.config.value.hydrationIntervalHours)
    }
}
