package com.therecalibrationyears.keel.checkin

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.therecalibrationyears.keel.data.db.SymptomEntity
import com.therecalibrationyears.keel.designsystem.KeelDateFormats
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.components.EmojiGlyph
import com.therecalibrationyears.keel.designsystem.components.KeelPrimaryButton
import com.therecalibrationyears.keel.designsystem.components.KeelTextEditor
import com.therecalibrationyears.keel.designsystem.components.SymptomChip
import com.therecalibrationyears.keel.designsystem.components.dashedCapsule
import com.therecalibrationyears.keel.designsystem.components.energyColour
import com.therecalibrationyears.keel.designsystem.components.severityColour
import com.therecalibrationyears.keel.domain.CheckInRules
import com.therecalibrationyears.keel.domain.EnergyLevel
import com.therecalibrationyears.keel.domain.MoodPack
import com.therecalibrationyears.keel.domain.SymptomSeverity
import com.therecalibrationyears.keel.platform.SpeechGate
import com.therecalibrationyears.keel.platform.SpeechRecognitionService
import com.therecalibrationyears.keel.platform.SpeechState
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** What the check-in asks of the app, so this module never touches storage itself. */
interface CheckInActions {
    /** Active symptoms (not archived), by name. */
    val activeSymptoms: Flow<List<SymptomEntity>>
    /** Whether the Alcohol section shows (Settings; on by default). */
    val notesAlcohol: Flow<Boolean>
    /** The quick chips' order, resolved once when the screen opens. */
    suspend fun quickChipIds(): List<String>
    /** The form for this request: empty for a new entry, filled in when editing. */
    suspend fun loadDraft(request: CheckInRequest): CheckInDraft
    suspend fun save(request: CheckInRequest, draft: CheckInDraft)
    suspend fun delete(checkInId: String)
    /** The "more" picker's actions. */
    val picker: SymptomPickerActions
    /** Voice notes for the diary (on-device dictation). */
    val voice: SpeechRecognitionService
}

