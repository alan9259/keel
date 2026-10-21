package com.therecalibrationyears.keel.settings

import android.app.Application
import android.provider.Settings
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.therecalibrationyears.keel.designsystem.KeelThemeProvider
import com.therecalibrationyears.keel.domain.ReminderConfig
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

private class OnReminders(ids: Set<String>) : RemindersActions {
    override val enabledIds = MutableStateFlow(ids)
    override val config = MutableStateFlow(ReminderConfig())
    override val notificationPermissionAsked = MutableStateFlow(true)
    override suspend fun markNotificationPermissionAsked() {}
    override suspend fun setEnabled(id: String, on: Boolean) {}
    override suspend fun setConfig(config: ReminderConfig) {}
}

/** "Reminders on time" (Android only, plan D25): the one-time dialog and the Reminders screen note. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class ExactTimingTests {
    @get:Rule val compose = createComposeRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun nextSettingsPage() = shadowOf(app).nextStartedActivity

    @Test fun theDialogHasOnlyContinueWhichOpensAlarmsAndReminders() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        var answered = 0
        compose.setContent { KeelThemeProvider { ExactTimingDialog(onContinue = { answered++ }) } }
        compose.onNodeWithText("Reminders on time").assertIsDisplayed()
        // Before Android's page, Continue is the only way on (no Skip or Not now).
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()
        assertEquals(1, answered)
        val page = nextSettingsPage()
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, page.action)
        assertEquals("package:${app.packageName}", page.dataString)
    }

    @Test fun theRemindersScreenNotesLateDeliveryAndOpensTheSamePage() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        compose.setContent { KeelThemeProvider { RemindersScreen(OnReminders(setOf("dailyCheckIn")), onBack = {}) } }
        compose.onNodeWithText("Reminders may arrive late").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, nextSettingsPage().action)
    }

    @Test fun noNoteWhenRemindersAreAlreadyOnTime() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        compose.setContent { KeelThemeProvider { RemindersScreen(OnReminders(setOf("dailyCheckIn")), onBack = {}) } }
        compose.onNodeWithText("Gentle nudges, on your terms").assertIsDisplayed()
        compose.onAllNodesWithText("Reminders may arrive late").assertCountEquals(0)
    }

    @Test fun noNoteWhenNoReminderIsOn() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        compose.setContent { KeelThemeProvider { RemindersScreen(OnReminders(emptySet()), onBack = {}) } }
        compose.onAllNodesWithText("Reminders may arrive late").assertCountEquals(0)
    }
}
