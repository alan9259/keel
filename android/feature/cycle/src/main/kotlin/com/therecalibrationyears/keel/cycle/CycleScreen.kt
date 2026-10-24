package com.therecalibrationyears.keel.cycle

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.therecalibrationyears.keel.designsystem.KeelDateFormats
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.MinTouchTarget
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.components.InsetTouchTarget
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import com.therecalibrationyears.keel.designsystem.components.StandardCard
import com.therecalibrationyears.keel.designsystem.lineSpacing
import com.therecalibrationyears.keel.domain.CycleDayMark
import com.therecalibrationyears.keel.domain.CyclePhase
import com.therecalibrationyears.keel.domain.FlowLevel
import com.therecalibrationyears.keel.domain.PatternFinding
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import com.therecalibrationyears.keel.designsystem.DisabledPlainAlpha

/** What the Cycle screen asks of the app. */
interface CycleActions {
    val data: Flow<CycleData>
    val zone: ZoneId
    fun now(): Instant
    /** Log a day's flow, or clear it (null). */
    suspend fun setFlow(level: FlowLevel?, day: LocalDate)
}

/**
 * Port of iOS `CycleTrackingView`: a timeline of the current cycle, an honest status and
 * history, and tap-to-log flow. When there isn't enough to say, it says so rather than
 * guessing. Nothing here is invented.
 */
