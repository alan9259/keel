package com.therecalibrationyears.keel.gpsummary

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therecalibrationyears.keel.designsystem.KeelDateFormats
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalGoHome
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.components.CalloutCard
import com.therecalibrationyears.keel.designsystem.components.KeelPrimaryButton
import com.therecalibrationyears.keel.designsystem.components.KeelToggleRow
import com.therecalibrationyears.keel.designsystem.components.ScreenHeader
import com.therecalibrationyears.keel.designsystem.components.StandardCard
import com.therecalibrationyears.keel.domain.GPAboutMe
import com.therecalibrationyears.keel.domain.GPMedCategory
import com.therecalibrationyears.keel.domain.GPPeriod
import com.therecalibrationyears.keel.domain.GPSummaryCopy
import com.therecalibrationyears.keel.domain.GPSummaryDocument
import com.therecalibrationyears.keel.domain.GPSummarySection
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the PDF goes: rendered, drawn for the preview, and handed to the share sheet. */
interface GPSummaryOutput {
    suspend fun render(document: GPSummaryDocument): ByteArray
    suspend fun pages(pdf: ByteArray, widthPx: Int): List<ImageBitmap>
    fun share(pdf: ByteArray, fileName: String)
}

/** The real output: Android's PDF writer, its PDF renderer for the preview, and the share sheet. */
private class DeviceGPSummaryOutput(private val context: Context) : GPSummaryOutput {
    private val fonts by lazy { GPSummaryFonts.load(context) }

    override suspend fun render(document: GPSummaryDocument): ByteArray =
        withContext(Dispatchers.Default) { GPSummaryPdfRenderer(document, fonts).render() }

    override suspend fun pages(pdf: ByteArray, widthPx: Int): List<ImageBitmap> =
        withContext(Dispatchers.IO) { GPSummaryFiles.rasterize(context, pdf, widthPx).map { it.asImageBitmap() } }

    override fun share(pdf: ByteArray, fileName: String) {
        val uri = GPSummaryFiles.writeForSharing(context, pdf, fileName)
        context.startActivity(GPSummaryFiles.shareIntent(uri))
    }
}

@Composable
fun rememberGPSummaryOutput(): GPSummaryOutput {
    val context = LocalContext.current
    return remember(context) { DeviceGPSummaryOutput(context) }
}

/** The PDF shown in the preview, kept so the very same bytes are shared. */
private class PreviewPdf(val document: GPSummaryDocument, val bytes: ByteArray)

/**
 * The GP Visit Summary flow (port of iOS `GPSummaryFlowView`): choose a period, review what
 * she recorded (remove any row), add what matters to her, preview the exact PDF, then share
 * it. Back steps back one page (and leaves from the first); Home leaves the whole flow,
 * asking first once she has made choices.
 */
@Composable
fun GPSummaryScreen(
    actions: GPSummaryActions,
    onBack: () -> Unit,
    initialStep: GPSummaryStep = GPSummaryStep.PERIOD,
    output: GPSummaryOutput = rememberGPSummaryOutput(),
) {
    val theme = LocalKeelTheme.current
    val goHome = LocalGoHome.current
    val scope = rememberCoroutineScope()
    val today = remember(actions) { actions.now().atZone(actions.zone).toLocalDate() }

    var draft by rememberSaveable(stateSaver = GPSummaryDraft.Saver) { mutableStateOf(GPSummaryDraft.start(today, initialStep)) }
    var candidates by remember { mutableStateOf(GPSummaryCandidates()) }
    var about by remember { mutableStateOf(GPAboutMe()) }
    var preview by remember { mutableStateOf<PreviewPdf?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }

    // The candidate lists depend only on the period, so re-read them when it changes.
    LaunchedEffect(draft.inputs.period) {
        candidates = GPSummaryCandidates.from(actions.document(GPSummaryCandidates.baseInputs(draft.inputs)), actions.reviewMeds())
    }
    LaunchedEffect(actions) { about = actions.aboutMe() }

    val back: () -> Unit = { if (draft.isFirst) onBack() else draft = draft.back() }
    BackHandler(enabled = !draft.isFirst) { draft = draft.back() }

    Column(Modifier.fillMaxSize().background(theme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenHeader(
            title = title(draft.step),
            subtitle = subtitle(draft.step),
            onBack = back,
            onHome = goHome?.let { home -> { if (draft.homeNeedsConfirmation) confirmLeave = true else home() } },
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
        )

        // Each step is a new page, so it starts at its own top.
        val scroll = key(draft.step) { rememberScrollState() }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
                .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (draft.step) {
                GPSummaryStep.PERIOD -> PeriodStep(draft, today) { draft = it }
                GPSummaryStep.REVIEW -> ReviewStep(draft, candidates) { draft = it }
                GPSummaryStep.DETAILS -> DetailsStep(draft, about) { draft = it }
                GPSummaryStep.PREVIEW -> PreviewStep(draft, actions, output) { preview = it }
            }
        }

        // The docked button.
        Column(Modifier.fillMaxWidth().background(theme.background)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
            KeelPrimaryButton(
                if (draft.isLast) "Generate and share" else "Continue",
                icon = if (draft.isLast) KeelIcons.share else null,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 8.dp),
            ) {
                if (draft.isLast) {
                    val inputs = draft.committedInputs
                    scope.launch {
                        val ready = preview
                        val pdf = ready ?: actions.document(inputs).let { PreviewPdf(it, output.render(it)) }
                        output.share(pdf.bytes, GPSummaryPdfRenderer.fileName(pdf.document.generatedOn))
                    }
                } else {
                    draft = draft.next()
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave your GP summary?", style = KeelFont.serif(20.sp), color = theme.heading) },
            text = { Text("What you've added so far won't be kept.", style = KeelFont.body, color = theme.text) },
            // Amber, never red (brand rule).
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; goHome?.invoke() }) { Text("Leave", style = KeelFont.button, color = theme.attention) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Keep going", style = KeelFont.button, color = theme.text) }
            },
            containerColor = theme.card,
        )
    }
}