/**
 * The check-in detail screen (port of iOS `CheckInModal`). The mood was chosen on the slide,
 * so this gathers energy, sleep, an optional diary, alcohol and symptoms on one scroll.
 * Android's back closes it without saving.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckInScreen(
    request: CheckInRequest,
    today: LocalDate,
    zone: ZoneId,
    pack: MoodPack,
    actions: CheckInActions,
    onClose: (saved: Boolean) -> Unit,
    onChangeMood: () -> Unit,
    onDeleted: () -> Unit,
) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val formats = remember(zone) { KeelDateFormats(context, zone) }
    val symptoms by actions.activeSymptoms.collectAsStateWithLifecycle(emptyList())
    val notesAlcohol by actions.notesAlcohol.collectAsStateWithLifecycle(true)

    var draft by remember { mutableStateOf(CheckInDraft()) }
    var defaultOrder by remember { mutableStateOf(emptyList<String>()) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showingPicker by remember { mutableStateOf(false) }

    LaunchedEffect(request) {
        defaultOrder = actions.quickChipIds()
        draft = actions.loadDraft(request)
        loaded = true
    }
    BackHandler { onClose(false) }

    // Voice notes: dictation streams into the diary while recording (iOS onChange(transcript)).
    val voice = actions.voice
    val voiceState by voice.state.collectAsStateWithLifecycle()
    val transcript by voice.transcript.collectAsStateWithLifecycle()
    LaunchedEffect(transcript) { if (voiceState == SpeechState.RECORDING) draft = draft.copy(notes = transcript) }
    DisposableEffect(Unit) { onDispose { voice.reset() } }
    val askForMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { voice.start(draft.notes) } else voice.permissionDenied()
    }
    fun toggleVoice() = when {
        voiceState == SpeechState.RECORDING -> voice.stop()
        !voice.hasMicPermission -> askForMic.launch(Manifest.permission.RECORD_AUDIO)
        else -> { scope.launch { voice.start(draft.notes) }; Unit }
    }

    val dateLabel = when (request.day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> formats.fullWeekdayMonthDay(request.day)
    }

    fun save() {
        if (saving || !loaded) return
        saving = true
        scope.launch {
            actions.save(request, draft)
            haptic(KeelHapticKind.SUCCESS)
            onClose(true)
        }
    }

    Column(Modifier.fillMaxSize().background(theme.background).imePadding()) {
        // Header
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (request.editingId == null) "New entry" else "Edit entry", style = KeelFont.serif(20.sp), color = theme.heading)
                Text(dateLabel, style = KeelFont.sans(12.sp), color = theme.muted)
            }
            IconTap(KeelIcons.close, "Close", tint = theme.muted, iconSize = 22.dp) { onClose(false) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // Mood recap: "Change" reopens the slide.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(theme.accent.copy(alpha = 0.05f))
                    .border(1.dp, theme.accent.copy(alpha = 0.2f), RoundedCornerShape(Radius.card))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmojiGlyph(pack.emojiFor(request.mood), size = 26.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(request.mood.label, style = KeelFont.body, color = theme.text)
                    Text("Change", Modifier.clickable(role = Role.Button, onClick = onChangeMood), style = KeelFont.sans(12.sp), color = theme.accent)
                }
            }

            Section("Energy level", trailing = { draft.energy?.let { Text(it.label, style = KeelFont.sans(14.sp, FontWeight.Medium), color = theme.accent) } }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnergyLevel.entries.forEach { level ->
                        val on = draft.energy == level
                        val shape = RoundedCornerShape(Radius.md)
                        Column(
                            Modifier.weight(1f).clip(shape)
                                .background(if (on) energyColour(level.level, theme) else theme.card)
                                .border(1.dp, if (on) Color.Transparent else theme.border, shape)
                                .clickable(role = Role.RadioButton) { draft = draft.copy(energy = level); haptic(KeelHapticKind.SELECTION) }
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("${level.level}", style = KeelFont.sans(18.sp, FontWeight.SemiBold), color = if (on) theme.onFill else theme.text.copy(alpha = 0.6f))
                            Text(level.label, style = KeelFont.sans(10.sp), color = if (on) theme.onFill.copy(alpha = 0.9f) else theme.muted, maxLines = 1)
                        }
                    }
                }
            }

            // Sleep: her own value for the night (imported nights arrive with Health Connect, M7).
            Section("Sleep last night", trailing = { Optional() }) {
                val hours = draft.sleepHours
                if (hours != null) {
                    StepperRow(
                        leading = { Icon(KeelIcons.moon, null, tint = theme.sage, modifier = Modifier.size(15.dp)) },
                        value = CheckInRules.sleepLabel(hours), valueMinWidth = 68,
                        onMinus = { haptic(KeelHapticKind.SELECTION); draft = draft.copy(sleepHours = CheckInRules.adjustSleep(hours, -0.5)) },
                        onPlus = { haptic(KeelHapticKind.SELECTION); draft = draft.copy(sleepHours = CheckInRules.adjustSleep(hours, 0.5)) },
                        onClear = { draft = draft.copy(sleepHours = null) },
                    )
                } else {
                    AddPill(KeelIcons.moon, "Add hours") { haptic(KeelHapticKind.SELECTION); draft = draft.copy(sleepHours = CheckInRules.SLEEP_START_HOURS) }
                }
            }

            Section("Diary", trailing = { Optional() }) {
                KeelTextEditor(
                    value = draft.notes,
                    onValueChange = { draft = draft.copy(notes = it) },
                    placeholder = "Anything on your mind… a rough night, a small win, how you're feeling in words.",
                ) {
                    VoiceInputButton(isRecording = voiceState == SpeechState.RECORDING) { toggleVoice() }
                }
                if (voiceState == SpeechState.RECORDING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(theme.accent))
                        Text("Listening…", style = KeelFont.sans(12.sp), color = theme.accent)
                    }
                } else {
                    SpeechGate.unavailableMessage(voiceState)?.let { Text(it, style = KeelFont.caption, color = theme.muted) }
                }
            }

            // Optional (Settings > Check-in). Starts blank; only a number she saves counts, zero included.
            if (notesAlcohol) {
                Section("Alcohol", trailing = { Optional() }) {
                    val count = draft.alcohol
                    if (count != null) {
                        StepperRow(
                            value = "$count", valueMinWidth = 40, valueDescription = "Alcohol, $count",
                            minusDescription = "One fewer", plusDescription = "One more",
                            clearDescription = "Leaves alcohol not recorded for this entry",
                            onMinus = { haptic(KeelHapticKind.SELECTION); draft = draft.copy(alcohol = CheckInRules.adjustAlcohol(count, -1)) },
                            onPlus = { haptic(KeelHapticKind.SELECTION); draft = draft.copy(alcohol = CheckInRules.adjustAlcohol(count, 1)) },
                            onClear = { draft = draft.copy(alcohol = null) },
                        )
                    } else {
                        AddPill(KeelIcons.plus, "Add a number") { haptic(KeelHapticKind.SELECTION); draft = draft.copy(alcohol = 0) }
                    }
                }
            }

            // The common, low-sensitivity chips only; everything else sits behind "more".
            Section(
                "Symptoms",
                trailing = {
                    Text(if (draft.selected.isEmpty()) "None" else "${draft.selected.size} selected", style = KeelFont.sans(12.sp), color = theme.accent)
                },
            ) {
                Text("Tap for severity: once mild, twice moderate, three times severe.", style = KeelFont.sans(12.sp), color = theme.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SymptomSeverity.entries.forEach { s ->
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(severityColour(s.level, theme)!!))
                            Text(s.label, style = KeelFont.sans(11.sp), color = theme.muted)
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickChips(symptoms, defaultOrder, draft.selected).forEach { s ->
                        SymptomChip(s.name, draft.selected[s.id] ?: 0) { draft = draft.toggled(s.id) }
                    }
                    AddPill(KeelIcons.plus, "More", description = "More symptoms", vertical = 8.dp) {
                        haptic(KeelHapticKind.SELECTION); showingPicker = true
                    }
                }
            }

            if (request.editingId != null) {
                val warn = theme.attention
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(Radius.input))
                        .border(1.dp, warn.copy(alpha = 0.4f), RoundedCornerShape(Radius.input))
                        .clickable(role = Role.Button) { confirmDelete = true }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(KeelIcons.trash, null, tint = warn, modifier = Modifier.size(18.dp))
                    Text("Remove entry", style = KeelFont.body, color = warn)
                }
            }
        }

        // Save bar
        Column(Modifier.fillMaxWidth().background(theme.background)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
            KeelPrimaryButton(
                "Save entry",
                Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
                icon = KeelIcons.check,
                isEnabled = loaded && !saving,
                onClick = ::save,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove this entry?", style = KeelFont.serif(20.sp), color = theme.heading) },
            text = {
                Text("This removes the whole check-in for ${dateLabel.lowercase()}. It can't be undone.", style = KeelFont.body, color = theme.text)
            },
            // Amber, never red (brand rule).
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val id = request.editingId ?: return@TextButton
                    scope.launch { actions.delete(id); haptic(KeelHapticKind.SUCCESS); onDeleted() }
                }) { Text("Remove entry", style = KeelFont.button, color = theme.attention) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", style = KeelFont.button, color = theme.text) } },
            containerColor = theme.card,
        )
    }

    if (showingPicker) {
        SymptomPickerSheet(
            symptoms = symptoms,
            selected = draft.selected,
            onToggle = { draft = draft.toggled(it) },
            onRemoved = { id -> draft = draft.copy(selected = draft.selected - id) },
            actions = actions.picker,
            onDismiss = { showingPicker = false },
        )
    }
}

@Composable
private fun Section(title: String, trailing: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val theme = LocalKeelTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(title, Modifier.weight(1f), style = KeelFont.serif(16.sp), color = theme.text)
            trailing()
        }
        content()
    }
}

@Composable
private fun Optional() {
    Text("Optional", style = KeelFont.sans(12.sp), color = LocalKeelTheme.current.muted)
}

/** A dashed "add" pill (Add hours, Add a number, More). */
@Composable
internal fun AddPill(icon: ImageVector, label: String, description: String? = null, vertical: androidx.compose.ui.unit.Dp = 10.dp, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    Row(
        Modifier.clip(CircleShape).dashedCapsule(theme.accent.copy(alpha = 0.5f))
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = description } else Modifier)
            .padding(horizontal = 14.dp, vertical = vertical),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = theme.accent, modifier = Modifier.size(14.dp))
        Text(label, style = KeelFont.body, color = theme.accent)
    }
}

