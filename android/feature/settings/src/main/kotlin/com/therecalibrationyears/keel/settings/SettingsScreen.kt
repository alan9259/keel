package com.therecalibrationyears.keel.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import com.therecalibrationyears.keel.designsystem.components.KeelToggleRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** What the Settings screen asks of the app. */
interface SettingsActions {
    val pushNotifications: Flow<Boolean>
    val haptics: Flow<Boolean>
    val medicineNamesInReminders: Flow<Boolean>
    val notesAlcohol: Flow<Boolean>
    val notificationPermissionAsked: Flow<Boolean>
    suspend fun markNotificationPermissionAsked()
    /** The master switch: on lays her reminders out, off clears every one. */
    suspend fun setPushNotificationsEnabled(on: Boolean)
    suspend fun setHaptics(on: Boolean)
    /** Names on the lock screen: the medication reminders are laid out again with or without. */
    suspend fun setMedicineNamesInReminders(on: Boolean)
    suspend fun setNotesAlcohol(on: Boolean)
    /**
     * "Delete all my data". Not suspending: it runs to the end in the app's scope, since the
     * screen asking for it is gone as soon as her profile is.
     */
    fun eraseAllData()
}

/** The word she types to confirm deleting everything. */
internal const val DELETE_WORD = "DELETE" // not-copy: shown in its own monospaced span below

/** Port of iOS `SettingsView`. */
@Composable
fun SettingsScreen(actions: SettingsActions, onOpenPrivacy: () -> Unit, onBack: () -> Unit) {
    val theme = LocalKeelTheme.current
    val scope = rememberCoroutineScope()
    val push by actions.pushNotifications.collectAsStateWithLifecycle(true)
    val haptics by actions.haptics.collectAsStateWithLifecycle(true)
    val medNames by actions.medicineNamesInReminders.collectAsStateWithLifecycle(false)
    val alcohol by actions.notesAlcohol.collectAsStateWithLifecycle(true)
    val asked by actions.notificationPermissionAsked.collectAsStateWithLifecycle(false)
    var showBlocked by remember { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    val access = rememberNotificationAccess(onAsked = actions::markNotificationPermissionAsked, onBlocked = { showBlocked = true })

    // The master switch only turns on once notifications can actually show, so it never sits
    // "on" while nothing can fire. If they're off for Keel, Android won't ask again: point her
    // to Settings instead.
    fun setPush(on: Boolean) {
        if (!on) {
            scope.launch { actions.setPushNotificationsEnabled(false) }
            return
        }
        access.whenAllowed(asked) { scope.launch { actions.setPushNotificationsEnabled(true) } }
    }

    KeelScreen(title = "Settings", onBack = onBack) {
        Group("Notifications") {
            SettingsToggle(KeelIcons.bell, "Push notifications", "Reminders and alerts", push, ::setPush)
            Divider()
            SettingsToggle(KeelIcons.vibration, "Haptic feedback", "Vibration on interactions", haptics) {
                scope.launch { actions.setHaptics(it) }
            }
            Divider()
            // Reminders show on the lock screen, so names are hers to turn on.
            SettingsToggle(KeelIcons.pill, "Medicine names in reminders", "Shown on your lock screen when on", medNames) {
                scope.launch { actions.setMedicineNamesInReminders(it) }
            }
        }

        // Optional check-in items she can choose to note. Alcohol is on by default.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Group("Check-in") {
                SettingsToggle(KeelIcons.wineGlass, "Alcohol", "Add a count to your check-in", alcohol) {
                    scope.launch { actions.setNotesAlcohol(it) }
                }
            }
            Text(
                "Some women find it useful to have this alongside everything else. It's here if you want it, and off if you don't.",
                Modifier.padding(horizontal = 4.dp),
                style = KeelFont.caption, color = theme.muted,
            )
        }

        // The privacy summary, with the full policy and Support linked from it.
        Group("Privacy") { LinkRow(KeelIcons.handRaised, "Your privacy", onOpenPrivacy) }

        YourData(onDelete = { showDelete = true })
    }

    if (showBlocked) {
        NotificationsBlockedDialog("Notifications are turned off for Keel. To get reminders and alerts, turn them on in Settings.") {
            showBlocked = false
        }
    }
    if (showDelete) DeleteEverythingDialog(onCancel = { showDelete = false }, onDelete = actions::eraseAllData)
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionEyebrow(title, Modifier.padding(start = 4.dp), tracking = 1.sp)
        Column(Modifier.fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape), content = content)
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(LocalKeelTheme.current.border))

