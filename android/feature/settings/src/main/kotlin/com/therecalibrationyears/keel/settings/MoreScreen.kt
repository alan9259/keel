package com.therecalibrationyears.keel.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.KeelTheme
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import com.therecalibrationyears.keel.domain.FeedbackMail
import com.therecalibrationyears.keel.domain.ShareInvite
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * The app's version and support context (iOS `DeviceContext`): the version under More, and
 * the line at the foot of a feedback draft that helps us see what she sees.
 */
data class AppInfo(val shortVersion: String, val appVersion: String, val osVersion: String, val deviceModel: String)

/** Where More's rows lead (iOS `MainRoute` cases reached from More). */
enum class MoreDestination {
    PROFILE, APP_LOCK, SETTINGS, ACTIVITIES, REMINDERS, STATS, MOOD_ICONS, THEMES, COLOUR_MODE, BACKUP, HEALTH, SUPPORT, CONNECT, ABOUT,
}

private class MoreItem(val title: String, val icon: ImageVector, val tint: (KeelTheme) -> Color, val onClick: () -> Unit)

/** Port of iOS `MoreView`: sectioned rows to each feature, or, for feedback, a mail draft. */
@Composable
fun MoreScreen(info: AppInfo, onOpen: (MoreDestination) -> Unit, onBack: () -> Unit) {
    val theme = LocalKeelTheme.current
    val context = LocalContext.current
    var showNoMailApp by remember { mutableStateOf(false) }

    fun compose(kind: FeedbackMail.Kind) {
        if (!context.openMailDraft(kind, info)) showNoMailApp = true
    }

    fun item(title: String, icon: ImageVector, tint: (KeelTheme) -> Color, to: MoreDestination) = MoreItem(title, icon, tint) { onOpen(to) }

    val sections = listOf(
        // Her own things first: profile, app lock and settings.
        "Profile/Settings" to listOf(
            item("Profile", KeelIcons.person, { it.accent }, MoreDestination.PROFILE),
            item("App lock", KeelIcons.lock, { it.plum }, MoreDestination.APP_LOCK),
            item("Settings", KeelIcons.gear, { it.muted }, MoreDestination.SETTINGS),
        ),
        // Cycle Tracking and the GP Visit Summary both live on Home, so they aren't repeated here.
        "Tracking" to listOf(
            item("Activities", KeelIcons.walk, { it.sage }, MoreDestination.ACTIVITIES),
            item("Reminders", KeelIcons.bell, { it.accent }, MoreDestination.REMINDERS),
            item("Stats", KeelIcons.barChart, { it.plum }, MoreDestination.STATS),
        ),
        "Personalisation" to listOf(
            item("Mood Icons", KeelIcons.smile, { it.accent }, MoreDestination.MOOD_ICONS),
            item("Themes", KeelIcons.palette, { it.plum }, MoreDestination.THEMES),
            item("Colour Mode", KeelIcons.halfCircle, { it.heading }, MoreDestination.COLOUR_MODE),
        ),
        "Data" to listOf(
            item("Backup & Restore", KeelIcons.cloud, { it.accent }, MoreDestination.BACKUP),
            item("Health Connect", KeelIcons.heart, { it.accent }, MoreDestination.HEALTH),
        ),
        "Feedback" to listOf(
            // A pre-filled draft in her own mail app.
            MoreItem("Share feedback", KeelIcons.mail, { it.accent }) { compose(FeedbackMail.Kind.FEEDBACK) },
            MoreItem("Request a feature", KeelIcons.lightbulb, { it.plum }) { compose(FeedbackMail.Kind.FEATURE_REQUEST) },
        ),
        "Support & about" to listOf(
            item("Get Support", KeelIcons.lifebuoy, { it.accent }, MoreDestination.SUPPORT),
            item("Connect with Us", KeelIcons.chatBubbles, { it.sage }, MoreDestination.CONNECT),
            MoreItem("Tell your friends", KeelIcons.share, { it.accent }) { context.shareInvite() },
            item("About Keel", KeelIcons.info, { it.muted }, MoreDestination.ABOUT),
        ),
    )

    KeelScreen(title = "More", onBack = onBack) {
        sections.forEach { (title, items) -> MoreSection(title, items) }
        Text(
            "Keel · Version ${info.shortVersion}",
            Modifier.fillMaxWidth(),
            style = KeelFont.caption.copy(textAlign = TextAlign.Center),
            color = theme.muted,
        )
    }

    if (showNoMailApp) NoMailAppDialog { showNoMailApp = false }
}

@Composable
private fun MoreSection(title: String, items: List<MoreItem>) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.lg)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionEyebrow(title, Modifier.padding(start = 4.dp))
        Column(Modifier.fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape)) {
            items.forEachIndexed { index, item ->
                MoreRow(item)
                if (index < items.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
            }
        }
    }
}

/** A section label: uppercase, tracked, muted (iOS `KeelFont.eyebrow`). */
@Composable
internal fun SectionEyebrow(title: String, modifier: Modifier = Modifier, tracking: TextUnit = 1.2.sp) {
    Text(title.uppercase(), modifier, style = KeelFont.eyebrow.copy(letterSpacing = tracking), color = LocalKeelTheme.current.muted)
}

@Composable
private fun MoreRow(item: MoreItem) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    val tint = item.tint(theme)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { haptic(KeelHapticKind.LIGHT); item.onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
        Text(item.title, Modifier.weight(1f), style = KeelFont.bodyLarge, color = theme.text)
        Icon(KeelIcons.chevronRight, contentDescription = null, tint = theme.muted, modifier = Modifier.size(20.dp))
    }
}

/** No mail app could take the draft: offer the address to copy, so she can write from anywhere. */
@Composable
internal fun NoMailAppDialog(onDismiss: () -> Unit) {
    val theme = LocalKeelTheme.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        title = { Text("No email app set up", style = KeelFont.serif(20.sp), color = theme.heading) },
        text = {
            Text(
                "We couldn't find an email app to open. You can copy our address and write to us from anywhere: ${FeedbackMail.ADDRESS}",
                style = KeelFont.body, color = theme.text,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(FeedbackMail.ADDRESS, FeedbackMail.ADDRESS))) }
                onDismiss()
            }) { Text("Copy email address", style = KeelFont.sans(15.sp, FontWeight.Medium), color = theme.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("OK", style = KeelFont.sans(15.sp), color = theme.muted) } },
    )
}

/**
 * Open a pre-filled draft in her default mail app (Gmail, Outlook, Proton, ...). False if
 * nothing on the phone handles email, so the caller offers the address instead.
 */
internal fun Context.openMailDraft(kind: FeedbackMail.Kind, info: AppInfo): Boolean {
    val uri = FeedbackMail.uri(kind, info.appVersion, info.osVersion, info.deviceModel)
    val intent = Intent(Intent.ACTION_SENDTO, uri.toUri())
        // Some mail apps read the extras rather than the mailto query; give both.
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(FeedbackMail.ADDRESS))
        .putExtra(Intent.EXTRA_SUBJECT, kind.subject)
        .putExtra(Intent.EXTRA_TEXT, FeedbackMail.body(kind, info.appVersion, info.osVersion, info.deviceModel))
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** "Tell your friends": the system share sheet with the note and the link. */
private fun Context.shareInvite() {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, ShareInvite.text) // not-copy: MIME type
    startActivity(Intent.createChooser(send, null))
}
