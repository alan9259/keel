package com.therecalibrationyears.keel.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.components.KeelPrimaryButton
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import com.therecalibrationyears.keel.designsystem.components.KeelToggleRow
import com.therecalibrationyears.keel.domain.HealthAvailability
import com.therecalibrationyears.keel.domain.HealthSyncCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** What the Health Connect screen asks of the app (iOS `AppleHealthSettingsView`'s calls on `env`). */
interface HealthConnectActions {
    /** She has connected (her profile's flag). */
    val connected: Flow<Boolean>
    /** Anything imported at all, live (the "no data yet" note). */
    val hasImportedData: Flow<Boolean>
    /** Items she has switched off (`keel.disabledHealthItems`). */
    val disabledItems: Flow<Set<String>>
    /** Whether Health Connect is on this phone and up to date; read again each time the screen shows. */
    suspend fun availability(): HealthAvailability
    /** Health Connect asks; returns whether she's now connected. */
    suspend fun connect(): Boolean
    /** Read now, past the 30-minute wait. */
    fun syncNow()
    suspend fun disconnect()
    suspend fun setSyncing(itemId: String, on: Boolean)
    /** "Remove imported Health Connect data": rows removed. */
    suspend fun removeImported(): Int
    /** Health Connect's page for what Keel may read. */
    fun manageAccess()
}

private val why = listOf(
    "Less to log" to "Sleep and activity flow in automatically.",
    "Alongside your record" to "Sleep, heart readings and periods sit next to what you log, labelled as from Health Connect.",
    "Read-only" to "Keel reads from Health Connect. It never writes anything back.",
    "Private by design" to "Data stays on your device and in Health Connect.",
)

/**
 * Port of iOS `AppleHealthSettingsView`, for Health Connect: connect, then choose what to sync,
 * sync now, disconnect, and remove what was imported. Keel only ever reads.
 */
@Composable
fun HealthConnectScreen(actions: HealthConnectActions, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = KeelHaptics.rememberPerformer()
    val connected by actions.connected.collectAsStateWithLifecycle(null)
    val hasImportedData by actions.hasImportedData.collectAsStateWithLifecycle(true)
    val disabled by actions.disabledItems.collectAsStateWithLifecycle(emptySet())
    var availability by remember { mutableStateOf<HealthAvailability?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var removeStatus by remember { mutableStateOf<String?>(null) }

    // Back from installing or updating Health Connect, or from its settings: look again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { availability = actions.availability() } }
    // Opening the screen refreshes, so a first import that came back empty fills in (iOS onAppear).
    LaunchedEffect(connected) { if (connected == true) actions.syncNow() }

    KeelScreen(title = "Health Connect", subtitle = "Let Keel and Health Connect work together", onBack = onBack) {
        val isConnected = connected ?: return@KeelScreen
        ConnectionCard(
            connected = isConnected,
            connecting = connecting,
            onSyncNow = { actions.syncNow(); haptic(KeelHapticKind.SUCCESS) },
            onDisconnect = { scope.launch { actions.disconnect(); haptic(KeelHapticKind.LIGHT) } },
        )
        if (isConnected) {
            if (!hasImportedData) NoDataNote(onManageAccess = actions::manageAccess)
            PermissionsSection(
                disabled = disabled,
                onToggle = { id, on -> scope.launch { actions.setSyncing(id, on) } },
                onRemove = { confirmRemove = true },
                removeStatus = removeStatus,
            )
        } else {
            KeelPrimaryButton(
                "Connect with Health Connect",
                isEnabled = !connecting && availability != HealthAvailability.UNAVAILABLE,
            ) {
                connecting = true
                scope.launch {
                    actions.connect()
                    connecting = false
                    haptic(KeelHapticKind.SUCCESS)
                }
            }
            if (availability == HealthAvailability.UNAVAILABLE) {
                Text("Health Connect isn't available on this phone.", Modifier.padding(horizontal = 4.dp), style = KeelFont.caption, color = LocalKeelTheme.current.muted)
            }
            WhySection()
        }
    }

    if (confirmRemove) {
        val theme = LocalKeelTheme.current
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = theme.card,
            title = { Text("Remove imported Health Connect data?", style = KeelFont.serif(20.sp), color = theme.heading) },
            text = {
                Text(
                    "This deletes the sleep, activity, vitals and periods imported from Health Connect on this device. It does not change Health Connect, and does not touch anything you entered in Keel yourself. Imported data returns on the next sync if you stay connected.",
                    style = KeelFont.body, color = theme.text,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        val removed = actions.removeImported()
                        removeStatus = if (removed > 0) "Removed imported Health Connect data." else "No imported data to remove."
                        haptic(KeelHapticKind.SUCCESS)
                    }
                }) { Text("Remove", style = KeelFont.button, color = theme.attention) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel", style = KeelFont.button, color = theme.text) } },
        )
    }
}

@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.semantics { heading() }, style = KeelFont.serif(18.sp, FontWeight.SemiBold), color = LocalKeelTheme.current.heading)

