package com.therecalibrationyears.keel.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.therecalibrationyears.keel.designsystem.KeelDateFormats
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import com.therecalibrationyears.keel.designsystem.components.KeelSegmented
import com.therecalibrationyears.keel.designsystem.components.KeelTimeDialog
import com.therecalibrationyears.keel.domain.DoseSlot
import com.therecalibrationyears.keel.domain.ExactTiming
import com.therecalibrationyears.keel.domain.ReminderCatalog
import com.therecalibrationyears.keel.domain.ReminderConfig
import com.therecalibrationyears.keel.domain.ReminderDef
import com.therecalibrationyears.keel.domain.ReminderKinds
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** What the Reminders screen asks of the app. */
interface RemindersActions {
    val enabledIds: Flow<Set<String>>
    val config: Flow<ReminderConfig>
    val notificationPermissionAsked: Flow<Boolean>
    suspend fun markNotificationPermissionAsked()
    /** Turns one on (laying it out) or off (clearing it). Called once notifications can show. */
    suspend fun setEnabled(id: String, on: Boolean)
    /** New timing; the reminders she has on follow it. */
    suspend fun setConfig(config: ReminderConfig)
}

/** Port of iOS `RemindersView`: gentle nudges, each one hers to turn on and time. */
@Composable
fun RemindersScreen(actions: RemindersActions, onBack: () -> Unit) {
    val context = LocalContext.current
    val theme = LocalKeelTheme.current
    val scope = rememberCoroutineScope()
    val haptic = KeelHaptics.rememberPerformer()
    val formats = remember { KeelDateFormats(context, zone = ZoneId.systemDefault()) }
    val enabled by actions.enabledIds.collectAsStateWithLifecycle(emptySet())
    val config by actions.config.collectAsStateWithLifecycle(ReminderConfig())
    val asked by actions.notificationPermissionAsked.collectAsStateWithLifecycle(false)
    val delivery = rememberReminderDelivery()
    var showBlocked by remember { mutableStateOf(false) }
    val access = rememberNotificationAccess(onAsked = actions::markNotificationPermissionAsked, onBlocked = { showBlocked = true })

    fun setEnabled(def: ReminderDef, on: Boolean) {
        haptic(KeelHapticKind.SELECTION)
        if (!on) {
            scope.launch { actions.setEnabled(def.id, false) }
            return
        }
        access.whenAllowed(asked) { scope.launch { actions.setEnabled(def.id, true) } }
    }

    fun update(change: (ReminderConfig) -> ReminderConfig) { scope.launch { actions.setConfig(change(config)) } }

    KeelScreen(title = "Reminders", subtitle = "Gentle nudges, on your terms", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ExactTiming.showsNote(delivery.onTime, delivery.notificationsAllowed, anyReminderOn = enabled.isNotEmpty())) {
                ExactTimingNote()
            }
            ReminderCatalog.all.forEach { def ->
                ReminderCard(def, icon(def.id), def.id in enabled, onToggle = { setEnabled(def, it) }) {
                    Editor(def.id, config, formats, ::update)
                }
            }
            Text(
                "Reminders respect your device's Do Not Disturb settings.",
                Modifier.fillMaxWidth().padding(top = 8.dp),
                style = KeelFont.caption.copy(textAlign = TextAlign.Center),
                color = theme.muted,
            )
        }
    }

    if (showBlocked) {
        NotificationsBlockedDialog("Notifications are turned off for Keel. To get reminders, turn them on in Settings.") { showBlocked = false }
    }
}

private fun icon(id: String): ImageVector = when (id) {
    ReminderKinds.DAILY_CHECK_IN -> KeelIcons.sun
    ReminderKinds.MEDICATION -> KeelIcons.pill
    ReminderKinds.HYDRATION -> KeelIcons.waterDrop
    ReminderKinds.MOVEMENT -> KeelIcons.walk
    else -> KeelIcons.moon
}

@Composable
private fun ReminderCard(def: ReminderDef, icon: ImageVector, isOn: Boolean, onToggle: (Boolean) -> Unit, editor: @Composable () -> Unit) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Column(Modifier.fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape)) {
        Row(
            Modifier.fillMaxWidth().toggleable(value = isOn, role = Role.Switch, onValueChange = onToggle).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (isOn) theme.accent.copy(alpha = 0.12f) else theme.inputBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (isOn) theme.accent else theme.muted, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(def.label, style = KeelFont.bodyLarge, color = theme.text)
                Text(def.desc, style = KeelFont.caption, color = theme.muted)
            }
            Switch(
                checked = isOn,
                onCheckedChange = null, // the row handles the tap
                colors = SwitchDefaults.colors(
                    checkedThumbColor = theme.onFill, checkedTrackColor = theme.accent, checkedBorderColor = theme.accent,
                    uncheckedThumbColor = theme.muted, uncheckedTrackColor = theme.track, uncheckedBorderColor = theme.border,
                ),
            )
        }
        if (isOn) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
            Column(Modifier.fillMaxWidth().background(theme.track.copy(alpha = 0.35f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                editor()
            }
        }
    }
}