/** The minus / value / plus / Clear row used by sleep and alcohol. */
@Composable
private fun StepperRow(
    value: String,
    valueMinWidth: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onClear: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    valueDescription: String? = null,
    minusDescription: String? = null,
    plusDescription: String? = null,
    clearDescription: String? = null,
) {
    val theme = LocalKeelTheme.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(theme.card)
            .border(1.dp, theme.border, RoundedCornerShape(Radius.card)).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        StepButton(KeelIcons.minus, minusDescription, onMinus)
        Text(
            value,
            Modifier.widthIn(min = valueMinWidth.dp).then(if (valueDescription != null) Modifier.semantics { contentDescription = valueDescription } else Modifier),
            style = KeelFont.sans(16.sp, FontWeight.Medium).copy(textAlign = TextAlign.Center),
            color = theme.text,
        )
        StepButton(KeelIcons.plus, plusDescription, onPlus)
        Spacer(Modifier.weight(1f))
        // iOS gives Clear an accessibility hint; on Android that's the click action's label.
        Text(
            "Clear",
            Modifier.clickable(role = Role.Button, onClickLabel = clearDescription, onClick = onClear).padding(8.dp),
            style = KeelFont.sans(12.sp),
            color = theme.muted,
        )
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String?, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(theme.track).clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = theme.text, modifier = Modifier.size(14.dp))
    }
}

/** An icon button with a 44dp target. */
@Composable
internal fun IconTap(icon: ImageVector, label: String, tint: Color, iconSize: androidx.compose.ui.unit.Dp = 20.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}
