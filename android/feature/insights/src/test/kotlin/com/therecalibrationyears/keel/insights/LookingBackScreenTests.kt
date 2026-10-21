package com.therecalibrationyears.keel.insights

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.therecalibrationyears.keel.designsystem.KeelThemeProvider
import com.therecalibrationyears.keel.domain.Insight
import com.therecalibrationyears.keel.domain.InsightAccent
import com.therecalibrationyears.keel.domain.MonthSummary
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val NOW: Instant = Instant.parse("2026-10-21T09:00:00Z")

private class FakeLookingBack(entries: List<MonthSummary.Entry>, insights: List<Insight> = emptyList()) : LookingBackActions {
    override val data = MutableStateFlow(
        LookingBackData(MonthSummary.build(entries, NOW, ZoneOffset.UTC), entries.any { !it.notes.isNullOrBlank() }, insights),
    )
    override val allNotes = MutableStateFlow(MonthSummary.notes(entries))
    override val zone: ZoneId = ZoneOffset.UTC
    override fun now(): Instant = NOW
}

/** Looking back and Your notes (iOS `PatternsView`, `AllNotesView`), driven as she would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class LookingBackScreenTests {
    @get:Rule val compose = createComposeRule()

    private fun entry(day: Int, notes: String? = null, symptoms: List<String> = emptyList(), month: Int = 10, year: Int = 2026) =
        MonthSummary.Entry("$year-$month-$day", Instant.parse("%d-%02d-%02dT09:00:00Z".format(year, month, day)), symptoms, notes)

    @Test fun anEmptyMonthSaysSoHonestly() {
        compose.setContent { KeelThemeProvider { LookingBackScreen(FakeLookingBack(emptyList()), {}, {}, {}) } }
        compose.onNodeWithText("OCTOBER SO FAR").assertIsDisplayed()
        compose.onNodeWithText("No check-ins yet this month.").assertIsDisplayed()
        compose.onNodeWithText("Nothing written this month.").assertIsDisplayed()
        compose.onAllNodesWithText("Symptoms you logged most often").assertCountEquals(0)
        compose.onAllNodesWithText("See all notes").assertCountEquals(0)
        compose.onNodeWithText("Seeing your GP soon?").assertIsDisplayed()
    }

    @Test fun showsHerMonthHerNotesAndLeadsOn() {
        val entries = listOf(
            entry(2, "First", listOf("Hot flushes", "Headaches")), entry(9, "Second", listOf("Hot flushes")),
            entry(14, "Third"), entry(20, "Fourth"), entry(28, "Last month", month = 9),
        )
        val card = Insight(
            "Your cycle length", "Your recent cycles ranged from about 25 to 35 days apart. That's the kind of detail that can be useful to bring to your GP.",
            "Across your last 2 logged cycles", "arrow.left.and.right", InsightAccent.SAGE,
        )
        var seeAll = 0
        var gp = 0
        compose.setContent { KeelThemeProvider { LookingBackScreen(FakeLookingBack(entries, listOf(card)), {}, { seeAll++ }, { gp++ }) } }
        compose.onNodeWithText("You checked in on 4 days this month.").assertIsDisplayed()
        compose.onNodeWithText("Hot flushes").assertIsDisplayed()
        compose.onNodeWithText("2 days").assertIsDisplayed()
        // Three newest notes here; the rest behind "See all notes".
        compose.onNodeWithText("Fourth").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("First").assertCountEquals(0)
        compose.onNodeWithText("See all notes").performScrollTo().performClick()
        compose.onNodeWithText("Your cycle length").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Seeing your GP soon?").performScrollTo().performClick()
        assertEquals(1, seeAll)
        assertEquals(1, gp)
    }

    @Test fun yourNotesListsEveryNoteNewestFirstWithTheYearWhenNotThisYear() {
        val entries = listOf(entry(14, "This year"), entry(3, "Last year", month = 12, year = 2025), entry(15, "  "))
        compose.setContent { KeelThemeProvider { AllNotesScreen(FakeLookingBack(entries), {}) } }
        compose.onNodeWithText("This year").assertIsDisplayed()
        compose.onNodeWithText("Last year").assertIsDisplayed()
        compose.onNodeWithText("2025", substring = true).assertIsDisplayed() // the date carries its year
        compose.onAllNodesWithText("2026", substring = true).assertCountEquals(0)
    }

    @Test fun yourNotesEmpty() {
        compose.setContent { KeelThemeProvider { AllNotesScreen(FakeLookingBack(emptyList()), {}) } }
        compose.onNodeWithText("Nothing written yet.").assertIsDisplayed()
    }
}
