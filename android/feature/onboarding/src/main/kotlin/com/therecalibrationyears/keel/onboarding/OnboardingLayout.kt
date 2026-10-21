package com.therecalibrationyears.keel.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.lineSpacing

/**
 * A screen laid out as on iOS (`Spacer` / content / `Spacer` / button): children are spread
 * so the middle one sits centred between the first and last. Unlike iOS it scrolls when
 * the content is taller than the screen (a small phone or a large font size).
 */
@Composable
internal fun OnboardingPage(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).fillMaxWidth().heightIn(min = maxHeight),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

/**
 * Onboarding title (iOS `onboardingTitle()`): serif 28 semibold, up to three lines, shrinking
 * to 80% before it would need a fourth. Uses the theme's heading colour so it reads in dark
 * mode (iOS uses fixed charcoal there, which is nearly invisible on the dark background).
 */
@Composable
internal fun OnboardingTitle(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center) {
    val theme = LocalKeelTheme.current
    BasicText(
        text,
        modifier.fillMaxWidth().semantics { heading() },
        style = KeelFont.serif(28.sp).copy(color = theme.heading, textAlign = align),
        maxLines = 3,
        autoSize = TextAutoSize.StepBased(minFontSize = 22.4.sp, maxFontSize = 28.sp, stepSize = 0.4.sp),
    )
}

/** Onboarding lead text (iOS `onboardingSubtitle()`): body large with 4 extra between lines. */
@Composable
internal fun OnboardingSubtitle(text: String, color: Color, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center) {
    Text(
        text,
        modifier.fillMaxWidth(),
        style = KeelFont.bodyLarge.lineSpacing(4f).copy(textAlign = align),
        color = color,
    )
}