@Composable
fun CycleScreen(
    actions: CycleActions,
    onBack: () -> Unit,
    /** "Periods don't apply to you?" leads to Profile. */
    onOpenProfile: () -> Unit,
    /** A day whose log sheet opens straight away (debug screenshots only). */
    initialEditing: LocalDate? = null,
) {
    val context = LocalContext.current
    val formats = remember(actions.zone) { KeelDateFormats(context, zone = actions.zone) }
    val haptic = KeelHaptics.rememberPerformer()
    val scope = rememberCoroutineScope()
    val data by actions.data.collectAsStateWithLifecycle(initialValue = null)
    val today = actions.now().atZone(actions.zone).toLocalDate()
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today)) }
    var editing by rememberSaveable { mutableStateOf(initialEditing) }

    val loaded = data ?: run { KeelScreen(title = "Cycle", onBack = onBack) {}; return }
    val model = remember(loaded, today, month) { CycleModel(loaded, today, month) }
    val open: (LocalDate) -> Unit = { haptic(KeelHapticKind.SELECTION); editing = it }

    KeelScreen(title = "Cycle", onBack = onBack) {
        // The timeline is the top pane in every state: empty, it shows a recent week to log into.
        TimelineCard(model, formats, open)
        if (model.hasAnyPeriod) {
            if (model.historyLine != null) HistoryCard(model)
            PhaseCard(model.phase)
            loaded.finding?.let { InsightCard(it) }
        }
        LogPastCard(model, formats, onMonth = { month = it }, open = open)
        BackgroundLink(onOpenProfile)
    }

    editing?.let { day ->
        CycleDaySheet(
            day = day, formats = formats,
            current = loaded.days.firstOrNull { it.first == day }?.second?.level,
            imported = loaded.imported[day],
            onDismiss = { editing = null },
        ) { level ->
            haptic(KeelHapticKind.SELECTION)
            editing = null
            scope.launch { actions.setFlow(level, day) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineCard(model: CycleModel, formats: KeelDateFormats, open: (LocalDate) -> Unit) {
    val theme = LocalKeelTheme.current
    StandardCard(padding = 20.dp) {
        Text("Your cycle", style = KeelFont.serif(20.sp), color = theme.heading)
        Text(model.statusLine(formats::monthDay), Modifier.padding(top = 4.dp, bottom = 20.dp), style = KeelFont.body, color = theme.muted)

        val state = rememberLazyListState()
        val density = LocalDensity.current
        // Centre on today, as the iOS timeline opens.
        LaunchedEffect(model.railDays) {
            val index = model.railDays.indexOf(model.today).coerceAtLeast(0)
            val viewport = state.layoutInfo.viewportSize.width
            val pip = with(density) { dayWidth(density.fontScale).roundToPx() }
            state.scrollToItem(index, -((viewport - pip) / 2).coerceAtLeast(0))
        }
        LazyRow(state = state, contentPadding = PaddingValues(horizontal = 2.dp)) {
            items(model.railDays, key = { it.toEpochDay() }) { day -> DayPip(day, model, formats, open) }
        }
        if (model.hasAnyPeriod || model.hasAnyImported) {
            Legend(model.hasAnyImported, Modifier.padding(top = 16.dp))
        }
        if (model.hasAnyImported) {
            Text(
                "Days outlined are from Health Connect. They're shown here, but Keel's estimates only use the periods you log in Keel.",
                Modifier.padding(top = 10.dp), style = KeelFont.caption.lineSpacing(2f), color = theme.muted,
            )
        }
        if (!model.hasAnyPeriod) {
            Text(
                "Tap a day here or in the calendar below to log a period, and Keel will build your timeline, keep your cycle lengths, and gently estimate the next one once there's a pattern.",
                Modifier.padding(top = 14.dp), style = KeelFont.caption.lineSpacing(2f), color = theme.text.copy(alpha = 0.7f),
            )
        }
    }
}

private val PIP_WIDTH = 34.dp

/** A day's circle, grown with large text so the date stays inside it. */
private fun pipSize(fontScale: Float) = PIP_WIDTH * fontScale.coerceIn(1f, 1.6f)

/** Each day in the strip is at least a 48 dp touch target (Android's minimum); iOS spaces its pips 9 apart. */
private fun dayWidth(fontScale: Float) = maxOf(MinTouchTarget, pipSize(fontScale) + 8.dp)

@Composable
private fun DayPip(day: LocalDate, model: CycleModel, formats: KeelDateFormats, open: (LocalDate) -> Unit) {
    val theme = LocalKeelTheme.current
    val mark = model.marks[day]
    val isToday = day == model.today
    val inEstimate = model.inEstimate(day)
    val label = model.accessibilityLabel(day, formats.fullDate(day), inEstimate)
    Column(
        Modifier.width(dayWidth(LocalDensity.current.fontScale)).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { open(day) }.semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(formats.letter(day.dayOfWeek), style = KeelFont.sans(11.sp), color = theme.muted)
        Box(
            Modifier.size(pipSize(LocalDensity.current.fontScale)).clip(CircleShape)
                .background(mark?.let { theme.accent.copy(alpha = CycleModel.fillOpacity(it)) } ?: Color.Transparent)
                .ring(
                    when {
                        isToday -> Ring(theme.text, 2.dp)
                        mark?.imported == true -> Ring(theme.accent, 1.5.dp)
                        inEstimate -> Ring(theme.accent, 1.5.dp, dashed = true)
                        else -> null
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.dayOfMonth}",
                style = KeelFont.sans(13.sp, if (isToday) FontWeight.SemiBold else FontWeight.Normal),
                color = when {
                    mark != null && CycleModel.isDarkFill(mark) -> theme.onFill
                    day > model.today -> theme.muted.copy(alpha = 0.7f)
                    else -> theme.text
                },
            )
        }
    }
}

private data class Ring(val color: Color, val width: Dp, val dashed: Boolean = false)

/** A ring drawn just inside the shape's bounds (solid, or dashed for the estimate). */
private fun Modifier.ring(ring: Ring?, corner: Dp? = null): Modifier = if (ring == null) this else drawBehind {
    val w = ring.width.toPx()
    val effect = if (ring.dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx())) else null
    if (corner == null) {
        drawCircle(ring.color, radius = size.minDimension / 2 - w / 2, style = Stroke(w, pathEffect = effect))
    } else {
        val r = corner.toPx()
        drawRoundRect(
            ring.color, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
            size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r), style = Stroke(w, pathEffect = effect),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(showImported: Boolean, modifier: Modifier = Modifier) {
    val theme = LocalKeelTheme.current
    // Wraps rather than squeezing, so the items never break mid-word.
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LegendItem("Period", fill = theme.accent)
        LegendItem("Lighter", fill = theme.accent.copy(alpha = 0.5f))
        if (showImported) LegendItem("Health Connect", fill = theme.accent.copy(alpha = 0.3f), ring = Ring(theme.accent, 1.5.dp))
        LegendItem("Today", ring = Ring(theme.text, 1.5.dp))
        LegendItem("Estimated", ring = Ring(theme.accent, 1.5.dp, dashed = true))
    }
}

@Composable
private fun LegendItem(label: String, fill: Color = Color.Transparent, ring: Ring? = null) {
    val theme = LocalKeelTheme.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(fill).ring(ring))
        Text(label, style = KeelFont.sans(11.sp), color = theme.muted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryCard(model: CycleModel) {
    val theme = LocalKeelTheme.current
    StandardCard(padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your recent cycles", style = KeelFont.serif(18.sp), color = theme.heading)
            model.historyLine?.let { Text(it, style = KeelFont.body, color = theme.text.copy(alpha = 0.8f)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                model.recentLengths.forEach { len ->
                    Text(
                        "$len days",
                        Modifier.clip(CircleShape).background(theme.accent.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 6.dp),
                        style = KeelFont.sans(13.sp, FontWeight.Medium), color = theme.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun PhaseCard(phase: CyclePhase) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(theme.sageTint).border(1.dp, theme.sageBorder, shape).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Estimated phase", style = KeelFont.caption, color = theme.muted)
        Text(if (phase == CyclePhase.UNKNOWN) "Learning your rhythm" else phase.label, style = KeelFont.serif(22.sp), color = theme.heading)
        Text(
            if (phase == CyclePhase.UNKNOWN) {
                "Log a couple of cycles and Keel can estimate roughly where you are. In perimenopause cycles are often irregular, so this stays a gentle guide, never a prediction."
            } else {
                "In perimenopause cycles can be irregular, so this is a gentle estimate from your own pattern, not a prediction."
            },
            style = KeelFont.caption.lineSpacing(2f), color = theme.text.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun InsightCard(finding: PatternFinding) {
    val theme = LocalKeelTheme.current
    StandardCard(padding = 20.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(theme.accent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KeelIcons.forSymbolName(finding.icon), null, tint = theme.accent, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(finding.title, style = KeelFont.serif(17.sp), color = theme.heading)
                Text(finding.detail, style = KeelFont.body.lineSpacing(2f), color = theme.text.copy(alpha = 0.8f))
                Text(finding.timeframe, style = KeelFont.caption.copy(fontStyle = FontStyle.Italic), color = theme.muted)
            }
        }
    }
}

/** "Log past days": the month grid. */
@Composable
private fun LogPastCard(model: CycleModel, formats: KeelDateFormats, onMonth: (YearMonth) -> Unit, open: (LocalDate) -> Unit) {
    val theme = LocalKeelTheme.current
    StandardCard(padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonthButton(KeelIcons.chevronLeft, "Previous month", enabled = true) { onMonth(model.month.minusMonths(1)) }
                Text(
                    formats.monthYear(model.month), Modifier.weight(1f),
                    style = KeelFont.serif(17.sp).copy(textAlign = TextAlign.Center), color = theme.heading,
                )
                MonthButton(KeelIcons.chevronRight, "Next month", enabled = !model.isCurrentOrFutureMonth) { onMonth(model.month.plusMonths(1)) }
            }
            Text("Tap a day to log or edit its flow.", style = KeelFont.caption, color = theme.muted)
            MonthGrid(model, formats, open)
        }
    }
}

@Composable
private fun MonthButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = theme.muted.copy(alpha = if (enabled) 1f else 0.35f * DisabledPlainAlpha), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MonthGrid(model: CycleModel, formats: KeelDateFormats, open: (LocalDate) -> Unit) {
    val theme = LocalKeelTheme.current
    val firstDay = formats.firstDayOfWeek
    // Days touch: each is a 48 dp target (Android's minimum) around the 40 dp shape iOS draws.
    Column {
        Row(Modifier.padding(bottom = 4.dp)) {
            (0L..6L).map { firstDay.plus(it) }.forEach { dow ->
                Text(
                    formats.letter(dow), Modifier.weight(1f).padding(vertical = 4.dp),
                    style = KeelFont.sans(12.sp, FontWeight.Medium).copy(textAlign = TextAlign.Center), color = theme.muted,
                )
            }
        }
        model.monthCells(firstDay).chunked(7).forEach { week ->
            Row {
                week.forEach { day -> Box(Modifier.weight(1f)) { if (day != null) GridCell(day, model, formats, open) } }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun GridCell(day: LocalDate, model: CycleModel, formats: KeelDateFormats, open: (LocalDate) -> Unit) {
    val theme = LocalKeelTheme.current
    val mark: CycleDayMark? = model.marks[day]
    val isToday = day == model.today
    val isFuture = day > model.today
    val shape = RoundedCornerShape(11.dp)
    val label = model.accessibilityLabel(day, formats.fullDate(day), inEstimate = false)
    InsetTouchTarget(
        onClick = { open(day) },
        inset = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        shape = shape,
        modifier = Modifier.fillMaxWidth().height(MinTouchTarget).semantics(mergeDescendants = true) { contentDescription = label },
        enabled = !isFuture,
        visual = Modifier
            .background(mark?.let { theme.accent.copy(alpha = CycleModel.fillOpacity(it)) } ?: Color.Transparent)
            .ring(
                when {
                    mark?.imported == true -> Ring(theme.accent, 1.5.dp)
                    isToday && mark == null -> Ring(theme.accent, 2.dp)
                    else -> null
                },
                corner = 11.dp,
            ),
    ) {
        Text(
            "${day.dayOfMonth}", style = KeelFont.body,
            color = when {
                mark != null -> if (CycleModel.isDarkFill(mark)) theme.onFill else theme.text
                isFuture -> theme.muted.copy(alpha = 0.5f * DisabledPlainAlpha)
                else -> theme.text
            },
        )
    }
}

/**
 * A gentle way out for someone this screen doesn't fit: Profile, where she can note that
 * periods no longer apply or add her background. Nothing here changes because of it.
 */
@Composable
private fun BackgroundLink(onOpen: () -> Unit) {
    val theme = LocalKeelTheme.current
    Text(
        "Periods don't apply to you? You can turn this off, or add your background in Profile.",
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(top = 4.dp, bottom = 8.dp),
        style = KeelFont.caption.copy(textDecoration = TextDecoration.Underline), color = theme.muted,
    )
}

/** Tap to log: pick a flow level, or mark the day as not a period day. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CycleDaySheet(
    day: LocalDate,
    formats: KeelDateFormats,
    current: FlowLevel?,
    imported: FlowLevel?,
    onDismiss: () -> Unit,
    onPick: (FlowLevel?) -> Unit,
) {
    val theme = LocalKeelTheme.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = theme.background,
    ) {
        // Scrolls rather than clips at large text sizes.
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 22.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(formats.fullDate(day), style = KeelFont.serif(20.sp), color = theme.heading)
                Text("How was your flow?", style = KeelFont.sans(13.sp), color = theme.muted)
                if (imported != null && current == null) {
                    val known = if (imported == FlowLevel.UNSPECIFIED) {
                        "Health Connect has this as a period day."
                    } else {
                        "Health Connect has this as a period day, ${imported.label.lowercase()} flow."
                    }
                    Text(known + " Choose a level to log it in Keel too.", Modifier.padding(top = 4.dp), style = KeelFont.caption, color = theme.muted)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowLevel.loggingOrder.forEach { level -> FlowChip(level.label, selected = current == level) { onPick(level) } }
            }
            // Keel can't change Health Connect, so a day only it has offers a level, not removal.
            if (current != null || imported == null) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onPick(null) },
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (current == null) KeelIcons.checkCircleOutline else KeelIcons.closeCircle, null, tint = theme.muted, modifier = Modifier.size(18.dp))
                    Text(if (current == null) "Not a period day" else "Remove this period day", style = KeelFont.body, color = theme.muted)
                }
            }
        }
    }
}

@Composable
private fun FlowChip(title: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    Text(
        title,
        Modifier.clip(CircleShape).background(if (selected) theme.accent else theme.card)
            .border(1.dp, if (selected) Color.Transparent else theme.border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 11.dp),
        style = KeelFont.body, color = if (selected) theme.onFill else theme.text.copy(alpha = 0.85f),
    )
}