@Composable
private fun ConnectionCard(connected: Boolean, connecting: Boolean, onSyncNow: () -> Unit, onDisconnect: () -> Unit) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(theme.accent), contentAlignment = Alignment.Center) {
                Icon(KeelIcons.heart, contentDescription = null, tint = theme.onFill, modifier = Modifier.size(26.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // The badge moves under the name when large text leaves no room beside it.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Health Connect", style = KeelFont.bodyLarge, color = theme.text)
                    if (connected) {
                        Text(
                            "Connected",
                            Modifier.clip(RoundedCornerShape(50)).background(theme.sageTint).padding(horizontal = 8.dp, vertical = 2.dp),
                            style = KeelFont.sans(11.sp), color = theme.sage, maxLines = 1, softWrap = false,
                        )
                    }
                }
                Text("Keel reads from Health Connect to save you logging.", style = KeelFont.caption, color = theme.muted)
            }
        }
        if (connected) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("Sync now", tint = theme.sage, onClick = onSyncNow)
                OutlineButton("Disconnect", tint = theme.attention, onClick = onDisconnect)
            }
        } else if (connecting) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(theme.accent.copy(alpha = 0.7f)).padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Connecting…", style = KeelFont.button, color = theme.background) }
        }
    }
}

@Composable
private fun RowScope.OutlineButton(title: String, tint: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.weight(1f).heightIn(min = 48.dp).clip(shape).border(1.dp, tint.copy(alpha = 0.35f), shape)
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(title, style = KeelFont.sans(13.sp, FontWeight.Medium), color = tint) }
}

@Composable
private fun PermissionsSection(disabled: Set<String>, onToggle: (String, Boolean) -> Unit, onRemove: () -> Unit, removeStatus: String?) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            SectionHeading("What to sync", Modifier.weight(1f))
            Text("Read-only", style = KeelFont.caption, color = theme.muted)
        }
        Column(Modifier.fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape)) {
            HealthSyncCatalog.all.forEachIndexed { index, item ->
                KeelToggleRow(
                    title = item.label,
                    detail = item.desc,
                    checked = item.id !in disabled,
                    onCheckedChange = { onToggle(item.id, it) },
                    detailStyle = KeelFont.caption,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                )
                if (index < HealthSyncCatalog.all.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
            }
        }
        Text(
            "Switch off anything you would rather Keel didn't import. Turning one off stops new data coming in, and keeps what was already imported until you remove it below. You can also change what Keel can read in Health Connect: open Health Connect, then App permissions, then Keel. Keel only ever reads what you allow, and never writes back. Imported data is kept in a separate area on this phone, and only goes into a backup file if you export one.",
            Modifier.padding(horizontal = 4.dp).padding(top = 2.dp), style = KeelFont.caption, color = theme.muted,
        )
        val buttonShape = RoundedCornerShape(12.dp)
        Box(
            Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(min = 48.dp).clip(buttonShape)
                .border(1.dp, theme.attention.copy(alpha = 0.35f), buttonShape).clickable(role = Role.Button, onClick = onRemove)
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Remove imported Health Connect data", style = KeelFont.sans(13.sp, FontWeight.Medium), color = theme.attention) }
        if (removeStatus != null) Text(removeStatus, Modifier.padding(horizontal = 4.dp), style = KeelFont.caption, color = theme.muted)
    }
}

/** Connected, but nothing has come in: perhaps nothing is allowed in Health Connect yet. */
@Composable
private fun NoDataNote(onManageAccess: () -> Unit) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(theme.attention.copy(alpha = 0.08f))
            .border(1.dp, theme.attention.copy(alpha = 0.25f), shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(KeelIcons.info, contentDescription = null, tint = theme.attention, modifier = Modifier.padding(top = 1.dp).size(16.dp))
            Text(
                "Connected, but no Health Connect data has come in yet. If you expected some, open Health Connect, then App permissions, then Keel, and check what Keel is allowed to read.",
                style = KeelFont.caption, color = theme.text,
            )
        }
        val buttonShape = RoundedCornerShape(12.dp)
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(buttonShape).border(1.dp, theme.accent.copy(alpha = 0.4f), buttonShape)
                .clickable(role = Role.Button, onClick = onManageAccess).padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Manage access in Health Connect", style = KeelFont.sans(13.sp, FontWeight.Medium), color = theme.accent) }
    }
}

@Composable
private fun WhySection() {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.md)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading("Why connect?")
        why.forEach { (title, detail) ->
            Row(
                Modifier.fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(KeelIcons.heart, contentDescription = null, tint = theme.accent, modifier = Modifier.padding(top = 2.dp).size(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = KeelFont.body, color = theme.text)
                    Text(detail, style = KeelFont.caption, color = theme.muted)
                }
            }
        }
    }
}