@Composable
private fun SettingsToggle(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val theme = LocalKeelTheme.current
    KeelToggleRow(
        title = title, checked = checked, onCheckedChange = onChange, detail = subtitle,
        detailStyle = KeelFont.caption,
        contentPadding = PaddingValues(14.dp),
        leading = { Icon(icon, contentDescription = null, tint = theme.accent, modifier = Modifier.width(24.dp).size(20.dp)) },
    )
}

/** A row that opens another screen; the whole row is the tap target. */
@Composable
private fun LinkRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { haptic(KeelHapticKind.LIGHT); onClick() }.padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = theme.accent, modifier = Modifier.size(20.dp).width(24.dp))
        Text(title, Modifier.weight(1f), style = KeelFont.body, color = theme.text)
        Icon(KeelIcons.chevronRight, contentDescription = null, tint = theme.muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun YourData(onDelete: () -> Unit) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionEyebrow("YOUR DATA", Modifier.padding(start = 4.dp), tracking = 1.sp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(theme.card)
                .border(1.dp, theme.attention.copy(alpha = 0.18f), shape)
                .clickable(role = Role.Button, onClick = onDelete)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(theme.attention.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) { Icon(KeelIcons.personRemove, contentDescription = null, tint = theme.attention, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Delete all my data", style = KeelFont.body, color = theme.attention)
                Text("Everything Keel has stored on this phone", style = KeelFont.caption, color = theme.muted)
            }
            Icon(KeelIcons.chevronRight, contentDescription = null, tint = theme.muted, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * "Delete all your data?": she types DELETE before the button works, so it can't happen by
 * a stray tap. Tapping outside, Cancel or Back closes it.
 */
@Composable
private fun DeleteEverythingDialog(onCancel: () -> Unit, onDelete: () -> Unit) {
    val theme = LocalKeelTheme.current
    var typed by rememberSaveable { mutableStateOf("") }
    val confirmed = typed == DELETE_WORD
    val mono = KeelFont.sans(15.sp).copy(fontFamily = FontFamily.Monospace)
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onCancel)
        Box(
            Modifier
                .fillMaxSize()
                .background(theme.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(theme.card)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {} // keeps taps on the card
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(theme.attention.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(KeelIcons.warning, contentDescription = null, tint = theme.attention, modifier = Modifier.size(22.dp)) }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Delete all your data?", style = KeelFont.serif(18.sp), color = theme.text)
                        Text(
                            "This permanently deletes everything Keel has stored on this phone: your entries, medicines, reminders, settings and app lock. It can't be undone. Backup files you've exported aren't affected.",
                            style = KeelFont.caption, color = theme.muted,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        buildAnnotatedString {
                            append("Type ")
                            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)) { append(DELETE_WORD) }
                            append(" to confirm")
                        },
                        style = KeelFont.caption, color = theme.muted,
                    )
                    BasicTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        textStyle = mono.copy(color = theme.text),
                        cursorBrush = SolidColor(theme.accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = DELETE_WORD },
                        decorationBox = { field ->
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(theme.inputBackground)
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                            ) {
                                if (typed.isEmpty()) Text(DELETE_WORD, style = mono, color = theme.muted)
                                field()
                            }
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val buttonShape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier.weight(1f).clip(buttonShape).border(1.dp, theme.border, buttonShape)
                            .clickable(role = Role.Button, onClick = onCancel).padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Cancel", style = KeelFont.body, color = theme.text) }
                    Box(
                        Modifier.weight(1f).alpha(if (confirmed) 1f else 0.5f).clip(buttonShape).background(theme.attention)
                            .clickable(enabled = confirmed, role = Role.Button, onClick = onDelete).padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Delete", style = KeelFont.body, color = theme.onFill) }
                }
            }
        }
    }
}