@Composable
private fun Editor(id: String, c: ReminderConfig, formats: KeelDateFormats, update: ((ReminderConfig) -> ReminderConfig) -> Unit) {
    when (id) {
        ReminderKinds.DAILY_CHECK_IN -> TimeRow("Reminder time", c.checkInHour, c.checkInMinute, formats) { h, m -> update { it.copy(checkInHour = h, checkInMinute = m) } }
        ReminderKinds.HYDRATION -> {
            HourRow("Start", c.hydrationStartHour) { h -> update { it.copy(hydrationStartHour = h) } }
            HourRow("End", c.hydrationEndHour) { h -> update { it.copy(hydrationEndHour = h) } }
            IntervalRow(c.hydrationIntervalHours) { n -> update { it.copy(hydrationIntervalHours = n) } }
        }
        ReminderKinds.MOVEMENT -> {
            TimeRow("Reminder time", c.movementHour, c.movementMinute, formats) { h, m -> update { it.copy(movementHour = h, movementMinute = m) } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label("Days", Modifier.weight(1f))
                KeelSegmented(
                    options = listOf("Weekdays", "Every day"),
                    selection = if (c.movementWeekdaysOnly) 0 else 1,
                    onSelect = { i -> update { it.copy(movementWeekdaysOnly = i == 0) } },
                    modifier = Modifier.width(190.dp),
                )
            }
        }
        ReminderKinds.WIND_DOWN -> TimeRow("Reminder time", c.windDownHour, c.windDownMinute, formats) { h, m -> update { it.copy(windDownHour = h, windDownMinute = m) } }
        ReminderKinds.MEDICATION -> {
            DetailRow("Reminder time", "Per item")
            DetailRow("Set in", "Each entry")
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = KeelFont.body, color = LocalKeelTheme.current.muted)
}

@Composable
private fun ValueChip(text: String, onClick: (() -> Unit)?, label: String? = null) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.heightIn(min = 36.dp).clip(shape).background(theme.card).border(1.dp, theme.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = label, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KeelFont.body, color = if (onClick != null) theme.accent else theme.text)
    }
}

@Composable
private fun TimeRow(label: String, hour: Int, minute: Int, formats: KeelDateFormats, onPick: (Int, Int) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(label, Modifier.weight(1f))
        ValueChip(formats.time(DoseSlot(hour = hour, minute = minute)), onClick = { picking = true }, label = label)
    }
    if (picking) KeelTimeDialog(hour, minute, onDismiss = { picking = false }) { h, m -> picking = false; onPick(h, m) }
}

/** Whole hours (the hydration window steps by the hour). */
@Composable
private fun HourRow(label: String, hour: Int, onPick: (Int) -> Unit) {
    val theme = LocalKeelTheme.current
    var open by remember { mutableStateOf(false) }
    fun text(h: Int) = String.format(Locale.ROOT, "%02d:00", h)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(label, Modifier.weight(1f))
        Box {
            ValueChip(text(hour), onClick = { open = true }, label = label)
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = theme.card) {
                (0..23).forEach { h ->
                    DropdownMenuItem(
                        text = { Text(text(h), style = KeelFont.body, color = if (h == hour) theme.accent else theme.text) },
                        onClick = { open = false; onPick(h) },
                    )
                }
            }
        }
    }
}

/** "Every 2 hours", one to six. */
@Composable
private fun IntervalRow(hours: Int, onChange: (Int) -> Unit) {
    val theme = LocalKeelTheme.current
    val shown = "Every $hours hour${if (hours == 1) "" else "s"}"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("Remind me", Modifier.weight(1f))
        StepButton(KeelIcons.minus, "Decrease", hours > 1) { onChange(hours - 1) }
        Text(shown, Modifier.widthIn(min = 112.dp).semantics { stateDescription = shown }, style = KeelFont.body.copy(textAlign = TextAlign.Center), color = theme.text)
        StepButton(KeelIcons.plus, "Increase", hours < 6) { onChange(hours + 1) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(theme.card)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) theme.text else theme.muted.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(label, Modifier.weight(1f))
        ValueChip(value, onClick = null)
    }
}

