package com.therecalibrationyears.keel.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therecalibrationyears.keel.designsystem.KeelDateFormats
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A backup file she chose, before she confirms replacing her data with it. */
data class BackupPreview(val checkIns: Int, val symptoms: Int, val medications: Int, val exportedAt: Instant)

/** What a restore brought back (iOS `BackupService.Summary`, the parts shown). */
data class RestoreSummary(val checkIns: Int, val medications: Int, val cycleEntries: Int)

/** How a restore ended: done, or refused with the reason to show (a newer format). */
sealed interface RestoreOutcome {
    data class Done(val summary: RestoreSummary) : RestoreOutcome
    data class Refused(val message: String?) : RestoreOutcome
}

/** What the Backup & Restore screen asks of the app. */
interface BackupActions {
    val zone: ZoneId
    /** `Keel-backup-2026-10-22-1045.keelbackup` (plan D17). */
    fun fileName(): String
    /** Everything Keel holds, as the file's bytes. */
    suspend fun exportData(): ByteArray
    /** Read a file she chose; null if it isn't a Keel backup. The app keeps it for [restorePending]. */
    suspend fun read(bytes: ByteArray): BackupPreview?
    /** Replace all her data with the file last read. */
    suspend fun restorePending(): RestoreOutcome
    fun discardPending()
}

private enum class RestoreStage { IDLE, CONFIRM, DONE }

/**
 * Port of iOS `BackupRestoreView`. Export saves a `.keelbackup` wherever she picks (Android's
 * "Save to" picker in place of the iOS share sheet, plan D17); restore reads one she picks, then
 * asks before replacing everything.
 */
@Composable
fun BackupRestoreScreen(actions: BackupActions, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = KeelHaptics.rememberPerformer()
    val formats = remember(actions.zone) { KeelDateFormats(context, zone = actions.zone) }
    var stage by remember { mutableStateOf(RestoreStage.IDLE) }
    var pending by remember { mutableStateOf<BackupPreview?>(null) }
    var summary by remember { mutableStateOf<RestoreSummary?>(null) }
    // Null: no alert. A blank message shows the title alone.
    var error by remember { mutableStateOf<String?>(null) }

    val saveTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult // she backed out of the picker
        scope.launch {
            runCatching { context.write(uri, actions.exportData()) }.onFailure { error = "" }
        }
    }
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val preview = runCatching { actions.read(context.read(uri)) }.getOrNull()
            if (preview == null) {
                error = "That file isn't a valid Keel backup."
            } else {
                pending = preview
                stage = RestoreStage.CONFIRM
            }
        }
    }

    KeelScreen(title = "Backup & Restore", subtitle = "Your data, safely yours", onBack = onBack) {
        SaveSection(onExport = { saveTo.launch(actions.fileName()) })
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading("Restore")
            when (stage) {
                RestoreStage.IDLE -> RestoreRow { choose.launch(arrayOf("*/*")) } // not-copy: MIME filter (.keelbackup has no registered type)
                RestoreStage.CONFIRM -> ConfirmCard(
                    detail = pending?.let {
                        "This restores ${it.checkIns} check-ins, ${it.symptoms} symptoms and ${it.medications} medications from a backup made ${formats.dateAndTime(it.exportedAt)}. Your current data will be overwritten."
                    } ?: "Your current data will be overwritten.",
                    onCancel = { stage = RestoreStage.IDLE; pending = null; actions.discardPending() },
                    onRestore = {
                        scope.launch {
                            when (val outcome = runCatching { actions.restorePending() }.getOrElse { RestoreOutcome.Refused(null) }) {
                                is RestoreOutcome.Done -> {
                                    haptic(KeelHapticKind.SUCCESS)
                                    summary = outcome.summary
                                    pending = null
                                    stage = RestoreStage.DONE
                                }
                                is RestoreOutcome.Refused -> error = outcome.message.orEmpty()
                            }
                        }
                    },
                )
                RestoreStage.DONE -> DoneBanner(summary) { stage = RestoreStage.IDLE; summary = null }
            }
        }
    }

    error?.let { message ->
        val theme = LocalKeelTheme.current
        AlertDialog(
            onDismissRequest = { error = null },
            containerColor = theme.card,
            title = { Text("Couldn't complete", style = KeelFont.serif(20.sp), color = theme.heading) },
            text = if (message.isEmpty()) null else ({ Text(message, style = KeelFont.body, color = theme.text) }),
            confirmButton = {
                TextButton(onClick = { error = null }) { Text("OK", style = KeelFont.sans(15.sp, FontWeight.Medium), color = theme.accent) }
            },
        )
    }
}

