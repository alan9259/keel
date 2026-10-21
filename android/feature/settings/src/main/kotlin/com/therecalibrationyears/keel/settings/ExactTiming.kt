package com.therecalibrationyears.keel.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.platform.ExactAlarms
import com.therecalibrationyears.keel.platform.ReminderChannels

/** Whether reminders arrive on time and can show at all, read again whenever she comes back. */
data class ReminderDelivery(val onTime: Boolean, val notificationsAllowed: Boolean)

@Composable
fun rememberReminderDelivery(): ReminderDelivery {
    val context = LocalContext.current
    fun read() = ReminderDelivery(ExactAlarms.allowed(context), ReminderChannels.enabled(context))
    var delivery by remember { mutableStateOf(read()) }
    // She may have changed either in Android's settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { delivery = read() }
    return delivery
}

/**
 * "Reminders on time" (Android only, plan D25): shown once, after she saves a medicine whose
 * reminders Android would deliver late. It leads to Android's own page, so "Continue" is its
 * only button (the pre-permission rule); the choice is hers on that page.
 */
@Composable
fun ExactTimingDialog(onContinue: () -> Unit) {
    val theme = LocalKeelTheme.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = theme.card,
        title = { Text("Reminders on time", style = KeelFont.serif(20.sp), color = theme.heading) },
        text = {
            Text(
                "Android can hold reminders back by up to an hour to save battery. To get yours on time, allow Keel to set alarms and reminders on the next screen.",
                style = KeelFont.body, color = theme.text,
            )
        },
        confirmButton = {
            TextButton(onClick = { onContinue(); ExactAlarms.openSettings(context) }) {
                Text("Continue", style = KeelFont.sans(15.sp, FontWeight.Medium), color = theme.accent)
            }
        },
    )
}

/** The Reminders screen's note while Android may deliver late; a tap opens Android's page. */
@Composable
fun ExactTimingNote(modifier: Modifier = Modifier) {
    val theme = LocalKeelTheme.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(Radius.card)
    Column(
        modifier.fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape)
            .clickable(role = Role.Button) { ExactAlarms.openSettings(context) }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Reminders may arrive late", style = KeelFont.bodyLarge, color = theme.text)
        Text(
            "Android can hold them back by up to an hour to save battery. To get them on time, allow Keel to set alarms and reminders.",
            style = KeelFont.caption, color = theme.muted,
        )
        Text("Open Settings", Modifier.padding(top = 6.dp), style = KeelFont.sans(15.sp, FontWeight.Medium), color = theme.accent)
    }
}
