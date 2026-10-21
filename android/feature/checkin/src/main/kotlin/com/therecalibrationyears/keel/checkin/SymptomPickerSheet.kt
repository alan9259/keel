package com.therecalibrationyears.keel.checkin

import android.content.ClipDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.therecalibrationyears.keel.data.db.SymptomEntity
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.components.KeelTextField
import com.therecalibrationyears.keel.designsystem.components.SymptomChip
import com.therecalibrationyears.keel.designsystem.components.dashedCapsule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** What the picker asks of the app. */
interface SymptomPickerActions {
    /** Whether she has opted in to the intimacy and bladder group. */
    val showsSensitive: Flow<Boolean>
    suspend fun showSensitive()
    /** Her own symptom in a group; created unselected (she taps it to include it). */
    suspend fun addCustom(name: String, categoryRaw: String)
    suspend fun archive(symptom: SymptomEntity)
    suspend fun rename(symptom: SymptomEntity, name: String)
    suspend fun move(symptom: SymptomEntity, categoryRaw: String)
}

/**
 * The full symptom picker, opened from "More" on the check-in (port of iOS
 * `SymptomPickerSheet`). Grouped gently, so nothing is in her face until she goes looking.
 * Selections write straight back to the check-in.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SymptomPickerSheet(
    symptoms: List<SymptomEntity>,
    selected: Map<String, Int>,
    onToggle: (String) -> Unit,
    onRemoved: (String) -> Unit,
    actions: SymptomPickerActions,
    onDismiss: () -> Unit,
) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showsSensitive by actions.showsSensitive.collectAsStateWithLifecycle(false)

    var editing by remember { mutableStateOf(false) }
    var composing by remember { mutableStateOf<String?>(null) }
    var draftName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<SymptomEntity?>(null) }
    var dropTarget by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()
    val autoScroll = rememberDragAutoScroll(scrollState)
    // Catches the pointer between groups, so the list keeps scrolling at its edges.
    val edgeTarget = remember(autoScroll) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean = false
            override fun onMoved(event: DragAndDropEvent) { autoScroll.onMoved(event.toAndroidDragEvent().y) }
            override fun onEnded(event: DragAndDropEvent) { autoScroll.stop() }
        }
    }

    fun addDraft(raw: String) {
        val name = draftName.trim()
        if (name.isEmpty()) return
        scope.launch { actions.addCustom(name, raw); haptic(KeelHapticKind.SUCCESS) }
        draftName = "" // the composer stays open for rapid multi-add
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = theme.background) {
        // Inside the sheet: the drag must start from the sheet's own window, the one being
        // touched (the activity's window can't hand its touch over to the drag).
        val startDrag = rememberSymptomDrag()
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("More symptoms", style = KeelFont.serif(20.sp), color = theme.heading)
                    Text(if (selected.isEmpty()) "Nothing selected yet" else "${selected.size} selected", style = KeelFont.sans(12.sp), color = theme.muted)
                }
                // "Finish", not "Done": that's the sheet's own dismiss, right beside it.
                Row(
                    Modifier.clip(CircleShape).background(theme.accent.copy(alpha = 0.1f))
                        .clickable(role = Role.Button) { editing = !editing; if (editing) composing = null; haptic(KeelHapticKind.SELECTION) }
                        .semantics(mergeDescendants = true) { contentDescription = if (editing) "Finish editing symptoms" else "Edit symptoms" }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (editing) KeelIcons.check else KeelIcons.tune, null, tint = theme.accent, modifier = Modifier.size(13.dp))
                    Text(if (editing) "Finish" else "Edit", style = KeelFont.sans(12.sp, FontWeight.Medium), color = theme.accent)
                }
                Text(
                    "Done",
                    Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp).clickable(role = Role.Button) {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    }.padding(top = 12.dp),
                    style = KeelFont.sans(15.sp, FontWeight.Medium),
                    color = theme.accent,
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))

            Column(
                Modifier
                    .onGloballyPositioned { c -> c.boundsInRoot().let { autoScroll.setBounds(it.top, it.bottom) } }
                    .dragAndDropTarget(shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) }, target = edgeTarget)
                    .verticalScroll(scrollState).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    if (editing) "Press and hold a symptom to drag it into another group. Tap it to rename or remove it."
                    else "Tap for severity: once mild, twice moderate, three times severe.",
                    style = KeelFont.sans(12.sp), color = theme.muted,
                )
                pickerGroups(symptoms).forEach { group ->
                    val isTarget = dropTarget == group.raw
                    val target = remember(group.raw, symptoms) {
                        object : DragAndDropTarget {
                            override fun onEntered(event: DragAndDropEvent) { dropTarget = group.raw }
                            override fun onMoved(event: DragAndDropEvent) { autoScroll.onMoved(event.toAndroidDragEvent().y) }
                            override fun onExited(event: DragAndDropEvent) { if (dropTarget == group.raw) dropTarget = null }
                            override fun onEnded(event: DragAndDropEvent) { dropTarget = null; autoScroll.stop() }
                            override fun onDrop(event: DragAndDropEvent): Boolean {
                                val id = event.toAndroidDragEvent().clipData?.getItemAt(0)?.text?.toString() ?: return false
                                val s = symptoms.firstOrNull { it.id == id } ?: return false
                                if (s.categoryRaw == group.raw) return false
                                scope.launch { actions.move(s, group.raw); haptic(KeelHapticKind.SUCCESS) }
                                dropTarget = null
                                autoScroll.stop()
                                return true
                            }
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth()
                            .dragAndDropTarget(shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) }, target = target)
                            .clip(RoundedCornerShape(Radius.md))
                            .background(if (isTarget) theme.accent.copy(alpha = 0.08f) else Color.Transparent)
                            .then(if (isTarget) Modifier.dashedRoundRect(theme.accent.copy(alpha = 0.6f)) else Modifier)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(group.label.uppercase(), style = KeelFont.eyebrow.copy(letterSpacing = 1.5.sp), color = theme.muted)
                        if (group.isSensitive && !showsSensitive) {
                            SensitiveInvitation(group) { haptic(KeelHapticKind.SELECTION); scope.launch { actions.showSensitive() } }
                        } else {
                            group.intro?.let { Text(it, style = KeelFont.sans(12.sp), color = theme.muted) }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                group.items.forEach { s ->
                                    if (editing) {
                                        EditChip(s, onLongClick = { startDrag(s) }, onRename = { renaming = s }) {
                                            scope.launch { actions.archive(s); haptic(KeelHapticKind.SUCCESS) }
                                            onRemoved(s.id)
                                        }
                                    } else {
                                        SymptomChip(s.name, selected[s.id] ?: 0, onLongClick = { startDrag(s) }) { onToggle(s.id) }
                                    }
                                }
                                if (!editing) {
                                    AddPill(KeelIcons.plus, "Add your own", description = "Add your own symptom to ${group.label}", vertical = 8.dp) {
                                        haptic(KeelHapticKind.SELECTION); draftName = ""; composing = group.raw
                                    }
                                }
                                if (group.items.isEmpty() && editing) {
                                    Text("Drop here", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = KeelFont.sans(12.sp), color = theme.muted)
                                }
                            }
                            if (!editing && composing == group.raw) {
                                Composer(draftName, { draftName = it }, onAdd = { addDraft(group.raw) }, onCancel = { draftName = ""; composing = null })
                            }
                        }
                    }
                }
            }
        }
    }

    renaming?.let { s ->
        var text by remember(s.id) { mutableStateOf(s.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename symptom", style = KeelFont.serif(20.sp), color = theme.heading) },
            text = { KeelTextField(label = "Name", value = text, onValueChange = { text = it }) },
            confirmButton = {
                TextButton(onClick = { scope.launch { actions.rename(s, text) }; renaming = null }) {
                    Text("Save", style = KeelFont.button, color = theme.accent)
                }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel", style = KeelFont.button, color = theme.text) } },
            containerColor = theme.card,
        )
    }
}

/** A sensitive group stays folded away until she asks for it, with soft framing. */
@Composable
private fun SensitiveInvitation(group: SymptomGroup, onShow: () -> Unit) {
    val theme = LocalKeelTheme.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(theme.card)
            .border(1.dp, theme.border, RoundedCornerShape(Radius.card)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        group.intro?.let { Text(it, style = KeelFont.body, color = theme.text.copy(alpha = 0.8f)) }
        Row(
            Modifier.clip(CircleShape).border(1.dp, theme.accent.copy(alpha = 0.5f), CircleShape)
                .clickable(role = Role.Button, onClick = onShow)
                .semantics(mergeDescendants = true) { contentDescription = "Show intimacy and bladder symptoms" }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(KeelIcons.eye, null, tint = theme.accent, modifier = Modifier.size(15.dp))
            Text("Show these", style = KeelFont.body, color = theme.accent)
        }
    }
}

