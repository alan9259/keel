package com.therecalibrationyears.keel.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.KeelMark
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.Spacing
import com.therecalibrationyears.keel.designsystem.components.KeelPrimaryButton
import kotlinx.coroutines.launch

/**
 * Port of iOS `AppleHealthView`, for Health Connect. One way forward: no "Skip" before the
 * system prompt (App Review 5.1.1(iv), applied on Android too); she chooses on Health
 * Connect's own screen.
 */
@Composable
internal fun HealthScreen(connectHealth: suspend () -> Boolean, onContinue: () -> Unit) {
    val theme = LocalKeelTheme.current
    val scope = rememberCoroutineScope()
    var connecting by remember { mutableStateOf(false) }

    OnboardingPage(Modifier.padding(horizontal = Spacing.screenH)) {
        Spacer(Modifier.height(Spacing.xl))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Icon pair: partnership.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(Radius.lg)).background(theme.plum),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(KeelIcons.heart, contentDescription = null, tint = theme.onFill, modifier = Modifier.size(32.dp))
                }
                Icon(KeelIcons.plus, contentDescription = null, tint = theme.muted, modifier = Modifier.size(24.dp))
                KeelMark(Modifier.size(64.dp), cornerRadius = Radius.lg)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                OnboardingTitle("Bring in what you already record.", Modifier.padding(top = Spacing.xl))
                OnboardingSubtitle(
                    "Keel can read your sleep, activity, vitals and periods from Health Connect, so there is less for you to enter by hand. Keel shows these alongside your own record and labels where each one came from. Keel only ever reads. It never writes anything back.",
                    color = theme.text.copy(alpha = 0.8f),
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            KeelPrimaryButton("Continue", isEnabled = !connecting) {
                connecting = true
                scope.launch {
                    connectHealth()
                    connecting = false
                    onContinue()
                }
            }
            Text("We never sell your data. Ever.", style = KeelFont.caption, color = theme.muted)
        }
    }
}
