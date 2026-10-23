package com.therecalibrationyears.keel.settings

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.therecalibrationyears.keel.designsystem.KeelThemeProvider
import com.therecalibrationyears.keel.domain.Hysterectomy
import com.therecalibrationyears.keel.domain.PrivacySummary
import com.therecalibrationyears.keel.domain.ProfileForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private class FakeProfile(var stored: ProfileForm = ProfileForm()) : ProfileActions {
    val saves = mutableListOf<ProfileForm>()
    override suspend fun load() = stored
    override suspend fun save(form: ProfileForm) { saves += form; stored = form }
}

/** A stand-in summary for exercising the layout (the owner's Android wording is still to come). */
private val sampleSummary = PrivacySummary(
    lastUpdated = "Last updated: 1 January 2026",
    shortVersion = listOf("First paragraph.", "Second paragraph."),
    columns = listOf("Your information", "Where it is kept", "Access", "Retention and deletion"),
    atAGlance = listOf(
        PrivacySummary.Row("Check-ins", "On this phone", "Only you", "Until deleted"),
        PrivacySummary.Row("Support correspondence", "Our email service", "The team", "Up to two years"),
    ),
)

private val info = AppInfo("1.0.0", "1.0.0 (1)", "Android 16 (API 36)", "Google Pixel 8")

/** Get support, About, Connect, Profile and Your privacy (iOS views of the same names), driven as she would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class InfoScreensTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun started(): Intent = shadowOf(compose.activity).nextStartedActivity

    @Test fun supportInAustraliaShowsAustralianLinesAndDialsThem() {
        compose.setContent { KeelThemeProvider { SupportScreen(onBack = {}, localeCountry = "AU", zoneId = "Australia/Sydney") } }
        compose.onNodeWithContentDescription("Call Emergency, 000").assertIsDisplayed()
        compose.onNodeWithContentDescription("Call Lifeline, 13 11 14").performClick()
        val dial = started()
        assertEquals(Intent.ACTION_DIAL, dial.action)
        assertEquals("tel:131114", dial.dataString)
        compose.onAllNodesWithText("Australia".uppercase()).assertCountEquals(0) // one region: no country label
        compose.onNodeWithText("call 000 or go to", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun supportElsewhereShowsBothCountriesWithGeneralWording() {
        compose.setContent { KeelThemeProvider { SupportScreen(onBack = {}, localeCountry = "GB", zoneId = "Europe/London") } }
        compose.onNodeWithText("AUSTRALIA").assertIsDisplayed()
        compose.onNodeWithText("NEW ZEALAND").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Call Need to talk?, 1737").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("call your local emergency number", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun aboutShowsTheOwnersWordsAndLinks() {
        var privacy = 0
        compose.setContent { KeelThemeProvider { AboutScreen(info, onOpenPrivacy = { privacy++ }, onBack = {}) } }
        compose.onNodeWithText("You're not imagining it.").assertIsDisplayed()
        compose.onNodeWithText("What Keel isn't").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Keel · Version 1.0.0 · © 2026 TRY Keel Pty Ltd").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Your privacy").performScrollTo().performClick()
        assertEquals(1, privacy)
        compose.onNodeWithText("Support").performScrollTo().performClick()
        assertTrue(started().dataString!!.contains("subject=Keel%20support"))
    }

    @Test fun connectOpensEachAccountAndEmail() {
        compose.setContent { KeelThemeProvider { ConnectScreen(info, onBack = {}) } }
        compose.onNodeWithContentDescription("Instagram, @keelperiapp").performClick()
        assertEquals("https://www.instagram.com/keelperiapp/", started().dataString)
        compose.onNodeWithContentDescription("Facebook, @KeelPeriApp").performClick()
        assertEquals("https://www.facebook.com/KeelPeriApp", started().dataString)
        compose.onNodeWithContentDescription("Email us at keel@therecalibrationyears.com").performScrollTo().performClick()
        assertEquals(Intent.ACTION_SENDTO, started().action)
    }

    @Test fun profileLoadsHerDetailsAndSavesThemWithHerBackground() {
        val actions = FakeProfile(ProfileForm(firstName = "Mischa", age = "49"))
        compose.setContent { KeelThemeProvider { ProfileScreen(actions, onBack = {}) } }
        compose.waitForIdle()
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].assertTextContains("Mischa")
        fields[1].performTextReplacement("Wang")
        compose.onNodeWithText("Yes, both ovaries were kept").performScrollTo().performClick()
        compose.onNodeWithText("Year (if you know it)").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction())[5].performTextReplacement("2019")
        compose.onNodeWithText("Save changes").performScrollTo()
        compose.mainClock.autoAdvance = false // to see the confirmation before it fades
        compose.onNodeWithText("Save changes").performClick()
        compose.mainClock.advanceTimeBy(500)
        val saved = actions.saves.single()
        assertEquals("Wang", saved.lastName)
        assertEquals(Hysterectomy.YES_OVARIES_KEPT, saved.hysterectomy)
        assertEquals("2019", saved.hysterectomyYear)
        compose.onNodeWithText("Changes saved").assertExists()
        compose.mainClock.advanceTimeBy(3_000) // shown for two seconds, as on iOS
        compose.onNodeWithText("Changes saved").assertDoesNotExist()
    }

    /** Android-only: each field carries its label, so TalkBack names it (no unnamed "Edit box"). */
    @Test fun eachProfileFieldCarriesItsLabel() {
        compose.setContent { KeelThemeProvider { ProfileScreen(FakeProfile(), onBack = {}) } }
        compose.waitForIdle()
        listOf("First name", "Last name (optional)", "Age (optional)", "Mobile (optional)", "Email (optional)").forEach { label ->
            compose.onNode(hasSetTextAction() and hasAnyDescendant(hasText(label)), useUnmergedTree = true).assertExists(label)
        }
    }

    @Test fun tappingHerAnswerAgainClearsItAndAYearIsOnlyAskedForAYes() {
        compose.setContent { KeelThemeProvider { ProfileScreen(FakeProfile(ProfileForm(hysterectomy = Hysterectomy.NO)), onBack = {}) } }
        compose.onNodeWithText("No").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Year (if you know it)").assertDoesNotExist()
        compose.onNodeWithText("No").performClick()
        compose.onNodeWithText("No").assertIsNotSelected()
        compose.onNodeWithText("Yes, I'm not sure about my ovaries").performScrollTo().performClick()
        compose.onNodeWithText("Not being sure is common. It can be a useful question to take to your GP.").performScrollTo().assertIsDisplayed()
    }

    @Test fun yourPrivacyReadsEachRowAsOneSentenceAndLinksThePolicy() {
        compose.setContent { KeelThemeProvider { YourPrivacyScreen(sampleSummary, info, onBack = {}) } }
        compose.onNodeWithText("Last updated: 1 January 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Check-ins. Where it is kept: On this phone. Access: Only you. Retention and deletion: Until deleted.",
        ).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Where it is kept").assertCountEquals(0) // the header row is read with each row instead
        compose.onNodeWithText("Read the full privacy policy").performScrollTo().performClick()
        assertEquals("https://therecalibrationyears.com/keel-privacy", started().dataString)
    }
}
