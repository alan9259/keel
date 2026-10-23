package com.therecalibrationyears.keel.medications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.data.toDraft
import com.therecalibrationyears.keel.designsystem.KeelDateFormats
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.components.KeelSegmented
import com.therecalibrationyears.keel.designsystem.components.dashedCapsule
import com.therecalibrationyears.keel.domain.TreatmentCatalog
import com.therecalibrationyears.keel.domain.TreatmentDraft
import com.therecalibrationyears.keel.domain.TreatmentKind
import java.text.Collator
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Adding something she's taking (port of iOS `TreatmentPickerSheet`): pick it from the
 * catalog or type her own, then fill in the details. The catalog is a list of what exists,
 * the way a pharmacy shelf is: nothing recommended, ranked or badged, products sorted
 * alphabetically, and no dose ever pre-filled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreatmentPickerSheet(
    catalog: TreatmentCatalog,
    current: List<MedicationEntity>,
    zone: ZoneId,
    today: LocalDate,
    formats: KeelDateFormats,
    onAdd: (TreatmentDraft) -> Unit,
    onDismiss: () -> Unit,
    initialKind: TreatmentKind = TreatmentKind.TREATMENT,
) {
    val theme = LocalKeelTheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(initialKind) }
    var draft by remember { mutableStateOf<TreatmentDraft?>(null) }
    var alreadyOnList by remember { mutableStateOf(false) }
    val sameName = remember { Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY } }

    /** The active entry with this name, if she's already taking it. */
    fun alreadyTaking(name: String): MedicationEntity? = current.firstOrNull { sameName.compare(it.name, name.trim()) == 0 }

    fun open(next: TreatmentDraft?, name: String) {
        if (next == null) return
        alreadyOnList = alreadyTaking(name) != null
        draft = next
    }

    fun close() { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = theme.background, dragHandle = null) {
        val d = draft
        if (d != null) {
            // Back returns to the list, as the form's own Back does.
            BackHandler { draft = null }
            TreatmentDetailForm(
                initial = d,
                title = TreatmentPicker.formTitle(alreadyOnList),
                today = today,
                zone = zone,
                formats = formats,
                onCancel = { draft = null },
                onSave = { finished -> onAdd(finished); close() },
            )
        } else {
            PickerList(
                catalog = catalog,
                kind = kind,
                onKind = { kind = it },
                isTaking = { alreadyTaking(it) != null },
                onCancel = ::close,
                onItem = { item, group -> open(TreatmentPicker.draftFor(item, group, alreadyTaking(item.name)?.toDraft(zone)), item.name) },
                onCustom = { name, group -> open(TreatmentPicker.draftForCustom(name, group, alreadyTaking(name)?.toDraft(zone)), name) },
                onSearched = { q -> open(TreatmentPicker.draftForSearch(q, kind, alreadyTaking(q)?.toDraft(zone)), q) },
            )
        }
    }
}

