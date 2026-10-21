package com.therecalibrationyears.keel.medications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.data.detailLine
import com.therecalibrationyears.keel.data.kind
import com.therecalibrationyears.keel.data.toDraft
import com.therecalibrationyears.keel.designsystem.KeelDateFormats
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.Spacing
import com.therecalibrationyears.keel.designsystem.components.InfoNoteCard
import com.therecalibrationyears.keel.designsystem.components.KeelPrimaryButton
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import com.therecalibrationyears.keel.domain.TreatmentCatalog
import com.therecalibrationyears.keel.domain.TreatmentDraft
import com.therecalibrationyears.keel.domain.TreatmentKind
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** What the Medications screen asks of the app, so this module never touches storage itself. */
interface MedicationsActions {
    /** What she's taking now, oldest first. */
    val active: Flow<List<MedicationEntity>>
    /** Treatments she has marked stopped: kept for her records and the GP summary. */
    val stopped: Flow<List<MedicationEntity>>
    val catalog: TreatmentCatalog
    val zone: ZoneId
    fun now(): Instant
    /** Adds it (or updates the entry with that name), then brings reminders and auto-log up to date. */
    suspend fun add(draft: TreatmentDraft)
    suspend fun update(id: String, draft: TreatmentDraft)
    /** Removes it from her list and cancels its reminders. */
    suspend fun remove(id: String)
    /** The tick: whether it shows in Home's Medicines log. Not a dose record. */
    suspend fun setTracked(id: String, tracked: Boolean)
}

/**
 * What she's taking, in two plain groups (port of iOS `MedicationsView`). Keel records what
 * she tells us: it never suggests a treatment, never ranks one product above another, and
 * never infers what she should be on.
 */
@Composable
fun MedicationsScreen(actions: MedicationsActions, onBack: () -> Unit) {
    val theme = LocalKeelTheme.current
    val context = LocalContext.current
    val formats = remember(actions.zone) { KeelDateFormats(context, zone = actions.zone) }
    val haptic = KeelHaptics.rememberPerformer()
    val scope = rememberCoroutineScope()
    val medications by actions.active.collectAsStateWithLifecycle(emptyList())
    val stopped by actions.stopped.collectAsStateWithLifecycle(emptyList())

    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MedicationEntity?>(null) }

    KeelScreen(title = "Medications and supplements", onBack = onBack) {
        if (medications.isEmpty() && stopped.isEmpty()) {
            EmptyState()
        } else {
            TreatmentKind.entries.forEach { kind ->
                val items = medications.filter { it.kind == kind }
                if (items.isNotEmpty()) {
                    Group(kind.label) {
                        items.forEach { med ->
                            MedicationRow(
                                med, detail = med.detailLine(formats, actions.zone),
                                onOpen = { haptic(KeelHapticKind.SELECTION); editing = med },
                                onToggleTracked = {
                                    haptic(KeelHapticKind.SELECTION)
                                    scope.launch { actions.setTracked(med.id, !med.isTracked) }
                                },
                            )
                        }
                    }
                }
            }
            if (stopped.isNotEmpty()) {
                Group("No longer taking") {
                    stopped.forEach { med ->
                        StoppedRow(med, formats, actions.zone) { haptic(KeelHapticKind.SELECTION); editing = med }
                    }
                }
            }
        }

        KeelPrimaryButton("Add Medication or Supplement", icon = KeelIcons.plus) { showAdd = true }

        InfoNoteCard(
            lead = "The tick:",
            message = "Ticked medicines show in your home Medicines log, where you record each day whether you took them. Auto-logged medicines show there too.",
        )
        InfoNoteCard(
            lead = "Reminders:",
            message = "Add a time to anything here and we'll send a gentle nudge on the days it's due. Leave it off and nothing will chase you.",
        )
    }

    if (showAdd) {
        TreatmentPickerSheet(
            catalog = actions.catalog,
            current = medications,
            zone = actions.zone,
            today = actions.now().atZone(actions.zone).toLocalDate(),
            formats = formats,
            onAdd = { draft -> scope.launch { actions.add(draft) } },
            onDismiss = { showAdd = false },
        )
    }

    editing?.let { med ->
        TreatmentFormSheet(
            initial = med.toDraft(actions.zone),
            title = "Edit",
            today = actions.now().atZone(actions.zone).toLocalDate(),
            zone = actions.zone,
            formats = formats,
            onRemove = {
                scope.launch { actions.remove(med.id) }
                editing = null
            },
            onCancel = { editing = null },
            onSave = { updated ->
                scope.launch { actions.update(med.id, updated) }
                editing = null
            },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow(title)
        content()
    }
}

@Composable
internal fun Eyebrow(text: String) {
    Text(text.uppercase(), style = KeelFont.eyebrow.copy(letterSpacing = 1.5.sp), color = LocalKeelTheme.current.muted)
}

/**
 * The whole card opens the details (remove lives in there). The track tick is its own
 * control, so its taps don't fall through to the card.
 */
@Composable
private fun MedicationRow(med: MedicationEntity, detail: String, onOpen: () -> Unit, onToggleTracked: () -> Unit) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape)
            .clickable(onClickLabel = "Opens details", role = Role.Button, onClick = onOpen).padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(med.name, style = KeelFont.serif(17.sp), color = theme.heading)
            if (detail.isNotEmpty()) Text(detail, style = KeelFont.caption, color = theme.muted)
            if (med.autoLogDoses) {
                Row(
                    Modifier.padding(top = 2.dp).semantics(mergeDescendants = true) { contentDescription = "Doses are marked taken automatically" },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(KeelIcons.checkCircle, null, tint = theme.sage, modifier = Modifier.size(11.dp))
                    Text("Auto-logged", style = KeelFont.sans(11.sp, FontWeight.Medium), color = theme.sage)
                }
            }
        }
        // Whether it shows in Home's Medicines log, where she records each day's dose.
        // Not a "taken today" tick.
        val tracked = med.isTracked
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(if (tracked) theme.sage else theme.card)
                .then(if (tracked) Modifier else Modifier.border(2.dp, theme.border, CircleShape))
                .toggleable(value = tracked, role = Role.Checkbox) { onToggleTracked() }
                .semantics {
                    contentDescription = "${med.name}, show in the home Medicines log"
                    stateDescription = if (tracked) "Tracked" else "Not tracked"
                },
            contentAlignment = Alignment.Center,
        ) {
            if (tracked) Icon(KeelIcons.check, null, tint = theme.onFill, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun StoppedRow(med: MedicationEntity, formats: KeelDateFormats, zone: ZoneId, onOpen: () -> Unit) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(theme.card.copy(alpha = 0.5f)).border(1.dp, theme.border, shape)
            .clickable(onClickLabel = "Opens details", role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) {}
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(med.name, style = KeelFont.serif(17.sp), color = theme.muted)
            med.stoppedAt?.let {
                Text("Stopped ${formats.dayMonthYear(it.atZone(zone).toLocalDate())}", style = KeelFont.caption, color = theme.muted)
            }
        }
        Icon(KeelIcons.chevronRight, null, tint = theme.muted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun EmptyState() {
    val theme = LocalKeelTheme.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(KeelIcons.pill, null, tint = theme.sage, modifier = Modifier.size(40.dp))
        Text("Nothing here yet", style = KeelFont.serif(20.sp), color = theme.heading)
        Text(
            "Add what you're taking, prescribed or otherwise, to keep track of it alongside how you're feeling.",
            style = KeelFont.body.copy(textAlign = TextAlign.Center),
            color = theme.muted,
        )
    }
}
