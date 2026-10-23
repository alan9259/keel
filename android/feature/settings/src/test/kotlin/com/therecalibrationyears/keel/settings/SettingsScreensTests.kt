package com.therecalibrationyears.keel.settings

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.therecalibrationyears.keel.designsystem.ColourMode
import com.therecalibrationyears.keel.designsystem.KeelThemeProvider
import com.therecalibrationyears.keel.domain.FeedbackMail
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private class FakeSettings : SettingsActions {
    override val pushNotifications = MutableStateFlow(true)
    override val haptics = MutableStateFlow(true)
    override val medicineNamesInReminders = MutableStateFlow(false)
    override val notesAlcohol = MutableStateFlow(true)
    override val notificationPermissionAsked = MutableStateFlow(true)
    var erased = 0
    override suspend fun markNotificationPermissionAsked() { notificationPermissionAsked.value = true }
    override suspend fun setPushNotificationsEnabled(on: Boolean) { pushNotifications.value = on }
    override suspend fun setHaptics(on: Boolean) { haptics.value = on }
    override suspend fun setMedicineNamesInReminders(on: Boolean) { medicineNamesInReminders.value = on }
    override suspend fun setNotesAlcohol(on: Boolean) { notesAlcohol.value = on }
    override fun eraseAllData() { erased++ }
}

private class FakeAppearance : AppearanceActions {
    override val themeId = MutableStateFlow<String?>(null)
    override val colourModeRaw = MutableStateFlow<String?>(null)
    override val moodPackId = MutableStateFlow<String?>(null)
    override suspend fun setThemeId(id: String) { themeId.value = id }
    override suspend fun setColourMode(mode: ColourMode) { colourModeRaw.value = mode.raw }
    override suspend fun setMoodPackId(id: String) { moodPackId.value = id }
}

private val info = AppInfo("1.0.0", "1.0.0 (1)", "Android 16 (API 36)", "Google Pixel 8")

