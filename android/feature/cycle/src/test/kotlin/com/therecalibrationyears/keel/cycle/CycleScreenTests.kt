package com.therecalibrationyears.keel.cycle

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.therecalibrationyears.keel.designsystem.KeelThemeProvider
import com.therecalibrationyears.keel.domain.CycleDay
import com.therecalibrationyears.keel.domain.CycleEntryType
import com.therecalibrationyears.keel.domain.FlowLevel
import com.therecalibrationyears.keel.domain.InsightAccent
import com.therecalibrationyears.keel.domain.PatternFinding
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

private class FakeCycle(days: List<Pair<LocalDate, CycleDay>> = emptyList(), finding: PatternFinding? = null) : CycleActions {
    override val data = MutableStateFlow(CycleData(days, finding = finding))
    override val zone: ZoneId = ZoneOffset.UTC
    override fun now(): Instant = Instant.parse("2026-10-21T09:00:00Z")
    val calls = mutableListOf<Pair<FlowLevel?, LocalDate>>()
    override suspend fun setFlow(level: FlowLevel?, day: LocalDate) {
        calls += level to day
        val rest = data.value.days.filter { it.first != day }
        data.value = data.value.copy(days = if (level == null) rest else rest + (day to CycleDay(CycleEntryType.PERIOD_START, level)))
    }
}

/** The Cycle screen (iOS `CycleTrackingView`), driven as she would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class CycleScreenTests {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 21)

    private fun show(actions: FakeCycle, profile: () -> Unit = {}) =
        compose.setContent { KeelThemeProvider { CycleScreen(actions, onBack = {}, onOpenProfile = profile) } }

    @Test fun emptyStateInvitesTheFirstPeriodAndLogsIt() {
        val actions = FakeCycle()
        show(actions)
        compose.onNodeWithText("Log a period to start your cycle.").assertIsDisplayed()
        compose.onAllNodesWithText("Estimated phase").assertCountEquals(0) // nothing to estimate from
        // Today is on the timeline and in the month grid; tap the timeline's.
        compose.onAllNodesWithContentDescription(", today", substring = true).onFirst().performClick()
        compose.onNodeWithText("How was your flow?").assertIsDisplayed()
        compose.onNodeWithText("Not a period day").assertIsDisplayed()
        compose.onNodeWithText("Heavy").performClick()
        compose.waitForIdle()
        assertEquals(FlowLevel.HEAVY to today, actions.calls.single())
        compose.onNodeWithText("Day 1 of your cycle.").assertIsDisplayed()
    }

    @Test fun aLoggedDayCanBeRemoved() {
        val actions = FakeCycle(listOf(today.minusDays(1) to CycleDay(CycleEntryType.PERIOD_START, FlowLevel.LIGHT)))
        show(actions)
        compose.onAllNodesWithContentDescription(", Light", substring = true).onFirst().performClick()
        compose.onNodeWithText("Remove this period day").performClick()
        compose.waitForIdle()
        assertEquals(null to today.minusDays(1), actions.calls.single())
    }

    @Test fun withCyclesItShowsHistoryPhaseAndTheRealInsight() {
        val start = CycleDay(CycleEntryType.PERIOD_START, FlowLevel.MEDIUM)
        val finding = PatternFinding(
            PatternFinding.Kind.CYCLE_VARIABILITY, "Your cycle length",
            "Your recent cycles ranged from about 22 to 40 days apart. That's the kind of detail that can be useful to bring to your GP.",
            "Across your last 3 logged cycles", "arrow.left.and.right", InsightAccent.SAGE,
        )
        show(FakeCycle(listOf(-90L, -68L, -28L, -3L).map { today.plusDays(it) to start }, finding))
        compose.onNodeWithText("Day 4 of your cycle. Your recent cycles have varied, so no estimate yet.").assertIsDisplayed()
        compose.onNodeWithText("Your recent cycles").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lately your cycles have run 22 to 40 days.").assertIsDisplayed()
        compose.onNodeWithText("Menstrual").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Your cycle length").performScrollTo().assertIsDisplayed()
    }

    @Test fun profileLinkLeadsOn() {
        var opened = 0
        show(FakeCycle()) { opened++ }
        compose.onNodeWithText("Periods don't apply to you? You can turn this off, or add your background in Profile.").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test fun theMonthGridCannotGoPastThisMonth() {
        show(FakeCycle())
        compose.onNodeWithText("October 2026").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithText("October 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous month").performClick()
        compose.onNodeWithText("September 2026").assertIsDisplayed()
    }
}
