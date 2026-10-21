package com.therecalibrationyears.keel.checkin

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme

/**
 * The diary's microphone toggle (port of iOS `VoiceInputButton`): pulses gently while
 * recording. Compose follows the system's animation scale, so it stays still when she has
 * turned animations off.
 */
@Composable
internal fun VoiceInputButton(isRecording: Boolean, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    val pulse = if (isRecording) {
        rememberInfiniteTransition(label = "micPulse").animateFloat(
            1f, 1.12f, infiniteRepeatable(tween(500, easing = EaseInOut), RepeatMode.Reverse), label = "micScale",
        ).value
    } else 1f
    Box(
        Modifier
            .size(40.dp)
            .scale(pulse)
            .clip(CircleShape)
            .background(if (isRecording) theme.accentTint else theme.inputBackground)
            .clickable(role = Role.Button) { haptic(KeelHapticKind.LIGHT); onClick() }
            .semantics { contentDescription = if (isRecording) "Stop recording" else "Voice input" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(KeelIcons.mic, null, tint = if (isRecording) theme.accent else theme.muted, modifier = Modifier.size(20.dp))
    }
}
