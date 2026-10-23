package com.therecalibrationyears.keel.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import com.therecalibrationyears.keel.designsystem.components.blocksTouchesBelow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android-only: a layer drawn over another screen in the same window must not pass taps on its
 * empty parts to the screen beneath (found on the emulator: a tap below the check-in's Save
 * button opened Cycle from Home underneath).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class KeelTouchTests {
    @get:Rule val compose = createComposeRule()

    @Test fun aTapOnAnOverlaysEmptyPartStaysOnTheOverlay() {
        var beneath = 0
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().testTag("beneath").clickable { beneath++ })
                Box(Modifier.fillMaxSize().testTag("overlay").blocksTouchesBelow())
            }
        }
        compose.onNodeWithTag("overlay").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(0, beneath) }
    }

    @Test fun anOverlaysOwnButtonsStillWork() {
        var beneath = 0
        var button = 0
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().clickable { beneath++ })
                Box(Modifier.fillMaxSize().blocksTouchesBelow()) {
                    Text("Save entry", Modifier.clickable { button++ })
                }
            }
        }
        compose.onNodeWithText("Save entry").performClick()
        compose.runOnIdle { assertEquals(1, button); assertEquals(0, beneath) }
    }

    @Test fun aTapOnTheFloatingHeaderTitleDoesntOpenTheRowUnderIt() {
        val opened = mutableListOf<Int>()
        compose.setContent {
            KeelThemeProvider {
                KeelScreen(title = "More", onBack = {}) {
                    Column {
                        repeat(40) { i -> Box(Modifier.fillMaxWidth().height(64.dp).clickable { opened += i }) { Text("Row $i") } }
                    }
                }
            }
        }
        // Scroll well past the header so the floating bar shows over the rows.
        compose.onNodeWithText("Row 30").performScrollTo()
        compose.waitForIdle()
        compose.onAllNodesWithText("More").onLast().assertIsDisplayed().performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(emptyList<Int>(), opened) }
    }
}
