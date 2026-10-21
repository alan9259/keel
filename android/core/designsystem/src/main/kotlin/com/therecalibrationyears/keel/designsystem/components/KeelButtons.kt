package com.therecalibrationyears.keel.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.MinTouchTarget
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.Spacing

/** Press feedback (port of iOS `PressableStyle`): scale 0.97 and 90% opacity while pressed. */
@Composable
private fun pressScale(source: MutableInteractionSource): Pair<Float, Float> {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "pressScale")
    return scale to (if (pressed) 0.9f else 1f)
}

/** Filled accent button (port of iOS `KeelPrimaryButton`). */
@Composable
fun KeelPrimaryButton(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    val source = remember { MutableInteractionSource() }
    val (scale, pressAlpha) = pressScale(source)
    val shape = RoundedCornerShape(Radius.card)
    Box(
        modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(if (isEnabled) pressAlpha else 0.5f)
            .clip(shape)
            .background(theme.accent)
            .clickable(source, indication = null, enabled = isEnabled, role = Role.Button) {
                haptic(KeelHapticKind.TAP); onClick()
            }
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, contentDescription = null, tint = theme.background, modifier = Modifier.size(20.dp))
            Text(title, style = KeelFont.button, color = theme.background)
        }
    }
}

/** Outlined button (port of iOS `KeelSecondaryButton`). */
@Composable
fun KeelSecondaryButton(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    val source = remember { MutableInteractionSource() }
    val (scale, pressAlpha) = pressScale(source)
    val shape = RoundedCornerShape(Radius.input)
    Box(
        modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(pressAlpha)
            .clip(shape)
            .border(BorderStroke(2.dp, theme.text.copy(alpha = 0.18f)), shape)
            .clickable(source, indication = null, role = Role.Button) { haptic(KeelHapticKind.LIGHT); onClick() }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, contentDescription = null, tint = theme.text, modifier = Modifier.size(20.dp))
            Text(title, style = KeelFont.button, color = theme.text)
        }
    }
}

/** Muted text link with an optional underlined emphasised suffix (port of iOS `KeelTextLink`). */
@Composable
fun KeelTextLink(
    text: String,
    modifier: Modifier = Modifier,
    emphasized: String? = null,
    onClick: () -> Unit,
) {
    val theme = LocalKeelTheme.current
    val label = buildAnnotatedString {
        withStyle(SpanStyle(color = theme.muted)) { append(text) }
        if (emphasized != null) {
            withStyle(SpanStyle(color = theme.text, textDecoration = TextDecoration.Underline)) { append(emphasized) }
        }
    }
    Box(
        modifier.heightIn(min = MinTouchTarget).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = KeelFont.body)
    }
}
