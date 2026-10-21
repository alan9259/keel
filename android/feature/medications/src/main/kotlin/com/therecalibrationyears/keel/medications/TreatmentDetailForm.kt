package com.therecalibrationyears.keel.medications

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therecalibrationyears.keel.designsystem.KeelDateFormats
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.Spacing
import com.therecalibrationyears.keel.designsystem.components.KeelPrimaryButton
import com.therecalibrationyears.keel.designsystem.components.KeelTextEditor
import com.therecalibrationyears.keel.designsystem.components.KeelTimeDialog
import com.therecalibrationyears.keel.designsystem.components.KeelToggleRow
import com.therecalibrationyears.keel.domain.DoseSchedule
import com.therecalibrationyears.keel.domain.DoseSlot
import com.therecalibrationyears.keel.domain.DoseUnit
import com.therecalibrationyears.keel.domain.MedicationMethod
import com.therecalibrationyears.keel.domain.TreatmentDraft
import com.therecalibrationyears.keel.domain.TreatmentKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The details step, shared by adding and editing (port of iOS `TreatmentDetailForm`).
 * Everything past the name is optional, and no dose is ever suggested.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TreatmentDetailForm(
    initial: TreatmentDraft,
    title: String,
    today: LocalDate,
    zone: ZoneId,
    formats: KeelDateFormats,
    onCancel: () -> Unit,
    onSave: (TreatmentDraft) -> Unit,
    /** Shown on the edit path only. */
    onRemove: (() -> Unit)? = null,
) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    var draft by remember { mutableStateOf(initial) }
    var editingTime by remember { mutableStateOf<DoseSlot?>(null) }
    var pickingStopDate by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().imePadding()) {
        SheetHeader(title = draft.name, subtitle = title, action = "Back", onAction = onCancel)
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 32.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (draft.isOffLabel) {
                Note("Recorded as off-label, which means it's prescribed outside its approved use. Your GP will have explained why.")
            }
            if (draft.isCompounded) {
                Note("Compounded preparations aren't standardised, so Keel records this as compounded rather than a set strength.")
            }
            if (!draft.isCompounded) DoseField(draft) { draft = it }
            // Patch, gel, pessary: a prescription idea. A supplement is just taken.
            if (draft.kind == TreatmentKind.TREATMENT) {
                Field("How you take it") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MedicationMethod.entries.forEach { method ->
                            OptionChip(method.label, draft.method == method) {
                                haptic(KeelHapticKind.SELECTION)
                                draft = draft.copy(method = if (draft.method == method) null else method)
                            }
                        }
                    }
                }
            }
            ScheduleSection(
                draft = draft,
                today = today,
                formats = formats,
                onChange = { draft = it },
                onEditTime = { editingTime = it },
            )
            KeelToggleRow(
                title = "Mark taken automatically",
                detail = "Logs this medicine's doses as taken on the days you open the app, without tapping.",
                checked = draft.autoLogDoses,
                onCheckedChange = { draft = draft.copy(autoLogDoses = it) },
                titleStyle = KeelFont.body.copy(fontWeight = FontWeight.Medium),
            )
            // Kept for her, not asked for: reflected back, never an input.
            if (draft.date != null || draft.doseChangedAt != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    draft.date?.let { DateLine("Added", formats.dayMonthYear(it.atZone(zone).toLocalDate())) }
                    draft.doseChangedAt?.let { DateLine("Dose last changed", formats.dayMonthYear(it.atZone(zone).toLocalDate())) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Note", Modifier.weight(1f), style = KeelFont.body.copy(fontWeight = FontWeight.Medium), color = theme.text)
                    Text("Optional", style = KeelFont.sans(12.sp), color = theme.muted)
                }
                KeelTextEditor(
                    value = draft.note,
                    onValueChange = { draft = draft.copy(note = it) },
                    placeholder = "Anything worth remembering, like a brand swap at the chemist.",
                )
            }
            if (onRemove != null) {
                StoppedSection(draft, zone, formats, onChange = { draft = it }, onPickDate = { pickingStopDate = true })
            }
            Column(Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                KeelPrimaryButton("Save", icon = KeelIcons.check) { onSave(draft) }
                if (onRemove != null) {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            .clickable(role = Role.Button) { haptic(KeelHapticKind.LIGHT); onRemove() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Remove from my list", style = KeelFont.body, color = theme.muted)
                    }
                }
            }
        }
    }

    editingTime?.let { slot ->
        KeelTimeDialog(
            hour = slot.hour ?: 9,
            minute = slot.minute ?: 0,
            onDismiss = { editingTime = null },
            onPick = { h, m ->
                draft = TreatmentForm.updateSlot(draft, slot.id) { it.copy(hour = h, minute = m) }
                editingTime = null
            },
        )
    }
    if (pickingStopDate) {
        StopDateDialog(
            initial = draft.stoppedAt?.atZone(zone)?.toLocalDate() ?: today,
            today = today,
            onDismiss = { pickingStopDate = false },
            onPick = { day ->
                draft = TreatmentForm.setStoppedAt(draft, day.atStartOfDay(zone).toInstant())
                pickingStopDate = false
            },
        )
    }
}

