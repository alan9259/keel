package com.therecalibrationyears.keel.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.domain.NotificationGate
import com.therecalibrationyears.keel.domain.NotificationGates
import com.therecalibrationyears.keel.platform.ReminderChannels
import kotlinx.coroutines.launch

/**
 * Turning a reminder on (port of the iOS gate in `RemindersView` and `SettingsView`): go ahead
 * if Android will show notifications; ask on Android 13+ while it still can; otherwise she has
 * said no and Android won't ask again, so [onBlocked] points her to Settings. [onAllowed] runs
 * only once notifications can actually show, so a toggle never sits "on" with nothing firing.
 */
class NotificationAccess internal constructor(private val check: (alreadyAsked: Boolean, onAllowed: () -> Unit) -> Unit) {
    fun whenAllowed(alreadyAsked: Boolean, onAllowed: () -> Unit) = check(alreadyAsked, onAllowed)
}

@Composable
fun rememberNotificationAccess(onAsked: suspend () -> Unit, onBlocked: () -> Unit): NotificationAccess {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // What to do once she answers Android's prompt (kept across recompositions).
    val pending = remember { mutableListOf<() -> Unit>() }
    val latestBlocked by rememberUpdatedState(onBlocked)
    val latestAsked by rememberUpdatedState(onAsked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val next = pending.lastOrNull()
        pending.clear()
        // Decide by what Android will actually show, not just the permission answer.
        if (ReminderChannels.enabled(context)) next?.invoke() else latestBlocked()
    }
    return remember(context, launcher) { NotificationAccess { alreadyAsked, onAllowed ->
        val activity = context.findActivity()
        val gate = NotificationGates.gate(
            enabled = ReminderChannels.enabled(context),
            sdk = Build.VERSION.SDK_INT,
            alreadyAsked = alreadyAsked,
            systemWillAsk = Build.VERSION.SDK_INT >= 33 && activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true,
        )
        when (gate) {
            NotificationGate.PROCEED -> onAllowed()
            NotificationGate.BLOCKED -> latestBlocked()
            NotificationGate.REQUEST -> {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    pending.clear(); pending += onAllowed
                    scope.launch { latestAsked() }
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    latestBlocked() // allowed, but switched off for Keel in Settings
                }
            }
        }
    } }
}

/** "Turn on notifications": they're off for Keel, and only Settings can turn them back on. */
@Composable
fun NotificationsBlockedDialog(message: String, onDismiss: () -> Unit) {
    val theme = LocalKeelTheme.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        title = { Text("Turn on notifications", style = KeelFont.serif(20.sp), color = theme.heading) },
        text = { Text(message, style = KeelFont.body, color = theme.text) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); context.openNotificationSettings() }) {
                Text("Open Settings", style = KeelFont.sans(15.sp, FontWeight.Medium), color = theme.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now", style = KeelFont.sans(15.sp), color = theme.muted) } },
    )
}

private fun Context.openNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
