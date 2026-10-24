package com.therecalibrationyears.keel.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therecalibrationyears.keel.designsystem.KeelDateFormats
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.MinTouchTarget
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.components.EmojiGlyph
import com.therecalibrationyears.keel.designsystem.components.InsetTouchTarget
import com.therecalibrationyears.keel.designsystem.components.StandardCard
import com.therecalibrationyears.keel.designsystem.components.severityColour
import com.therecalibrationyears.keel.designsystem.mixOklab
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.domain.CalendarGrid
import com.therecalibrationyears.keel.domain.EnergyLevel
import com.therecalibrationyears.keel.domain.Greeting
import com.therecalibrationyears.keel.domain.MoodPacks
import com.therecalibrationyears.keel.domain.SleepFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import com.therecalibrationyears.keel.designsystem.DisabledPlainAlpha

/** Where Home's buttons lead (the iOS `MainRoute`s reachable from the dashboard). */
enum class HomeDestination { CYCLE, MEDICATIONS, PATTERNS, MORE, GP_SUMMARY }

/** What Home asks the app to do. */
interface HomeActions {
    fun navigate(destination: HomeDestination)
    /** Start a new entry for the day (the mood slide, then the detail screen). */
    fun createEntry(day: LocalDate)
    fun editEntry(checkInId: String)
    /** Record whether she took a medicine on the day (iOS `toggleMedicationFromHome`). */
    fun toggleMedication(medication: MedicationEntity, day: LocalDate, currentlyTaken: Boolean)
}

/**
 * Home (port of iOS `DashboardView`): the day selector and greeting, the day's entries,
 * the medicines log, the GP summary entry, the last 7 days, a month calendar, and the
 * docked row of buttons with the one "+".
 */
@Composable
fun HomeScreen(data: HomeData, now: Instant, zone: ZoneId, actions: HomeActions) {
    val theme = LocalKeelTheme.current
    val context = LocalContext.current
    val formats = remember(zone) { KeelDateFormats(context, zone = zone) }
    val today = now.atZone(zone).toLocalDate()
    val model = remember(data, today, zone) { HomeModel(data, today, zone) }
    val pack = MoodPacks.pack(data.moodPackId)

    var selectedEpochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val selDate = LocalDate.ofEpochDay(selectedEpochDay).let { if (it > today) today else it }
    var calMonthIndex by rememberSaveable { mutableIntStateOf(YearMonth.from(today).let { it.year * 12 + it.monthValue - 1 }) }
    val calMonth = YearMonth.of(calMonthIndex / 12, calMonthIndex % 12 + 1)

    fun select(day: LocalDate) { selectedEpochDay = day.toEpochDay() }

    val dayLabel: (LocalDate) -> String = { day ->
        when (day) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> formats.weekdayMonthDay(day)
        }
    }

    Column(Modifier.fillMaxSize().background(theme.background)) {
        Box(Modifier.weight(1f)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            TopBar(
                label = dayLabel(selDate), isToday = selDate == today, greeting = Greeting.forHour(now.atZone(zone).hour),
                onPrevious = { select(selDate.minusDays(1)) },
                onNext = { if (selDate < today) select(selDate.plusDays(1)) },
                onToday = { select(today) },
            )
            Snapshot(model, selDate, dayLabel(selDate), formats, pack, actions)
            MedicinesCard(model, selDate, dayLabel(selDate), actions)
            GpSummaryCard { actions.navigate(HomeDestination.GP_SUMMARY) }
            Trends(model, today, formats, pack)
            CalendarCard(
                model, calMonth, selDate, formats, pack,
                onMonth = { delta -> calMonthIndex += delta },
                onSelect = ::select,
            )
        }
        // The page colour behind the status bar, so scrolled cards pass under it rather
        // than colliding with the clock (Android draws edge to edge).
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(theme.background))
        }
        // Docked, not floating, so the buttons never sit over the charts behind them.
        FabRow(onNavigate = actions::navigate, onCreate = { actions.createEntry(selDate) })
    }
}