@Composable
private fun SectionHeading(text: String) =
    Text(text, Modifier.semantics { heading() }, style = KeelFont.serif(18.sp), color = LocalKeelTheme.current.heading)

@Composable
private fun SaveSection(onExport: () -> Unit) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading("Save a backup")
        Column(Modifier.fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape)) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(KeelIcons.smartphone, tint = theme.text, background = theme.track)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Save to a file", style = KeelFont.body, color = theme.text)
                    Text("Export everything as a .keelbackup file you keep", style = KeelFont.caption, color = theme.muted)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onExport).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(KeelIcons.download, contentDescription = null, tint = theme.accent, modifier = Modifier.size(20.dp))
                Text("Export data", style = KeelFont.body, color = theme.accent)
            }
        }
        Text(
            "Your data stays on this device. A backup file is yours to keep and store wherever you like.",
            Modifier.padding(horizontal = 4.dp), style = KeelFont.caption, color = theme.muted,
        )
    }
}

@Composable
private fun IconTile(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, background: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RestoreRow(onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(KeelIcons.uploadFile, tint = theme.accent, background = theme.accent.copy(alpha = 0.1f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Restore from a file", style = KeelFont.body, color = theme.text)
            Text("Choose a .keelbackup file you exported", style = KeelFont.caption, color = theme.muted)
        }
        Icon(KeelIcons.chevronRight, contentDescription = null, tint = theme.muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ConfirmCard(detail: String, onCancel: () -> Unit, onRestore: () -> Unit) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    val buttonShape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(theme.attentionTint).border(1.dp, theme.attention.copy(alpha = 0.25f), shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(KeelIcons.warning, contentDescription = null, tint = theme.attention, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Replace all current data?", style = KeelFont.body.copy(fontWeight = FontWeight.Medium), color = theme.text)
                Text(detail, style = KeelFont.caption, color = theme.muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.weight(1f).clip(buttonShape).border(1.dp, theme.border, buttonShape)
                    .clickable(role = Role.Button, onClick = onCancel).padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Cancel", style = KeelFont.body, color = theme.text) }
            Box(
                Modifier.weight(1f).clip(buttonShape).background(theme.attention)
                    .clickable(role = Role.Button, onClick = onRestore).padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Yes, restore", style = KeelFont.body, color = theme.onFill) }
        }
    }
}

@Composable
private fun DoneBanner(summary: RestoreSummary?, onDone: () -> Unit) {
    val theme = LocalKeelTheme.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(theme.sageTint).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KeelIcons.checkCircle, contentDescription = null, tint = theme.sage, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Data restored", style = KeelFont.body, color = theme.text)
            if (summary != null) {
                Text("${summary.checkIns} check-ins · ${summary.medications} meds · ${summary.cycleEntries} cycle entries", style = KeelFont.caption, color = theme.muted)
            }
        }
        Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onDone).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text("Done", style = KeelFont.caption, color = theme.sage)
        }
    }
}

private suspend fun Context.write(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
    // "wt": replace whatever the file held (some providers keep old bytes past the end otherwise).
    contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) } // not-copy: file mode
}

private suspend fun Context.read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    contentResolver.openInputStream(uri)!!.use { it.readBytes() }
}
