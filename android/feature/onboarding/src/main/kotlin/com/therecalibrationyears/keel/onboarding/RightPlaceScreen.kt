package com.therecalibrationyears.keel.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Spacing
import com.therecalibrationyears.keel.designsystem.components.CalloutCard
import com.therecalibrationyears.keel.designsystem.components.KeelPrimaryButton
import com.therecalibrationyears.keel.designsystem.components.StandardCard
import com.therecalibrationyears.keel.designsystem.lineSpacing

/** Port of iOS `RightPlaceView`. */
@Composable
internal fun RightPlaceScreen(onContinue: () -> Unit) {
    val theme = LocalKeelTheme.current
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier
                .weight(1f)
                // On a smaller phone the callout may need a scroll: fade the last few dp
                // rather than slicing a line of text against the button.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fade = Spacing.md.toPx()
                    drawRect(
                        Brush.verticalGradient(
                            listOf(theme.text, theme.text.copy(alpha = 0f)),
                            startY = size.height - fade, endY = size.height,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenH),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Spacer(Modifier.height(Spacing.md))
            OnboardingTitle("You're in the right place.", align = TextAlign.Start)

            // Pull quote, with the accent bar along its leading edge.
            Box(Modifier.height(IntrinsicSize.Min)) {
                StandardCard {
                    Text(
                        "“Ohhh… that explains a lot.”",
                        style = KeelFont.h3.copy(fontStyle = FontStyle.Italic),
                        color = theme.text.copy(alpha = 0.8f),
                    )
                }
                Box(
                    Modifier.align(Alignment.CenterStart).width(4.dp).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp)).background(theme.accent),
                )
            }

            Text(
                "Maybe I'm not losing myself after all. Perimenopause can make you feel like you're piecing yourself together from scattered clues. Keel gives you one place to record what you're noticing: sleep, mood, energy, cycle, symptoms, treatments and how it's affecting your days. Whatever you record is there for you to look back on later, in your own words.",
                style = KeelFont.bodyLarge.lineSpacing(4f),
                color = theme.text.copy(alpha = 0.9f),
            )

            CalloutCard {
                Text(
                    "One thing to know: there is no minimum. Even a few entries give you something to look back on, and the more you record, the fuller your record becomes.",
                    style = KeelFont.body.lineSpacing(3f),
                    color = theme.text.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.height(Spacing.sm))
        }
        // A clear gap above the button so the callout never runs into it.
        KeelPrimaryButton(
            "Continue",
            Modifier.fillMaxWidth().padding(start = Spacing.screenH, end = Spacing.screenH, top = Spacing.md, bottom = Spacing.xxl),
            onClick = onContinue,
        )
    }
}
