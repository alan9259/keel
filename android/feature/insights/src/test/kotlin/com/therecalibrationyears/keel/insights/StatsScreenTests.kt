package com.therecalibrationyears.keel.insights

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.therecalibrationyears.keel.designsystem.KeelThemeProvider
import com.therecalibrationyears.keel.domain.DayAmount
import com.therecalibrationyears.keel.domain.Mood
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeStats(data: StatsData) : StatsActions {
    override val data = MutableStateFlow(data)
    override val zone: ZoneId = ZoneOffset.UTC
    override fun now(): Instant = Instant.parse("2026-10-21T09:00:00Z")
}

/** The Stats screen (iOS `ReportsView`), driven as she would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class StatsScreenTests {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 21)

    @Test fun emptyStatsAreHonest() {
        compose.setContent { KeelThemeProvider { StatsScreen(FakeStats(StatsData()), {}) } }
        compose.onNodeWithText("What you have recorded").assertIsDisplayed()
        compose.onNodeWithText("You checked in on 0 of the last 30 days.").assertIsDisplayed()
        compose.onNodeWithText("No entries yet.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No symptoms logged.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No medications or supplements tracked.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No sleep logged yet. Add it in a check-in, or connect to Health Connect.").performScrollTo().assertIsDisplayed()
    }

    @Test fun periodsAndTheSleepBars() {
        val data = StatsData(
            entries = listOf(StatsEntry(today, Mood.GOOD, null, 0, listOf("Hot flushes")), StatsEntry(today.minusDays(10), Mood.LOW, null, 0, emptyList())),
            manualSleep = listOf(DayAmount("sleep", today, 7.5)),
        )
        compose.setContent { KeelThemeProvider { StatsScreen(FakeStats(data), {}) } }
        compose.onNodeWithText("You checked in on 2 of the last 30 days.").assertIsDisplayed()
        compose.onNodeWithText("Week").performClick()
        compose.onNodeWithText("You checked in on 1 of the last 7 days.").assertIsDisplayed()
        compose.onNodeWithText("Tap a bar for that night's hours.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("avg 7.5h").assertIsDisplayed()
    }

    /** Android-only: TalkBack names a night by what tapping it shows, and skips nights with nothing. */
    @Test fun sleepBarsAreNamedForTalkBack() {
        val data = StatsData(manualSleep = listOf(DayAmount("sleep", today, 7.5)))
        compose.setContent { KeelThemeProvider { StatsScreen(FakeStats(data), {}) } }
        compose.onNodeWithText("Week").performClick()
        compose.onAllNodesWithContentDescription(": ", substring = true).assertCountEquals(1)
        compose.onNodeWithContentDescription("Last night: 7.5h").performScrollTo().assertIsNotSelected().performClick()
        compose.onNodeWithContentDescription("Last night: 7.5h").assertIsSelected()
        compose.onNodeWithText("Last night: 7.5h").assertIsDisplayed()
    }
}