private fun title(step: GPSummaryStep): String = when (step) {
    GPSummaryStep.PERIOD -> "Choose a time period"
    GPSummaryStep.REVIEW -> "Review what you recorded"
    GPSummaryStep.DETAILS -> "Add what matters to you"
    GPSummaryStep.PREVIEW -> "Preview"
}

private fun subtitle(step: GPSummaryStep): String = when (step) {
    GPSummaryStep.PERIOD -> "The summary covers this window of your records."
    GPSummaryStep.REVIEW -> "Remove anything you would rather leave off."
    GPSummaryStep.DETAILS -> "Optional. All in your own words."
    GPSummaryStep.PREVIEW -> "This is exactly what will be shared."
}

// Step 1: period

@Composable
private fun PeriodStep(draft: GPSummaryDraft, today: LocalDate, onChange: (GPSummaryDraft) -> Unit) {
    val theme = LocalKeelTheme.current
    StandardCard {
        GPPeriod.presets.forEach { period ->
            OptionRow(period.label, selected = draft.inputs.period == period) { onChange(draft.selectPreset(period)) }
            HorizontalDivider(color = theme.border)
        }
        OptionRow("Custom range", selected = draft.isCustom) { onChange(draft.setCustomRange()) }
    }

    if (draft.isCustom) {
        val context = LocalContext.current
        val formats = remember(context) { KeelDateFormats(context, ZoneOffset.UTC) }
        var picking by remember { mutableStateOf<String?>(null) }
        StandardCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DateRow("From", formats.dayMonthYear(draft.customStart)) { picking = "From" }
                DateRow("To", formats.dayMonthYear(draft.customEnd)) { picking = "To" }
            }
        }
        when (picking) {
            "From" -> DayDialog(draft.customStart, earliest = null, latest = draft.customEnd, onDismiss = { picking = null }) {
                picking = null
                onChange(draft.setCustomRange(start = it))
            }
            "To" -> DayDialog(draft.customEnd, earliest = draft.customStart, latest = today, onDismiss = { picking = null }) {
                picking = null
                onChange(draft.setCustomRange(end = it))
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton) { haptic(KeelHapticKind.SELECTION); onSelect() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = KeelFont.bodyLarge, color = theme.text, modifier = Modifier.weight(1f))
        Icon(
            if (selected) KeelIcons.radioChecked else KeelIcons.radioUnchecked, contentDescription = null,
            tint = if (selected) theme.accent else theme.muted, modifier = Modifier.size(22.dp),
        )
    }
}