/** More, Settings, Themes, Colour mode and Mood icons (iOS `MoreView` etc.), driven as she would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class SettingsScreensTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun showMore(opened: MutableList<MoreDestination> = mutableListOf()) =
        compose.setContent { KeelThemeProvider { MoreScreen(info, onOpen = { opened += it }, onBack = {}) } }

    @Test fun moreLeadsToEachFeatureAndShowsTheVersion() {
        val opened = mutableListOf<MoreDestination>()
        showMore(opened)
        // Before scrolling: once the floating header is up, a tap where App lock sits under it
        // lands on the header (it no longer leaks through to the row).
        compose.onNodeWithText("App lock").performClick()
        listOf("Profile/Settings", "Tracking", "Personalisation", "Data", "Feedback", "Support & about").forEach {
            compose.onNodeWithText(it.uppercase()).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Themes").performScrollTo().performClick()
        compose.onNodeWithText("Health Connect").performScrollTo().performClick()
        compose.onNodeWithText("About Keel").performScrollTo().performClick()
        assertEquals(listOf(MoreDestination.APP_LOCK, MoreDestination.THEMES, MoreDestination.HEALTH, MoreDestination.ABOUT), opened)
        compose.onNodeWithText("Keel · Version 1.0.0").performScrollTo().assertIsDisplayed()
    }

    @Test fun feedbackOpensADraftInHerMailApp() {
        showMore()
        compose.onNodeWithText("Share feedback").performScrollTo().performClick()
        val sent = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_SENDTO, sent.action)
        assertTrue(sent.dataString!!.startsWith("mailto:keel@therecalibrationyears.com?subject=Keel%20feedback&body="))
        assertEquals("Keel feedback", sent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertTrue(sent.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Sent from Keel 1.0.0 (1) · Android 16 (API 36) · Google Pixel 8"))
    }

    @Test fun withNoMailAppSheCanCopyTheAddress() {
        shadowOf(RuntimeEnvironment.getApplication() as Application).checkActivities(true) // nothing handles mailto
        showMore()
        compose.onNodeWithText("Request a feature").performScrollTo().performClick()
        compose.onNodeWithText("No email app set up").assertIsDisplayed()
        compose.onNodeWithText("Copy email address").performClick()
        compose.waitForIdle()
        val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(FeedbackMail.ADDRESS, clipboard.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test fun tellYourFriendsSharesTheNoteAndLink() {
        showMore()
        compose.onNodeWithText("Tell your friends").performScrollTo().performClick()
        val chooser = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertTrue(send.getStringExtra(Intent.EXTRA_TEXT)!!.startsWith("I've been using Keel"))
    }

    private fun showSettings(actions: FakeSettings, onPrivacy: () -> Unit = {}) =
        compose.setContent { KeelThemeProvider { SettingsScreen(actions, onOpenPrivacy = onPrivacy, onBack = {}) } }

    @Test fun togglesChangeHerSettings() {
        val actions = FakeSettings()
        showSettings(actions)
        compose.onNodeWithText("Haptic feedback").assertIsOn().performClick()
        compose.onNodeWithText("Medicine names in reminders").assertIsOff().performClick()
        compose.onNodeWithText("Alcohol").performScrollTo().performClick()
        compose.onNodeWithText("Push notifications").performClick()
        compose.waitForIdle()
        assertFalse(actions.haptics.value)
        assertTrue(actions.medicineNamesInReminders.value)
        assertFalse(actions.notesAlcohol.value)
        assertFalse(actions.pushNotifications.value)
        compose.onNodeWithText("Push notifications").performClick() // back on: notifications are allowed here
        compose.waitForIdle()
        assertTrue(actions.pushNotifications.value)
    }

    @Test fun yourPrivacyIsLinkedFromSettings() {
        var opened = 0
        showSettings(FakeSettings()) { opened++ }
        compose.onNodeWithText("Your privacy").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test fun deletingEverythingNeedsTheWordTyped() {
        val actions = FakeSettings()
        showSettings(actions)
        compose.onNodeWithText("Delete all my data").performScrollTo().performClick()
        compose.onNodeWithText("Delete all your data?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick() // nothing typed: inert
        compose.onNodeWithContentDescription("DELETE").performTextInput("delete")
        compose.onNodeWithText("Delete").performClick() // wrong case: still inert
        assertEquals(0, actions.erased)
        compose.onNodeWithContentDescription("DELETE").performTextReplacement("DELETE")
        compose.onNodeWithText("Delete").performClick()
        assertEquals(1, actions.erased)
    }

    @Test fun cancelLeavesEverythingAsItWas() {
        val actions = FakeSettings()
        showSettings(actions)
        compose.onNodeWithText("Delete all my data").performScrollTo().performClick()
        compose.onNodeWithContentDescription("DELETE").performTextInput("DELETE")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete all your data?").assertDoesNotExist()
        assertEquals(0, actions.erased)
    }

    @Test fun choosingAThemeMakesItActive() {
        val actions = FakeAppearance()
        compose.setContent { KeelThemeProvider { ThemesScreen(actions, onBack = {}) } }
        compose.onNodeWithText("Make Keel feel like yours").assertIsDisplayed()
        compose.onNodeWithText("Forest Floor").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("forest", actions.themeId.value)
        // Now in the "Current theme" card as well as its own row.
        compose.onAllNodesWithText("Deep mossy greens, bark browns and mushroom tones.").assertCountEquals(2)
        compose.onNode(hasText("Forest Floor") and isSelected()).assertExists()
    }

    @Test fun colourModeSaysWhatIsShowing() {
        val actions = FakeAppearance()
        compose.setContent { KeelThemeProvider { ColourModeScreen(actions, onBack = {}) } }
        compose.onNodeWithText("System").assertIsSelected() // until she chooses
        compose.onNodeWithText("Currently showing: Light").assertIsDisplayed() // the test device is light
        compose.onNodeWithText("Dark").performClick()
        compose.waitForIdle()
        assertEquals("dark", actions.colourModeRaw.value)
        compose.onNodeWithText("Currently showing: Dark").assertIsDisplayed()
    }

    @Test fun choosingAMoodPack() {
        val actions = FakeAppearance()
        compose.setContent { KeelThemeProvider { MoodIconsScreen(actions, onBack = {}) } }
        compose.onNodeWithText("Ocean Tides").assertIsSelected() // the default
        compose.onNodeWithText("Moon Phases").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("moon", actions.moodPackId.value)
        compose.onNodeWithText("Moon Phases").assertIsSelected()
    }
}