@Composable
private fun Field(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(label, style = KeelFont.body.copy(fontWeight = FontWeight.Medium), color = LocalKeelTheme.current.text)
        content()
    }
}

/** Factual, not a warning: it's recorded so her GP summary is accurate. */
@Composable
private fun Note(text: String) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Text(
        text,
        Modifier.fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape).padding(14.dp),
        style = KeelFont.sans(12.sp),
        color = theme.text.copy(alpha = 0.75f),
    )
}

@Composable
private fun DateLine(label: String, date: String) {
    Text("$label $date", style = KeelFont.sans(12.sp), color = LocalKeelTheme.current.muted)
}

/** A number and a unit. Nothing is pre-selected: she reads it off her own box. */
@Composable
private fun DoseField(draft: TreatmentDraft, onChange: (TreatmentDraft) -> Unit) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.md)
    // The text she's typing, kept as typed ("0." mid-entry) and read as a number on change.
    var text by remember { mutableStateOf(TreatmentForm.amountText(draft.doseAmount)) }
    var menuOpen by remember { mutableStateOf(false) }
    Field(TreatmentForm.doseLabel(draft.kind)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text,
                onValueChange = { new ->
                    text = new
                    onChange(draft.copy(doseAmount = TreatmentForm.parseAmount(new)))
                },
                singleLine = true,
                textStyle = KeelFont.bodyLarge.copy(color = theme.text),
                cursorBrush = SolidColor(theme.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).clip(shape).background(theme.inputBackground).padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text("Amount", style = KeelFont.bodyLarge, color = theme.muted)
                        inner()
                    }
                },
            )
            Box {
                Row(
                    Modifier.heightIn(min = 44.dp).widthIn(min = 96.dp).clip(shape).background(theme.inputBackground)
                        .clickable(role = Role.DropdownList) { menuOpen = true }
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Unit"
                            stateDescription = draft.doseUnit?.label ?: "Not set"
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(draft.doseUnit?.label ?: "Unit", style = KeelFont.bodyLarge, color = if (draft.doseUnit == null) theme.muted else theme.text)
                    Icon(KeelIcons.unfold, null, tint = theme.muted, modifier = Modifier.size(14.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = theme.card) {
                    DoseUnit.entries.forEach { unit ->
                        DropdownMenuItem(
                            text = { Text(unit.label, style = KeelFont.body, color = theme.text) },
                            trailingIcon = if (draft.doseUnit == unit) { { Icon(KeelIcons.check, null, tint = theme.accent, modifier = Modifier.size(16.dp)) } } else null,
                            onClick = { onChange(draft.copy(doseUnit = unit)); menuOpen = false },
                        )
                    }
                    if (draft.doseUnit != null) {
                        HorizontalDivider(color = theme.border)
                        DropdownMenuItem(
                            text = { Text("Clear", style = KeelFont.body, color = theme.text) },
                            onClick = { onChange(draft.copy(doseUnit = null)); menuOpen = false },
                        )
                    }
                }
            }
        }
        if (draft.legacyDosage.isNotEmpty() && draft.doseAmount == null) {
            Text("Previously recorded as ${draft.legacyDosage}.", style = KeelFont.sans(12.sp), color = theme.muted)
        }
    }
}

