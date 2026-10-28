package com.therecalibrationyears.keel.designsystem

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A feature screen's header, on a phone as wide as an iPhone 16 (393). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenHeaderTests {
    @get:Rule val compose = createComposeRule()

    private fun show(title: String, onBack: () -> Unit = {}, onHome: () -> Unit = {}) = compose.setContent {
        KeelThemeProvider { KeelScreen(title = title, onBack = onBack, onHome = onHome) {} }
    }

    private fun titleLayout(title: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(title).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }

    /** As iOS: each button takes 32 of the row, so the title has 393 - 2 x (24 + 32 + 14). */
    @Test fun theTitleHasTheRoomItHasOnIPhone() {
        show("Settings")
        compose.onNodeWithText("Settings").assertLeftPositionInRootIsEqualTo(70.dp)
        val room = with(compose.density) { titleLayout("Settings").layoutInput.constraints.maxWidth.toDp() }
        assertEquals(253f, room.value, 0.5f)
    }

    /** The bug: three lines on a 393dp phone, two on iPhone. */
    @Test fun medicationsAndSupplementsTakesTwoLines() {
        show("Medications and supplements")
        assertEquals(2, titleLayout("Medications and supplements").lineCount)
    }

    @Test fun theButtonsKeepTheirFullTouchTarget() {
        var back = 0
        var home = 0
        show("Settings", onBack = { back++ }, onHome = { home++ })
        // Each button draws a 40dp circle in the 32dp it takes; a tap just outside the circle
        // (inside the 48dp target) still counts.
        val outside = with(compose.density) { 2.dp.toPx() }
        compose.onNodeWithContentDescription("Back").performTouchInput { click(Offset(-outside, centerY)) }
        compose.onNodeWithContentDescription("Home").performTouchInput { click(Offset(width + outside, centerY)) }
        compose.runOnIdle { assertEquals(1, back); assertEquals(1, home) }
    }
}