/** A labelled date she taps to change (iOS compact `DatePicker`). */
@Composable
private fun DateRow(label: String, date: String, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = KeelFont.body, color = theme.text, modifier = Modifier.weight(1f))
        Text(
            date, style = KeelFont.body, color = theme.accent,
            modifier = Modifier.clip(RoundedCornerShape(Radius.sm)).background(theme.inputBackground)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "$label $date" }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** A day between [earliest] and [latest] (both included). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDialog(initial: LocalDate, earliest: LocalDate?, latest: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val theme = LocalKeelTheme.current
    fun millis(day: LocalDate) = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val first = earliest?.let(::millis)
    val last = millis(latest)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = millis(initial),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= last && (first == null || utcTimeMillis >= first)
            override fun isSelectableYear(year: Int): Boolean = year <= latest.year && (earliest == null || year >= earliest.year)
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

// Step 2: review

@Composable
private fun ReviewStep(draft: GPSummaryDraft, candidates: GPSummaryCandidates, onChange: (GPSummaryDraft) -> Unit) {
    val theme = LocalKeelTheme.current

    // Symptoms.
    SectionHeader(GPSummaryCopy.symptomsHeading)
    if (candidates.symptoms.isEmpty()) {
        StandardCard { Text(GPSummaryCopy.noSymptoms, style = KeelFont.body, color = theme.muted) }
    } else {
        StandardCard {
            candidates.symptoms.forEachIndexed { index, stat ->
                ReviewRow(stat.name, "${stat.daysThisPeriod} of ${candidates.checkInDays} check-in days", draft.isSymptomIncluded(stat.name)) {
                    onChange(draft.toggleSymptom(stat.name))
                }
                if (index < candidates.symptoms.lastIndex) HorizontalDivider(color = theme.border)
            }
        }
    }

    // Treatments.
    if (candidates.meds.isNotEmpty()) {
        SectionHeader("Treatments and supplements")
        StandardCard {
            candidates.meds.forEachIndexed { index, med ->
                ReviewRow(med.name, categoryLabel(med.category), draft.isMedIncluded(med.id)) { onChange(draft.toggleMed(med.id)) }
                if (index < candidates.meds.lastIndex) HorizontalDivider(color = theme.border)
            }
        }
    }

    // The auto-populated sections she can leave off. Period dates and check-in counts are the
    // identifying frame and stay in, so they aren't listed here.
    SectionHeader("Cycle, sleep, energy and mood")
    StandardCard {
        val rows = sectionRows(includingAlcohol = candidates.hasAlcoholRecords)
        rows.forEachIndexed { index, (section, title, detail) ->
            ReviewRow(title, detail, draft.isSectionIncluded(section)) { onChange(draft.toggleSection(section)) }
            if (index < rows.lastIndex) HorizontalDivider(color = theme.border)
        }
    }

    CalloutCard(icon = KeelIcons.info) {
        Text("Your period dates and check-in counts are always included.", style = KeelFont.caption, color = theme.text)
    }
}

/** The removable sections, with a short descriptor each. Alcohol only when she recorded some. */
private fun sectionRows(includingAlcohol: Boolean): List<Triple<GPSummarySection, String, String>> = buildList {
    add(Triple(GPSummarySection.CYCLE, "Periods and cycle", "Cycle summary from your records"))
    add(Triple(GPSummarySection.SLEEP, "Sleep", "Nights with disrupted sleep logged"))
    add(Triple(GPSummarySection.ENERGY, "Energy", "Your average energy"))
    add(Triple(GPSummarySection.MOOD, "Mood", "Your most-logged moods"))
    if (includingAlcohol) add(Triple(GPSummarySection.ALCOHOL, "Alcohol", "Check-in days with a number recorded"))
}

private fun categoryLabel(category: GPMedCategory): String = when (category) {
    GPMedCategory.MHT -> "Hormonal treatment"
    GPMedCategory.OTHER_PRESCRIBED -> "Prescription"
    GPMedCategory.SUPPLEMENT -> "Supplement"
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = KeelFont.serif(17.sp, FontWeight.Medium), color = LocalKeelTheme.current.accent, modifier = Modifier.padding(top = 4.dp))
}

/** A row she taps to keep or leave off; TalkBack reads its name and "Included" or "Removed", as on iOS. */
@Composable
private fun ReviewRow(name: String, detail: String, included: Boolean, toggle: () -> Unit) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    val tap = { haptic(KeelHapticKind.SELECTION); toggle() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(onClick = tap)
            .clearAndSetSemantics {
                contentDescription = name
                stateDescription = if (included) "Included" else "Removed"
                role = Role.Checkbox
                toggleableState = ToggleableState(included)
                onClick { tap(); true }
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name, style = KeelFont.body, color = if (included) theme.text else theme.muted,
                textDecoration = if (included) null else TextDecoration.LineThrough,
            )
            Text(detail, style = KeelFont.caption, color = theme.muted)
        }
        Icon(
            if (included) KeelIcons.checkCircle else KeelIcons.radioUnchecked, contentDescription = null,
            tint = if (included) theme.accent else theme.muted, modifier = Modifier.size(24.dp),
        )
    }
}

