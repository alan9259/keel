package com.therecalibrationyears.keel.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.designsystem.KeelDateFormats
import com.therecalibrationyears.keel.designsystem.KeelThemeProvider
import com.therecalibrationyears.keel.domain.DayAmount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private object NoHomeActions : HomeActions {
    override fun navigate(destination: HomeDestination) {}
    override fun createEntry(day: LocalDate) {}
    override fun editEntry(checkInId: String) {}
    override fun toggleMedication(medication: MedicationEntity, day: LocalDate, currentlyTaken: Boolean) {}
}

/** Android-only checks on Home: what TalkBack reads, and 48 dp touch targets (Android's minimum). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class HomeScreenTests {
    @get:Rule val compose = createComposeRule()
    private val zone = ZoneId.of("Australia/Sydney")
    private val today = LocalDate.of(2026, 10, 21)
    private val now: Instant = today.atTime(10, 0).atZone(zone).toInstant()

    private fun show(data: HomeData) {
        compose.setContent { KeelThemeProvider { HomeScreen(data, now, zone, NoHomeActions) } }
        compose.waitForIdle()
    }

    @Test fun aNightWithSleepIsNamedAndNightsWithoutAreSkipped() {
        show(HomeData(manualSleep = listOf(DayAmount("sleep", today, 7.5), DayAmount("sleep", today.minusDays(2), 6.0))))
        val formats = KeelDateFormats(RuntimeEnvironment.getApplication(), zone = zone)
        compose.onNodeWithContentDescription("${formats.weekdayDay(today.minusDays(2))}, 6h").assertExists()
        // the five nights without sleep aren't buttons: only two bars on the strip are named
        val hoursBar = SemanticsMatcher("names a night's hours") { node ->
            node.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }.orEmpty().any { Regex(", [0-9.]+h$").containsMatchIn(it) }
        }
        compose.onAllNodes(hasClickAction() and hoursBar).assertCountEquals(2)
        compose.onNodeWithContentDescription("Last night, 7.5h").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Last night, 7.5h").assertIsSelected()
    }

    @Test fun eachMedicineIsAFullSizeRow() {
        val med = MedicationEntity(id = "m", name = "Magnesium", isTracked = true, ownerID = "o", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        show(HomeData(medications = listOf(med)))
        compose.onNodeWithContentDescription("Magnesium").assertHeightIsAtLeast(48.dp)
    }

    @Test fun eachCalendarDayIsAFullSizeTarget() {
        show(HomeData())
        val formats = KeelDateFormats(RuntimeEnvironment.getApplication(), zone = zone)
        listOf(today, today.withDayOfMonth(1)).forEach { day ->
            compose.onNodeWithContentDescription(formats.fullDate(day)).performScrollTo().assertHeightIsAtLeast(48.dp)
        }
    }
}