@Composable
private fun TopBar(
    label: String, isToday: Boolean, greeting: String,
    onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit,
) {
    val theme = LocalKeelTheme.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            ChromeButton(KeelIcons.chevronLeft, "Previous day", onClick = onPrevious)
            Column(
                Modifier.widthIn(min = 116.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onToday).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(label, style = KeelFont.sans(14.sp, FontWeight.Medium), color = theme.text)
                if (!isToday) Text("Tap to return", style = KeelFont.sans(12.sp), color = theme.accent)
            }
            ChromeButton(KeelIcons.chevronRight, "Next day", enabled = !isToday, onClick = onNext)
        }
        Spacer(Modifier.weight(1f).widthIn(min = 12.dp))
        BasicText(
            greeting,
            Modifier.padding(top = 4.dp),
            style = KeelFont.serif(22.sp).copy(color = theme.heading),
            // One line, shrinking as iOS does; with very large text it can't fit even shrunk, so
            // it wraps rather than being cut to "Good".
            maxLines = if (LocalDensity.current.fontScale > 1.3f) 2 else 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 15.4.sp, maxFontSize = 22.sp, stepSize = 0.4.sp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Snapshot(
    model: HomeModel, day: LocalDate, label: String, formats: KeelDateFormats,
    pack: com.therecalibrationyears.keel.domain.MoodPack, actions: HomeActions,
) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    val entries = model.entries(day)
    val isToday = day == model.today
    StandardCard {
        if (entries.isEmpty()) {
            // The whole card is the tap target; the single "+" is the docked button below.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { haptic(KeelHapticKind.SELECTION); actions.createEntry(day) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (isToday) "Quick check-in" else "Add an entry for this day"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (isToday) "No entry yet today" else "Nothing logged", style = KeelFont.body, color = theme.text.copy(alpha = 0.7f))
                    Text(if (isToday) "How are you feeling?" else label, style = KeelFont.sans(12.sp), color = theme.muted)
                }
                Icon(KeelIcons.chevronRight, contentDescription = null, tint = theme.muted, modifier = Modifier.size(18.dp))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(label.uppercase(), Modifier.weight(1f), style = KeelFont.eyebrow.copy(letterSpacing = 0.8.sp), color = theme.muted)
                    model.sleepHours(day)?.let {
                        Text("Sleep ${SleepFormat.hours(it)}", style = KeelFont.sans(12.sp), color = theme.muted)
                    }
                }
                entries.forEach { entry ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.md))
                            .background(theme.inputBackground)
                            .border(1.dp, theme.border, RoundedCornerShape(Radius.md))
                            .clickable(role = Role.Button) { haptic(KeelHapticKind.SELECTION); actions.editEntry(entry.id) }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            EmojiGlyph(pack.emojiFor(entry.mood), size = 28.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(entry.mood.label, style = KeelFont.bodyLarge, color = theme.text)
                                Text(formats.time(entry.at), style = KeelFont.caption, color = theme.muted)
                            }
                            // Energy in the word she chose (a subjective scale, not a percentage).
                            entry.energy?.let { Text(it.label, style = KeelFont.sans(13.sp, FontWeight.SemiBold), color = theme.accent) }
                            Icon(KeelIcons.chevronRight, contentDescription = null, tint = theme.muted, modifier = Modifier.size(16.dp))
                        }
                        if (entry.symptoms.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                entry.symptoms.forEach { s ->
                                    val fill = severityColour(s.severity, theme)
                                    Text(
                                        s.name,
                                        Modifier.clip(CircleShape).background(fill ?: theme.track).padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = KeelFont.sans(12.sp),
                                        color = if (fill != null) theme.onFill else theme.text.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                        entry.note?.let { Text(it, style = KeelFont.body, color = theme.text.copy(alpha = 0.85f)) }
                    }
                }
            }
        }
    }
}

/**
 * The day's medicines log: every medicine she tracks, plus any set to auto-log. Tapping a
 * row records whether she took it that day. Hidden when there's nothing to show.
 */
