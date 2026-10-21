package com.therecalibrationyears.keel.onboarding

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Spacing
import com.therecalibrationyears.keel.designsystem.components.KeelPrimaryButton
import com.therecalibrationyears.keel.designsystem.components.StandardCard
import com.therecalibrationyears.keel.designsystem.lineSpacing

/** Port of iOS `ReadyView`: everything fades in gently. */
@Composable
internal fun ReadyScreen(onComplete: () -> Unit) {
    val theme = LocalKeelTheme.current
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val alpha by animateFloatAsState(if (appeared) 1f else 0f, tween(600, easing = EaseOut), label = "readyFade")

    OnboardingPage(Modifier.padding(horizontal = Spacing.screenH)) {
        Spacer(Modifier.height(Spacing.xl))
        Column(Modifier.alpha(alpha)) {
            OnboardingTitle("Let's start your record.")
            StandardCard(Modifier.padding(top = Spacing.xl), padding = Spacing.xl) {
                Text(
                    "Check in when it suits you. The more you record, the more there is to look back on. Nothing here is a test, and a missed day does not set you back.",
                    style = KeelFont.bodyLarge.lineSpacing(4f),
                    color = theme.text.copy(alpha = 0.9f),
                )
            }
        }
        KeelPrimaryButton(
            "Start with Keel",
            Modifier.padding(top = Spacing.lg, bottom = Spacing.xxxl).alpha(alpha),
            onClick = onComplete,
        )
    }
}