// Step 3: details

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsStep(draft: GPSummaryDraft, about: GPAboutMe, onChange: (GPSummaryDraft) -> Unit) {
    val theme = LocalKeelTheme.current

    // Priorities.
    Block(GPSummaryCopy.prioritiesHeading, prompt = GPSummaryCopy.prioritiesPrompt) {
        repeat(GPSummaryCopy.maxPriorities) { i ->
            LimitedField("Priority ${i + 1}", draft.priorityDrafts[i]) { onChange(draft.setPriority(i, it)) }
        }
    }

    // Impact.
    Block(GPSummaryCopy.impactHeading, prompt = null) {
        // No vertical gap: each chip's 48 dp touch target already leaves one around the pill.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GPSummaryCopy.impactAreaOptions.forEach { area ->
                Chip(area, selected = draft.isAreaSelected(area)) { onChange(draft.toggleArea(area)) }
            }
        }
        LimitedField("Something else (optional)", draft.inputs.impactOther) { onChange(draft.setImpactOther(it)) }
        Text("Overall impact", style = KeelFont.caption, color = theme.muted, modifier = Modifier.padding(top = 2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GPSummaryCopy.impactLevelOptions.forEach { level ->
                Chip(level, selected = draft.inputs.impactOverall == level) { onChange(draft.toggleOverall(level)) }
            }
        }
    }

    // Questions.
    Block(GPSummaryCopy.questionsHeading, prompt = GPSummaryCopy.questionsPrompt) {
        repeat(GPSummaryCopy.maxQuestions) { i ->
            LimitedField("Question ${i + 1}", draft.questionDrafts[i]) { onChange(draft.setQuestion(i, it)) }
        }
    }

    // About me: only what she has recorded, and off unless she turns it on.
    if (about.hasName || about.hasAge || about.hasHysterectomyAnswer) {
        Block(GPSummaryCopy.aboutHeading, prompt = "Off unless you turn them on.") {
            if (about.hasName) KeelToggleRow("Include my name", draft.inputs.includeName, { onChange(draft.setIncludeName(it)) })
            if (about.hasAge) KeelToggleRow("Include my age", draft.inputs.includeAge, { onChange(draft.setIncludeAge(it)) })
            if (about.hasHysterectomyAnswer) {
                KeelToggleRow("Include my hysterectomy answer", draft.inputs.includeHysterectomy, { onChange(draft.setIncludeHysterectomy(it)) })
            }
        }
    }
}

@Composable
private fun Block(title: String, prompt: String?, content: @Composable ColumnScope.() -> Unit) {
    val theme = LocalKeelTheme.current
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = KeelFont.serif(17.sp, FontWeight.Medium), color = theme.accent)
        if (prompt != null) Text(prompt, style = KeelFont.caption, color = theme.muted)
        content()
    }
}

/** Her words, held to the spec's length by the draft; grows to more lines as she types. */
@Composable
private fun LimitedField(placeholder: String, value: String, onValueChange: (String) -> Unit) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.md)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = KeelFont.body.copy(color = theme.text),
        cursorBrush = SolidColor(theme.accent),
        modifier = Modifier.fillMaxWidth().clip(shape).background(theme.inputBackground).border(1.dp, theme.border, shape)
            .semantics { contentDescription = placeholder }
            .padding(12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = KeelFont.body, color = theme.muted)
                inner()
            }
        },
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    Text(
        label, style = KeelFont.body, color = if (selected) theme.onFill else theme.text.copy(alpha = 0.85f),
        modifier = Modifier.minimumInteractiveComponentSize() // a 48 dp target around the pill
            .clip(CircleShape)
            .background(if (selected) theme.accent else theme.card)
            .border(1.dp, if (selected) Color.Transparent else theme.border, CircleShape)
            .selectable(selected = selected, role = Role.Checkbox) { haptic(KeelHapticKind.SELECTION); onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

// Step 4: preview

@Composable
private fun PreviewStep(draft: GPSummaryDraft, actions: GPSummaryActions, output: GPSummaryOutput, onReady: (PreviewPdf?) -> Unit) {
    val theme = LocalKeelTheme.current
    CalloutCard(icon = KeelIcons.handRaised) {
        Text(GPSummaryCopy.shareWarning, style = KeelFont.caption, color = theme.text)
    }

    val shape = RoundedCornerShape(Radius.md)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(520.dp).clip(shape).border(1.dp, theme.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        var pages by remember { mutableStateOf<List<ImageBitmap>?>(null) }
        val inputs = draft.committedInputs
        LaunchedEffect(inputs, widthPx) {
            pages = null
            onReady(null)
            val document = actions.document(inputs)
            val pdf = output.render(document)
            onReady(PreviewPdf(document, pdf))
            pages = output.pages(pdf, widthPx)
        }
        val shown = pages
        if (shown == null) {
            CircularProgressIndicator(color = theme.accent)
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEachIndexed { index, page ->
                    Image(
                        page, contentDescription = "Page ${index + 1} of ${shown.size}",
                        contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
