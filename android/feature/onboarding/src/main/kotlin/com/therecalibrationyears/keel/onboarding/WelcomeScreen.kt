package com.therecalibrationyears.keel.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelMark
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Spacing
import com.therecalibrationyears.keel.designsystem.components.KeelPrimaryButton
import com.therecalibrationyears.keel.designsystem.lineSpacing

/** Port of iOS `WelcomeView`. */
@Composable
internal fun WelcomeScreen(onContinue: () -> Unit) {
    val theme = LocalKeelTheme.current
    OnboardingPage(Modifier.padding(horizontal = Spacing.screenH, vertical = 40.dp)) {
        Spacer(Modifier.height(0.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            KeelMark(Modifier.size(96.dp))
            Spacer(Modifier.height(Spacing.md))
            Text("Keel", style = KeelFont.serif(24.sp, FontWeight.Medium).copy(letterSpacing = 3.sp), color = theme.heading)
            // Brand lockup descriptor: where she meets Keel cold. Mist blue, upper case, spaced.
            Text(
                "Perimenopause companion".uppercase(),
                Modifier.padding(top = 6.dp),
                style = KeelFont.sans(10.sp).copy(letterSpacing = 2.sp, textAlign = TextAlign.Center),
                color = theme.secondary,
            )
            OnboardingTitle("You're not imagining it.", Modifier.padding(top = Spacing.lg))
            OnboardingSubtitle(
                "Let's keep track of what's been happening.",
                color = theme.heading.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = Spacing.md),
            )
            Text(
                "A calm companion for the years when your body stops feeling familiar, and you want a record you can trust.",
                Modifier.padding(top = 20.dp),
                style = KeelFont.body.lineSpacing(4f).copy(textAlign = TextAlign.Center),
                color = theme.muted,
            )
            Text(
                "Find your even keel, whatever that means to you.",
                Modifier.padding(top = 18.dp),
                style = KeelFont.body.copy(fontStyle = FontStyle.Italic, textAlign = TextAlign.Center),
                color = theme.muted,
            )
        }
        // One call to action.
        KeelPrimaryButton("Let's begin", Modifier.padding(top = Spacing.lg), onClick = onContinue)
    }
}