@Composable
private fun MedicinesCard(model: HomeModel, day: LocalDate, label: String, actions: HomeActions) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    val rows = model.medicineRows(day)
    if (rows.isEmpty()) return
    StandardCard(padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("Medications", Modifier.weight(1f), style = KeelFont.sans(12.sp), color = theme.muted)
                Text(label, style = KeelFont.sans(11.sp), color = theme.muted)
            }
            // Rows are 48 dp tall (Android's minimum touch target), touching, so the gap iOS
            // leaves between them is inside each row.
            Column {
            rows.forEach { row ->
                val taken = row.taken
                val hint = "Records whether you took it on $label"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinTouchTarget)
                        .clickable(role = Role.Button) {
                            haptic(KeelHapticKind.SUCCESS)
                            actions.toggleMedication(row.medication, day, taken)
                        }
                        .semantics(mergeDescendants = true) {
                            contentDescription = row.medication.name
                            stateDescription = if (taken) "Taken" else "Not taken"
                            onClick(label = hint) { actions.toggleMedication(row.medication, day, taken); true }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.medication.name, Modifier.weight(1f).padding(end = 8.dp), style = KeelFont.sans(13.sp),
                        color = theme.text.copy(alpha = if (taken) 0.85f else 0.55f), maxLines = 1,
                    )
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (taken) theme.sage else Color.Transparent)
                            .border(2.dp, if (taken) Color.Transparent else theme.border, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (taken) Icon(KeelIcons.check, contentDescription = null, tint = theme.onFill, modifier = Modifier.size(14.dp))
                    }
                }
            }
            }
            Box(Modifier.fillMaxWidth().padding(top = 2.dp).height(1.dp).background(theme.border))
            // A plain count, not a score or a progress bar: no failing-grade framing.
            val autoLogged = if (model.anyAutoLogged) ", including auto-logged" else ""
            Text(
                "Recorded as taken on ${model.daysTakenLast7} of the last 7 days$autoLogged.",
                style = KeelFont.sans(12.sp), color = theme.muted,
            )
        }
    }
}

/** Always-visible entry into the GP Visit Summary. */
@Composable
private fun GpSummaryCard(onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    val shape = RoundedCornerShape(Radius.card)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.card)
            .border(1.dp, theme.border, shape)
            .clickable(role = Role.Button) { haptic(KeelHapticKind.SELECTION); onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(theme.accentTint), contentAlignment = Alignment.Center) {
            Icon(KeelIcons.document, contentDescription = null, tint = theme.accent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Prepare for a GP visit", style = KeelFont.sans(16.sp, FontWeight.Medium), color = theme.text)
            Text("A summary of what you've recorded, for your appointment.", style = KeelFont.caption, color = theme.muted)
        }
        Icon(KeelIcons.chevronRight, contentDescription = null, tint = theme.muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Trends(model: HomeModel, today: LocalDate, formats: KeelDateFormats, pack: com.therecalibrationyears.keel.domain.MoodPack) {
    val theme = LocalKeelTheme.current
    var selectedEnergyDay by remember { mutableStateOf<LocalDate?>(null) }
    var selectedSleepDay by remember { mutableStateOf<LocalDate?>(null) }
    val barDayLabel: (LocalDate, String) -> String = { day, todayWord -> if (day == today) todayWord else formats.weekdayDay(day) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Last 7 days", style = KeelFont.eyebrow.copy(letterSpacing = 0.8.sp), color = theme.muted)

        StandardCard(padding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Mood", style = KeelFont.sans(12.sp), color = theme.muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    model.last7.forEach { day ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val mood = model.latestMood(day)
                            if (mood != null) EmojiGlyph(pack.emojiFor(mood), size = 26.dp)
                            else Box(Modifier.size(26.dp).clip(CircleShape).background(theme.track))
                            Text(formats.letter(day.dayOfWeek), style = KeelFont.sans(11.sp), color = theme.muted)
                        }
                    }
                }
            }
        }

        // Energy in her own words, not a percentage.
        BarTrendCard(
            title = "Energy", trailing = model.energyTrailing, bars = model.energyBars,
            colour = theme.accent, maxValue = 100.0, formats = formats,
            selected = selectedEnergyDay, onSelect = { selectedEnergyDay = it },
            selectedText = { day, value -> "${barDayLabel(day, "Today")} · ${EnergyLevel.fromPercent(value.toInt()).label}" },
        )
        // Real hours, no invented score; tap a night to read it.
        BarTrendCard(
            title = "Sleep",
            trailing = if (model.sleepAverage > 0) "avg ${SleepFormat.hours(model.sleepAverage)}" else "No data yet",
            bars = model.sleepBars, colour = theme.sage, maxValue = 10.0, formats = formats,
            selected = selectedSleepDay, onSelect = { selectedSleepDay = it },
            selectedText = { day, hours -> "${barDayLabel(day, "Last night")} · ${SleepFormat.hours(hours)}" },
        )
    }
}