/**
 * A weekly pattern, or a cycle with a pause, plus optional times. The summary underneath
 * says it back in plain words, so she never has to work it out from the controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleSection(
    draft: TreatmentDraft,
    today: LocalDate,
    formats: KeelDateFormats,
    onChange: (TreatmentDraft) -> Unit,
    onEditTime: (DoseSlot) -> Unit,
) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    val schedule = draft.schedule
    Field("When you take it") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DoseSchedule.Kind.entries.forEach { kind ->
                OptionChip(kind.label, schedule.kind == kind) {
                    haptic(KeelHapticKind.SELECTION)
                    onChange(TreatmentForm.setKind(draft, kind, today))
                }
            }
        }
        if (schedule.kind == DoseSchedule.Kind.CYCLE) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Stepper("Cycle length", schedule.cycleLength, "days", TreatmentForm.CYCLE_LENGTHS) { onChange(TreatmentForm.setCycleLength(draft, it)) }
                Stepper("Pause at the end", schedule.pauseDays, "days", 0..(schedule.cycleLength - 1)) { onChange(TreatmentForm.setPauseDays(draft, it)) }
                Stepper("Today is day", TreatmentForm.cycleDayToday(draft, today), "", 1..schedule.cycleLength) {
                    onChange(TreatmentForm.setCycleDayToday(draft, it, today))
                }
            }
        }
        if (schedule.kind != DoseSchedule.Kind.AS_NEEDED) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                schedule.sortedSlots.forEach { slot ->
                    SlotCard(
                        slot = slot,
                        showsDays = schedule.kind == DoseSchedule.Kind.WEEKLY,
                        canRemove = schedule.slots.size > 1,
                        formats = formats,
                        onEditTime = { onEditTime(slot) },
                        onAddTime = { haptic(KeelHapticKind.SELECTION); onChange(TreatmentForm.updateSlot(draft, slot.id) { it.copy(hour = 9, minute = 0) }) },
                        onClearTime = { haptic(KeelHapticKind.LIGHT); onChange(TreatmentForm.updateSlot(draft, slot.id) { it.copy(hour = null, minute = null) }) },
                        onRemove = { haptic(KeelHapticKind.LIGHT); onChange(TreatmentForm.removeSlot(draft, slot.id)) },
                        onToggleDay = { day -> haptic(KeelHapticKind.SELECTION); onChange(TreatmentForm.toggleWeekday(draft, slot.id, day)) },
                    )
                }
                DashedAddPill(if (schedule.slots.size > 1) "Add another time" else "Add a second time", vertical = 10.dp) {
                    haptic(KeelHapticKind.SELECTION)
                    onChange(TreatmentForm.addSlot(draft))
                }
            }
        }
        Text(schedule.summary(formats), Modifier.padding(top = 2.dp), style = KeelFont.sans(12.sp), color = theme.accent)
    }
}

/** One card per dose, leading with its own time and carrying its own days. */
@Composable
private fun SlotCard(
    slot: DoseSlot,
    showsDays: Boolean,
    canRemove: Boolean,
    formats: KeelDateFormats,
    onEditTime: () -> Unit,
    onAddTime: () -> Unit,
    onClearTime: () -> Unit,
    onRemove: () -> Unit,
    onToggleDay: (Int) -> Unit,
) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (slot.hasTime) {
                Box(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).background(theme.track)
                        .clickable(onClickLabel = "Time", role = Role.Button, onClick = onEditTime)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(formats.time(slot), style = KeelFont.sans(16.sp, FontWeight.Medium), color = theme.accent)
                }
                Box(
                    Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClearTime)
                        .semantics { contentDescription = "Remove the time from this dose" }.padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Clear time", style = KeelFont.sans(12.sp), color = theme.muted)
                }
            } else {
                Row(
                    Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onAddTime),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(KeelIcons.bell, null, tint = theme.accent, modifier = Modifier.size(15.dp))
                    Text("Add a time", style = KeelFont.body, color = theme.accent)
                }
            }
            Spacer(Modifier.weight(1f))
            if (canRemove) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onRemove)
                        .semantics { contentDescription = "Remove the ${formats.time(slot)} dose" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(KeelIcons.close, null, tint = theme.muted, modifier = Modifier.size(14.dp))
                }
            }
        }
        // A cycle already says which days it runs, so the week doesn't apply.
        if (showsDays) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                DoseSchedule.orderedWeekdays(formats.firstWeekday).forEach { weekday ->
                    val on = slot.appliesTo(weekday)
                    Box(
                        Modifier.width(36.dp).height(40.dp).clip(CircleShape)
                            .background(if (on) theme.accent else theme.background)
                            .then(if (on) Modifier else Modifier.border(1.dp, theme.border, CircleShape))
                            .selectable(selected = on, role = Role.Checkbox) { onToggleDay(weekday) }
                            .semantics { contentDescription = formats.fullWeekday(weekday) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            formats.weekdayLetter(weekday),
                            style = KeelFont.sans(12.sp, FontWeight.Medium),
                            color = if (on) theme.onFill else theme.text.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, unit: String, range: IntRange, onChange: (Int) -> Unit) {
    val theme = LocalKeelTheme.current
    val shown = if (unit.isEmpty()) "$value" else "$value $unit"
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { stateDescription = shown },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = KeelFont.body, color = theme.text)
        StepButton(KeelIcons.minus, "Decrease", value > range.first) { onChange(value - 1) }
        Text(shown, Modifier.widthIn(min = 64.dp), style = KeelFont.sans(15.sp, FontWeight.Medium).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), color = theme.text)
        StepButton(KeelIcons.plus, "Increase", value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(theme.track)
            .clickable(enabled = enabled, role = Role.Button) { haptic(KeelHapticKind.SELECTION); onClick() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) theme.text else theme.muted.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun OptionChip(title: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    Box(
        Modifier.clip(CircleShape)
            .background(if (selected) theme.accent else theme.card)
            .then(if (selected) Modifier else Modifier.border(1.dp, theme.border, CircleShape))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(title, style = KeelFont.body, color = if (selected) theme.onFill else theme.text.copy(alpha = 0.85f))
    }
}

/**
 * "I'm still taking this": turning it off keeps the treatment (not a delete) so the GP
 * summary can show it. The stop date stays blank unless she adds it: never defaulted to
 * today, because a date she didn't enter must not reach the summary.
 */
@Composable
private fun StoppedSection(draft: TreatmentDraft, zone: ZoneId, formats: KeelDateFormats, onChange: (TreatmentDraft) -> Unit, onPickDate: () -> Unit) {
    val theme = LocalKeelTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        KeelToggleRow(
            title = "I'm still taking this",
            checked = draft.isActive,
            onCheckedChange = { onChange(TreatmentForm.setStillTaking(draft, it)) },
        )
        if (!draft.isActive) {
            val stopped = draft.stoppedAt
            if (stopped != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Stopped on", Modifier.weight(1f), style = KeelFont.body, color = theme.text)
                    Box(
                        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).background(theme.track)
                            .clickable(onClickLabel = "Stopped on", role = Role.Button, onClick = onPickDate)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(formats.dayMonthYear(stopped.atZone(zone).toLocalDate()), style = KeelFont.body, color = theme.accent)
                    }
                }
                Text(
                    "Remove the date",
                    Modifier.clickable(role = Role.Button) { onChange(TreatmentForm.setStoppedAt(draft, null)) }.padding(vertical = 6.dp),
                    style = KeelFont.sans(12.sp),
                    color = theme.muted,
                )
            } else {
                Row(
                    Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onPickDate),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(KeelIcons.calendar, null, tint = theme.accent, modifier = Modifier.size(16.dp))
                    Text("Add the date you stopped (optional)", style = KeelFont.body, color = theme.accent)
                }
                Text(
                    "Left blank unless you add it. Your GP summary shows a stop date only if you enter one.",
                    style = KeelFont.sans(12.sp),
                    color = theme.muted,
                )
            }
        }
    }
}

/** The day she stopped, never later than today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopDateDialog(initial: LocalDate, today: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val theme = LocalKeelTheme.current
    val lastMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= lastMillis
            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        },
    )
    val colours = DatePickerDefaults.colors(
        containerColor = theme.card,
        titleContentColor = theme.muted,
        headlineContentColor = theme.heading,
        weekdayContentColor = theme.muted,
        navigationContentColor = theme.text,
        yearContentColor = theme.text,
        currentYearContentColor = theme.accent,
        selectedYearContainerColor = theme.accent,
        selectedYearContentColor = theme.onFill,
        dayContentColor = theme.text,
        disabledDayContentColor = theme.muted.copy(alpha = 0.4f),
        selectedDayContainerColor = theme.accent,
        selectedDayContentColor = theme.onFill,
        todayContentColor = theme.accent,
        todayDateBorderColor = theme.accent,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = colours,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text("Done", style = KeelFont.sans(15.sp, FontWeight.Medium), color = theme.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = KeelFont.sans(15.sp), color = theme.muted) } },
    ) {
        DatePicker(state = state, colors = colours, title = null, headline = null, showModeToggle = false)
    }
}
