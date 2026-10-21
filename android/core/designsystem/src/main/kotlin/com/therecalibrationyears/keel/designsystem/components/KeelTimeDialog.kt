package com.therecalibrationyears.keel.designsystem.components

import android.text.format.DateFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme

/**
 * Picking a time of day, in her 12 or 24 hour setting (the Android counterpart of an iOS
 * compact `DatePicker` for hour and minute), tinted from the theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeelTimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onPick: (hour: Int, minute: Int) -> Unit) {
    val theme = LocalKeelTheme.current
    val context = LocalContext.current
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        confirmButton = { TextButton(onClick = { onPick(state.hour, state.minute) }) { Text("Done", style = KeelFont.sans(15.sp, FontWeight.Medium), color = theme.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = KeelFont.sans(15.sp), color = theme.muted) } },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = theme.track,
                    selectorColor = theme.accent,
                    containerColor = theme.card,
                    clockDialSelectedContentColor = theme.onFill,
                    clockDialUnselectedContentColor = theme.text,
                    periodSelectorBorderColor = theme.border,
                    periodSelectorSelectedContainerColor = theme.accent.copy(alpha = 0.15f),
                    periodSelectorUnselectedContainerColor = theme.card,
                    periodSelectorSelectedContentColor = theme.accent,
                    periodSelectorUnselectedContentColor = theme.text,
                    timeSelectorSelectedContainerColor = theme.accent.copy(alpha = 0.15f),
                    timeSelectorUnselectedContainerColor = theme.track,
                    timeSelectorSelectedContentColor = theme.accent,
                    timeSelectorUnselectedContentColor = theme.text,
                ),
            )
        },
    )
}
