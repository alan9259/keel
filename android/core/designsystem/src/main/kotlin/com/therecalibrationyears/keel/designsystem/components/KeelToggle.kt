package com.therecalibrationyears.keel.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme

/**
 * A labelled switch tinted from the theme (port of a SwiftUI `Toggle` with `.tint(accent)`).
 * The whole row is the control, so TalkBack reads the title, detail and state together.
 */
@Composable
fun KeelToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    titleStyle: TextStyle = KeelFont.body,
) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch) { haptic(KeelHapticKind.SELECTION); onCheckedChange(it) },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = titleStyle, color = theme.text)
            if (detail != null) Text(detail, style = KeelFont.sans(12.sp), color = theme.muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = null, // the row handles the tap
            colors = SwitchDefaults.colors(
                checkedThumbColor = theme.onFill,
                checkedTrackColor = theme.accent,
                checkedBorderColor = theme.accent,
                uncheckedThumbColor = theme.muted,
                uncheckedTrackColor = theme.track,
                uncheckedBorderColor = theme.border,
            ),
        )
    }
}
