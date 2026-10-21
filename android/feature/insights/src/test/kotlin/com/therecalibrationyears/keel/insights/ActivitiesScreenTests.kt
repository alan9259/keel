package com.therecalibrationyears.keel.insights

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.therecalibrationyears.keel.designsystem.KeelThemeProvider
import com.therecalibrationyears.keel.domain.ActivityPeriod
import com.therecalibrationyears.keel.domain.DayAmount
import com.therecalibrationyears.keel.domain.EnergyLevel
import com.therecalibrationyears.keel.domain.Mood
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeActivities(data: ActivitiesData = ActivitiesData()) : ActivitiesActions {
    override val data = MutableStateFlow(data)
    override val zone: ZoneId = ZoneOffset.UTC
    override fun now(): Instant = Instant.parse("2026-10-21T09:00:00Z")
    val amounts = mutableListOf<Triple<String, LocalDate, Double>>()
    val eating = mutableListOf<Triple<String, LocalDate, Boolean?>>()
    override suspend fun setAmount(activityId: String, day: LocalDate, amount: Double) {
        amounts += Triple(activityId, day, amount)
        val rest = data.value.manual.filterNot { it.activityId == activityId && it.day == day }
        data.value = data.value.copy(manual = rest + DayAmount(activityId, day, amount))
    }
    override suspend fun setEating(itemId: String, day: LocalDate, answer: Boolean?) {
        eating += Triple(itemId, day, answer)
        val rest = data.value.manual.filterNot { it.activityId == itemId && it.day == day }
        data.value = data.value.copy(manual = if (answer == null) rest else rest + DayAmount(itemId, day, if (answer) 1.0 else 0.0))
    }
}

/** The Activities screen (iOS `ActivitiesView`), driven as she would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class ActivitiesScreenTests {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 21)

    private fun show(actions: FakeActivities, period: ActivityPeriod = ActivityPeriod.DAY, connect: () -> Unit = {}) =
        compose.setContent { KeelThemeProvider { ActivitiesScreen(actions, {}, connect, period) } }

    @Test fun beforeHealthConnectTheTilesAreHonestlyEmptyAndOfferToConnect() {
        var connect = 0
        show(FakeActivities()) { connect++ }
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next day").assertIsNotEnabled()
        compose.onNodeWithText("From Health Connect").assertIsDisplayed()
        compose.onAllNodesWithText("No data yet").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Connect to Health Connect").performClick()
        assertEquals(1, connect)
        compose.onAllNodesWithText("How you felt today").assertCountEquals(0) // no check-in today
    }

    @Test fun herCheckInAndHerOwnSleepShowOnTheDay() {
        val data = ActivitiesData(
            manual = listOf(DayAmount("sleep", today, 6.5)),
            feelings = listOf(DayFeeling(today, Mood.GOOD, EnergyLevel.LOW)),
            healthConnected = true,
        )
        show(FakeActivities(data))
        compose.onNodeWithText("How you felt today").assertIsDisplayed()
        compose.onNodeWithText("Good · energy low").assertIsDisplayed()
        compose.onNodeWithText("6.5", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Connect to Health Connect").assertCountEquals(0)
    }

    @Test fun waterCountsAndEatingAnswersToggle() {
        val actions = FakeActivities()
        show(actions)
        compose.onNodeWithContentDescription("Increase").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(Triple("water", today, 1.0), actions.amounts.last())
        compose.onNodeWithText("1 glasses").assertIsDisplayed()
        compose.onAllNodesWithText("Yes").onFirst().performScrollTo().performClick() // Protein: yes
        compose.waitForIdle()
        assertEquals(Triple("eat.protein", today, true), actions.eating.last())
        compose.onAllNodesWithText("Yes").onFirst().performClick() // again: cleared
        compose.waitForIdle()
        assertEquals(Triple("eat.protein", today, null), actions.eating.last())
    }

    @Test fun goingBackADayAndTheWeekView() {
        show(FakeActivities(ActivitiesData(feelings = listOf(DayFeeling(today.minusDays(1), Mood.OKAY, null)))))
        compose.onNodeWithContentDescription("Previous day").performClick()
        compose.onNodeWithText("Yesterday").assertIsDisplayed()
        compose.onNodeWithText("How you felt").assertIsDisplayed()
        compose.onNodeWithText("Week").performClick()
        compose.onNodeWithText("This week").assertIsDisplayed()
        compose.onNodeWithText("Checked in").performScrollTo().assertIsDisplayed()
        // Days so far this week, from her locale's first day to today.
        val first = java.time.temporal.WeekFields.of(java.util.Locale.getDefault()).firstDayOfWeek
        val soFar = (today.dayOfWeek.value - first.value + 7) % 7 + 1
        compose.onNodeWithText("1 of $soFar days").performScrollTo().assertIsDisplayed()
    }
}