/** A symptom pill in edit mode: tap for Rename (her own only) or Remove from list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditChip(s: SymptomEntity, onLongClick: () -> Unit, onRename: () -> Unit, onRemove: () -> Unit) {
    val theme = LocalKeelTheme.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(CircleShape).background(theme.card).border(1.5.dp, theme.accent.copy(alpha = 0.45f), CircleShape)
                .combinedClickable(role = Role.Button, onLongClick = onLongClick) { menu = true }
                .semantics(mergeDescendants = true) { contentDescription = "Options for ${s.name}" }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.name, style = KeelFont.body, color = theme.text)
            Icon(KeelIcons.more, null, tint = theme.text, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = theme.card) {
            if (s.isCustom) {
                DropdownMenuItem(
                    text = { Text("Rename", style = KeelFont.body, color = theme.text) },
                    leadingIcon = { Icon(KeelIcons.pencil, null, tint = theme.text, modifier = Modifier.size(18.dp)) },
                    onClick = { menu = false; onRename() },
                )
            }
            // Amber, never red (brand rule).
            DropdownMenuItem(
                text = { Text("Remove from list", style = KeelFont.body, color = theme.attention) },
                leadingIcon = { Icon(KeelIcons.trash, null, tint = theme.attention, modifier = Modifier.size(18.dp)) },
                onClick = { menu = false; onRemove() },
            )
        }
    }
}

/** The inline "add your own" field for one group. */
@Composable
private fun Composer(value: String, onValueChange: (String) -> Unit, onAdd: () -> Unit, onCancel: () -> Unit) {
    val theme = LocalKeelTheme.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val canAdd = value.isNotBlank()
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = KeelFont.body.copy(color = theme.text),
            cursorBrush = SolidColor(theme.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            modifier = Modifier.weight(1f).focusRequester(focus).clip(CircleShape).background(theme.inputBackground)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text("Something else you're noticing…", style = KeelFont.body, color = theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
            },
        )
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(enabled = canAdd, role = Role.Button, onClick = onAdd)
                .semantics { contentDescription = "Add symptom" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(KeelIcons.sendUp, null, tint = if (canAdd) theme.accent else theme.muted.copy(alpha = 0.4f), modifier = Modifier.size(28.dp))
        }
        IconTap(KeelIcons.close, "Cancel", tint = theme.muted, iconSize = 16.dp, onClick = onCancel)
    }
}

private fun Modifier.dashedRoundRect(colour: Color) = drawBehind {
    drawRoundRect(
        colour, cornerRadius = CornerRadius(Radius.md.toPx()),
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))),
    )
}