/** Editing what she already has: the details form on its own (iOS `.sheet(item:)`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreatmentFormSheet(
    initial: TreatmentDraft,
    title: String,
    today: LocalDate,
    zone: ZoneId,
    formats: KeelDateFormats,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
    onSave: (TreatmentDraft) -> Unit,
) {
    val theme = LocalKeelTheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheetState, containerColor = theme.background, dragHandle = null) {
        TreatmentDetailForm(
            initial = initial, title = title, today = today, zone = zone, formats = formats,
            onRemove = onRemove, onCancel = onCancel, onSave = onSave,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickerList(
    catalog: TreatmentCatalog,
    kind: TreatmentKind,
    onKind: (TreatmentKind) -> Unit,
    isTaking: (String) -> Boolean,
    onCancel: () -> Unit,
    onItem: (TreatmentCatalog.Item, TreatmentCatalog.Group) -> Unit,
    onCustom: (String, TreatmentCatalog.Group) -> Unit,
    onSearched: (String) -> Unit,
) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    var searchText by remember { mutableStateOf("") }
    var composingGroup by remember { mutableStateOf<String?>(null) }
    var customName by remember { mutableStateOf("") }
    val visible = TreatmentPicker.visibleGroups(catalog, kind, searchText)
    val isSearching = searchText.isNotBlank()

    Column(Modifier.fillMaxWidth().imePadding()) {
        SheetHeader(title = "What are you taking?", subtitle = "Pick it from the list, or add your own.", action = "Cancel", onAction = onCancel)
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            KeelSegmented(
                options = TreatmentKind.entries.map { it.shortLabel },
                selection = TreatmentKind.entries.indexOf(kind),
                onSelect = { onKind(TreatmentKind.entries[it]); composingGroup = null },
            )
            SearchField(searchText) { searchText = it }
            visible.forEach { group ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (TreatmentPicker.showsSectionHeading(visible, group)) Eyebrow(group.section)
                    if (group.title != group.section) Text(group.title, style = KeelFont.serif(16.sp), color = theme.text)
                    group.note?.let { Text(it, style = KeelFont.sans(12.sp), color = theme.muted) }
                    // No vertical gap: each pill's 48 dp touch target already leaves one.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        group.sortedItems().forEach { item ->
                            ItemChip(item.name, isTaking(item.name)) { haptic(KeelHapticKind.SELECTION); onItem(item, group) }
                        }
                        // While searching, the "Add …" affordance sits once at the bottom instead.
                        if (!isSearching) {
                            DashedAddPill("Add your own", description = "Add your own to ${group.title}") {
                                haptic(KeelHapticKind.SELECTION); customName = ""; composingGroup = group.id
                            }
                        }
                    }
                    if (composingGroup == group.id) {
                        Composer(
                            value = customName,
                            onValueChange = { customName = it },
                            onAdd = {
                                if (customName.isNotBlank()) {
                                    val name = customName
                                    composingGroup = null
                                    customName = ""
                                    onCustom(name, group)
                                }
                            },
                            onCancel = { customName = ""; composingGroup = null },
                        )
                    }
                }
            }
            if (isSearching) {
                // Not in the list: add it by that name, as her own.
                DashedAddPill("Add “${searchText.trim()}”", vertical = 8.dp) { haptic(KeelHapticKind.SELECTION); onSearched(searchText) }
            }
        }
    }
}

@Composable
internal fun SheetHeader(title: String, subtitle: String, action: String, onAction: () -> Unit) {
    val theme = LocalKeelTheme.current
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = KeelFont.serif(20.sp), color = theme.heading)
                Text(subtitle, style = KeelFont.sans(12.sp), color = theme.muted)
            }
            Box(
                Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(action, style = KeelFont.sans(15.sp, FontWeight.Medium), color = theme.accent)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    val theme = LocalKeelTheme.current
    val focus = LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(theme.inputBackground).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KeelIcons.search, null, tint = theme.muted, modifier = Modifier.size(16.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = KeelFont.body.copy(color = theme.text),
            cursorBrush = SolidColor(theme.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text("Search what you're taking", style = KeelFont.body, color = theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .clickable(role = Role.Button) { onValueChange(""); focus.clearFocus() }
                    .semantics { contentDescription = "Clear search" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(KeelIcons.clearFilled, null, tint = theme.muted, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun ItemChip(name: String, taking: Boolean, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    Row(
        Modifier.minimumInteractiveComponentSize() // a 48 dp target around the pill
            .clip(CircleShape)
            .background(if (taking) theme.accent.copy(alpha = 0.1f) else theme.card)
            .border(1.dp, if (taking) theme.accent.copy(alpha = 0.4f) else theme.border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { if (taking) stateDescription = "Already on your list" }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (taking) Icon(KeelIcons.check, null, tint = theme.accent, modifier = Modifier.size(12.dp))
        Text(name, style = KeelFont.body, color = if (taking) theme.accent else theme.text.copy(alpha = 0.85f))
    }
}

/** A dashed "add" pill (Add your own, Add a second time). */
@Composable
internal fun DashedAddPill(label: String, description: String? = null, vertical: androidx.compose.ui.unit.Dp = 8.dp, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    Row(
        Modifier.minimumInteractiveComponentSize().clip(CircleShape).dashedCapsule(theme.accent.copy(alpha = 0.5f))
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = description } else Modifier)
            .padding(horizontal = 14.dp, vertical = vertical),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KeelIcons.plus, null, tint = theme.accent, modifier = Modifier.size(14.dp))
        Text(label, style = KeelFont.body, color = theme.accent)
    }
}

@Composable
private fun Composer(value: String, onValueChange: (String) -> Unit, onAdd: () -> Unit, onCancel: () -> Unit) {
    val theme = LocalKeelTheme.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val canAdd = value.isNotBlank()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = KeelFont.body.copy(color = theme.text),
            cursorBrush = SolidColor(theme.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onAdd() }),
            modifier = Modifier.weight(1f).focusRequester(focus).clip(CircleShape).background(theme.inputBackground)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text("Name it as it's written on the box…", style = KeelFont.body, color = theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
            },
        )
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(enabled = canAdd, role = Role.Button, onClick = onAdd)
                .semantics { contentDescription = "Continue" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(KeelIcons.sendUp, null, tint = if (canAdd) theme.accent else theme.muted.copy(alpha = 0.4f), modifier = Modifier.size(28.dp))
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onCancel).semantics { contentDescription = "Cancel" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(KeelIcons.close, null, tint = theme.muted, modifier = Modifier.size(16.dp))
        }
    }
}