/**
 * A 7-day bar strip. Every day keeps a faint track so real bars sit in their slot; a day
 * with no data shows just the track (never a value). Tapping a bar reads that day.
 */
@Composable
private fun BarTrendCard(
    title: String, trailing: String, bars: List<DayBar>, colour: Color, maxValue: Double, formats: KeelDateFormats,
    selected: LocalDate?, onSelect: (LocalDate?) -> Unit, selectedText: (LocalDate, Double) -> String,
) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    val barHeight = 72.dp
    val selectedValue = bars.firstOrNull { it.day == selected }?.value?.takeIf { it > 0 }
    val trailingText = if (selected != null && selectedValue != null) selectedText(selected, selectedValue) else trailing
    StandardCard(padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(title, Modifier.weight(1f), style = KeelFont.sans(12.sp), color = theme.muted)
                Text(trailingText, style = KeelFont.sans(12.sp), color = theme.text.copy(alpha = 0.6f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                bars.forEach { bar ->
                    val value = bar.value?.takeIf { it > 0 }
                    val isSelected = bar.day == selected
                    Column(
                        Modifier.weight(1f).then(
                            // TalkBack reads a day as tapping it shows it; a day with nothing isn't a
                            // button (iOS disables it) and is skipped.
                            if (value != null) {
                                Modifier
                                    .clickable(role = Role.Button) {
                                        haptic(KeelHapticKind.SELECTION)
                                        onSelect(if (isSelected) null else bar.day)
                                    }
                                    .semantics {
                                        contentDescription = selectedText(bar.day, value).replace(" · ", ", ") // not-copy: a spoken pause in place of the dot
                                        this.selected = isSelected
                                    }
                            } else {
                                // A day with nothing is a disabled button on iOS, drawn faint.
                                Modifier.clearAndSetSemantics {}.alpha(DisabledPlainAlpha)
                            },
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(30.dp, barHeight).clip(RoundedCornerShape(5.dp)).background(theme.track), contentAlignment = Alignment.BottomCenter) {
                            if (value != null) {
                                val h: Dp = maxOf(barHeight * (value / maxValue).coerceAtMost(1.0).toFloat(), 5.dp)
                                Box(
                                    Modifier
                                        .size(30.dp, h)
                                        .alpha(if (selected == null || isSelected) 1f else 0.55f)
                                        .clip(RoundedCornerShape(5.dp))
                                        // SwiftUI's `.gradient`: a touch lighter at the top.
                                        .background(Brush.verticalGradient(listOf(colour.mixOklab(theme.card, 0.18f), colour))),
                                )
                            }
                        }
                        Text(
                            formats.letter(bar.day.dayOfWeek),
                            Modifier.clearAndSetSemantics {}, // the bar's description names the day
                            style = KeelFont.sans(11.sp, if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                            color = if (isSelected) theme.text else theme.muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarCard(
    model: HomeModel, month: YearMonth, selected: LocalDate, formats: KeelDateFormats,
    pack: com.therecalibrationyears.keel.domain.MoodPack, onMonth: (Int) -> Unit, onSelect: (LocalDate) -> Unit,
) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    val isCurrentMonth = month == YearMonth.from(model.today)
    val cells = CalendarGrid.monthCells(month, formats.firstDayOfWeek)
    StandardCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ChromeButton(KeelIcons.chevronLeft, "Previous month", iconSize = 18.dp) { onMonth(-1) }
                Text(
                    formats.monthYear(month), Modifier.weight(1f),
                    style = KeelFont.serif(16.sp, FontWeight.Medium).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                    color = theme.heading,
                )
                ChromeButton(KeelIcons.chevronRight, "Next month", enabled = !isCurrentMonth, iconSize = 18.dp) { onMonth(1) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    CalendarGrid.weekOrder(formats.firstDayOfWeek).forEach {
                        Text(
                            formats.letter(it), Modifier.weight(1f), style = KeelFont.sans(11.sp)
                                .copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                            color = theme.muted,
                        )
                    }
                }
                // Each day is a 48 dp touch target (Android's minimum) around the 38 dp day iOS draws.
                Column {
                cells.chunked(7).forEach { week ->
                    Row {
                        week.forEach { day ->
                            Box(Modifier.weight(1f).height(MinTouchTarget), contentAlignment = Alignment.Center) {
                                if (day != null) {
                                    val isSel = day == selected
                                    val future = model.isFuture(day)
                                    val mood = model.latestMood(day)
                                    val shape = RoundedCornerShape(10.dp)
                                    InsetTouchTarget(
                                        onClick = { onSelect(day); haptic(KeelHapticKind.SELECTION) },
                                        inset = PaddingValues(horizontal = 2.dp, vertical = 5.dp),
                                        shape = shape,
                                        modifier = Modifier.fillMaxSize().semantics(mergeDescendants = true) {
                                            contentDescription = listOfNotNull(formats.fullDate(day), mood?.label).joinToString(", ")
                                        },
                                        enabled = !future,
                                        role = null,
                                        visual = Modifier
                                            .background(if (isSel) theme.accent.copy(alpha = 0.12f) else Color.Transparent)
                                            .border(1.5.dp, if (isSel) theme.accent else Color.Transparent, shape),
                                    ) {
                                        if (mood != null) {
                                            EmojiGlyph(pack.emojiFor(mood), size = 22.dp)
                                        } else {
                                            Text(
                                                "${day.dayOfMonth}", style = KeelFont.sans(13.sp),
                                                color = theme.text.copy(alpha = if (future) 0.25f * DisabledPlainAlpha else 0.75f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                }
            }
        }
    }
}

/** The docked row: Cycle, Medications, Looking back, More, and the one "+" (new check-in). */
@Composable
private fun FabRow(onNavigate: (HomeDestination) -> Unit, onCreate: () -> Unit) {
    val theme = LocalKeelTheme.current
    Column(Modifier.fillMaxWidth().background(theme.background)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Labels are the screens they open (iOS gives these icon-only buttons no explicit label).
            MiniFab(KeelIcons.calendar, theme.plum, "Cycle") { onNavigate(HomeDestination.CYCLE) }
            MiniFab(KeelIcons.pill, theme.sage, "Medications and supplements") { onNavigate(HomeDestination.MEDICATIONS) }
            MiniFab(KeelIcons.chart, theme.heading, "Looking back") { onNavigate(HomeDestination.PATTERNS) }
            MiniFab(KeelIcons.more, theme.muted, "More") { onNavigate(HomeDestination.MORE) }
            // Always adds a new check-in for the selected day; existing ones are edited above.
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(theme.accent)
                    .clickable(role = Role.Button, onClick = onCreate)
                    .semantics { contentDescription = "New check-in" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(KeelIcons.plus, contentDescription = null, tint = theme.background, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun MiniFab(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    Box(
        Modifier
            .size(46.dp)
            .shadow(8.dp, CircleShape, ambientColor = theme.text.copy(alpha = 0.14f), spotColor = theme.text.copy(alpha = 0.14f))
            .clip(CircleShape)
            .background(theme.card)
            .border(2.dp, theme.border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** A chrome icon button (day and month arrows): 48dp target, muted when disabled. */
@Composable
private fun ChromeButton(
    icon: ImageVector, label: String, enabled: Boolean = true, iconSize: Dp = 22.dp, onClick: () -> Unit,
) {
    val theme = LocalKeelTheme.current
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) theme.text else theme.muted.copy(alpha = 0.3f), modifier = Modifier.size(iconSize))
    }
}
