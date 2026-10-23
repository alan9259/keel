package com.therecalibrationyears.keel.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.KeelTheme
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.Spacing

/** Pill segmented control (port of iOS `KeelSegmented`). */
@Composable
fun KeelSegmented(
    options: List<String>,
    selection: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selection
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) theme.card else Color.Transparent)
                    .selectable(selected, role = Role.Tab) { haptic(KeelHapticKind.LIGHT); onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option, style = KeelFont.sans(14.sp, FontWeight.Medium), color = if (selected) theme.text else theme.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Severity labels, as iOS `SymptomSeverity.label`. */
private val severityLabels = mapOf(1 to "Mild", 2 to "Moderate", 3 to "Severe")

/**
 * Energy fill from the theme (port of iOS `EnergyLevel.color(in:)`): accent (drained) through
 * copper and sand to sage (charged). No red.
 */
fun energyColour(level: Int, theme: KeelTheme): Color = when (level) {
    1 -> theme.accent
    2 -> theme.copper
    3 -> theme.sand
    4 -> theme.sageSoft
    else -> theme.sage
}

/** Severity fill from the theme's warm ramp: mild sand, moderate copper, severe accent. */
fun severityColour(level: Int, theme: KeelTheme): Color? = when (level) {
    1 -> theme.sand
    2 -> theme.copper
    3 -> theme.accent
    else -> null
}

/**
 * Symptom pill with a severity level (port of iOS `SymptomChip`): 0 unselected, 1 to 3
 * filled from the warm ramp with that many white dots.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SymptomChip(
    title: String,
    level: Int,
    modifier: Modifier = Modifier,
    /** Press and hold (the picker uses it to start dragging the chip to another group). */
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    val fill = severityColour(level, theme)
    Row(
        modifier
            .minimumInteractiveComponentSize() // a 48 dp target around the pill
            .clip(CircleShape)
            .background(fill ?: theme.card)
            .border(1.dp, if (fill != null) Color.Transparent else theme.border, CircleShape)
            .combinedClickable(role = Role.Button, onLongClick = onLongClick) { haptic(KeelHapticKind.SELECTION); onClick() }
            .semantics {
                selected = fill != null
                stateDescription = severityLabels[level] ?: "Not selected"
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = KeelFont.body, color = if (fill != null) theme.onFill else theme.text.copy(alpha = 0.85f))
        if (fill != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(level) { Box(Modifier.size(4.dp).clip(CircleShape).background(theme.onFill.copy(alpha = 0.9f))) }
            }
        }
    }
}

/** Selectable card with a leading radio indicator (port of iOS `RadioCard`). */
@Composable
fun RadioCard(
    title: String,
    description: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    val shape = RoundedCornerShape(Radius.card)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) theme.accent.copy(alpha = 0.05f) else theme.card)
            .border(2.dp, if (isSelected) theme.accent else theme.border, shape)
            // TalkBack announces the selected state itself (iOS uses the .isSelected trait).
            .selectable(isSelected, role = Role.RadioButton) { haptic(KeelHapticKind.SELECTION); onClick() }
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isSelected) theme.accent else Color.Transparent)
                .border(2.dp, if (isSelected) theme.accent else theme.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) Icon(KeelIcons.check, contentDescription = null, tint = theme.background, modifier = Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = KeelFont.serif(17.sp), color = theme.heading)
            Text(description, style = KeelFont.body, color = theme.text.copy(alpha = 0.7f))
        }
    }
}
