package com.therecalibrationyears.keel.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.therecalibrationyears.keel.designsystem.KeelDateFormats
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.components.EmojiGlyph
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import com.therecalibrationyears.keel.designsystem.components.KeelSegmented
import com.therecalibrationyears.keel.designsystem.components.VitalLineChart
import com.therecalibrationyears.keel.designsystem.lineSpacing
import com.therecalibrationyears.keel.domain.ActivityAggregation
import com.therecalibrationyears.keel.domain.ActivityCatalog
import com.therecalibrationyears.keel.domain.ActivityPeriod
import com.therecalibrationyears.keel.domain.EatingCatalog
import com.therecalibrationyears.keel.domain.EatingItem
import com.therecalibrationyears.keel.domain.MoodPacks
import com.therecalibrationyears.keel.domain.VitalTrend
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** What the Activities screen asks of the app. */
interface ActivitiesActions {
    val data: Flow<ActivitiesData>
    val zone: ZoneId
    fun now(): Instant
    /** A counted activity for a day (water). */
    suspend fun setAmount(activityId: String, day: LocalDate, amount: Double)
    /** Her eating answer for a day: yes, no, or cleared (null). */
    suspend fun setEating(itemId: String, day: LocalDate, answer: Boolean?)
}

/**
 * Port of iOS `ActivitiesView`: a Health-first daily picture. Metrics Health Connect knows fill
 * in on their own with a gentle "vs your usual" arrow, the day is tied back to how she felt,
 * and only what Health Connect can't capture (water, eating) is logged by hand. No invented
 * scores; empty tiles say "No data yet".
 */
@Composable
fun ActivitiesScreen(
    actions: ActivitiesActions,
    onBack: () -> Unit,
    /** "Connect to Health Connect" leads to the Health Connect screen. */
    onConnectHealth: () -> Unit,
    initialPeriod: ActivityPeriod = ActivityPeriod.DAY,
) {
    val context = LocalContext.current
    val formats = remember(actions.zone) { KeelDateFormats(context, zone = actions.zone) }
    val haptic = KeelHaptics.rememberPerformer()
    val scope = rememberCoroutineScope()
    val data by actions.data.collectAsStateWithLifecycle(initialValue = null)
    val today = actions.now().atZone(actions.zone).toLocalDate()
    var period by rememberSaveable { mutableStateOf(initialPeriod) }
    var anchor by rememberSaveable { mutableStateOf(today) }

    KeelScreen(title = "Activities", onBack = onBack, titleSize = 28.sp) {
        val loaded = data ?: return@KeelScreen
        val model = ActivitiesModel(loaded, today, period, anchor, formats.firstDayOfWeek)
        KeelSegmented(
            options = ActivityPeriod.entries.map { it.label },
            selection = period.ordinal,
            onSelect = { period = ActivityPeriod.entries[it] },
        )
        PeriodNav(model, formats) { delta ->
            anchor = ActivityAggregation.shift(anchor, delta, period, formats.firstDayOfWeek, today)
            haptic(KeelHapticKind.SELECTION)
        }
        if (period == ActivityPeriod.DAY) {
            model.feeling?.let { FeltCard(it, model) }
            HealthSection(model, onConnectHealth)
            BodySection(model)
            EatingPanel(model) { id, answer ->
                haptic(KeelHapticKind.SELECTION)
                scope.launch { actions.setEating(id, model.selectedDay, answer) }
            }
            ManualSection(model) { id, amount ->
                haptic(KeelHapticKind.SELECTION)
                scope.launch { actions.setAmount(id, model.selectedDay, amount) }
            }
        } else {
            AggregatedHealthSection(model, onConnectHealth)
            BodySection(model)
            PeriodSummaryCard(model)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = KeelFont.serif(18.sp), color = LocalKeelTheme.current.heading)
}

/** A bordered card surface, as the iOS rows draw them. */
private fun Modifier.card(theme: com.therecalibrationyears.keel.designsystem.KeelTheme): Modifier {
    val shape = RoundedCornerShape(Radius.card)
    return fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape)
}

@Composable
private fun PeriodNav(model: ActivitiesModel, formats: KeelDateFormats, shift: (Int) -> Unit) {
    val theme = LocalKeelTheme.current
    val unit = model.period.label.lowercase()
    Row(verticalAlignment = Alignment.CenterVertically) {
        NavButton(KeelIcons.chevronLeft, "Previous $unit", enabled = true) { shift(-1) }
        Text(
            model.periodLabel(formats::fullWeekdayMonthDay, formats::monthDay, formats::monthYear), Modifier.weight(1f),
            style = KeelFont.serif(17.sp).copy(textAlign = TextAlign.Center), color = theme.heading,
        )
        NavButton(KeelIcons.chevronRight, "Next $unit", enabled = !model.isCurrentPeriod) { shift(1) }
    }
}

@Composable
private fun NavButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) theme.text else theme.muted.copy(alpha = 0.3f), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun FeltCard(feeling: DayFeeling, model: ActivitiesModel) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    val pack = model.data.moodPack ?: MoodPacks.pack(null)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(theme.accent.copy(alpha = 0.06f)).border(1.dp, theme.accentBorder, shape)
            .padding(16.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiGlyph(pack.emojiFor(feeling.mood), size = 30.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (model.selectedDay == model.today) "How you felt today" else "How you felt", style = KeelFont.caption, color = theme.muted)
            Text(
                feeling.energy?.let { "${feeling.mood.label} · energy ${it.label.lowercase()}" } ?: feeling.mood.label,
                style = KeelFont.bodyLarge, color = theme.text,
            )
        }
    }
}

@Composable
private fun ConnectHealthPrompt(model: ActivitiesModel, onConnect: () -> Unit) {
    if (model.data.healthConnected) return
    val theme = LocalKeelTheme.current
    Row(
        Modifier.card(theme).clickable(role = Role.Button, onClick = onConnect).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(theme.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(KeelIcons.heart, null, tint = theme.accent, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Connect to Health Connect", style = KeelFont.body, color = theme.text)
            Text("Let your steps, sleep and more fill in on their own", style = KeelFont.caption, color = theme.muted)
        }
        Icon(KeelIcons.chevronRight, null, tint = theme.muted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun HealthSection(model: ActivitiesModel, onConnect: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("From Health Connect")
        ConnectHealthPrompt(model, onConnect)
        model.shownMetrics.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { MetricTile(it, model, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A glanceable tile: the value and unit, and a gentle direction arrow (never for body readings). */
@Composable
private fun MetricTile(metric: HealthMetric, model: ActivitiesModel, modifier: Modifier) {
    val theme = LocalKeelTheme.current
    val value = model.value(metric, model.selectedDay)
    val arrow = model.trend(metric, value)
    val shape = RoundedCornerShape(Radius.card)
    Column(
        modifier.heightIn(min = 104.dp).clip(shape).background(theme.card).border(1.dp, theme.border, shape).padding(14.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(KeelIcons.forSymbolName(metric.symbol), null, tint = if (value != null) theme.accent else theme.muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.weight(1f))
            arrow?.let {
                Icon(
                    when (it) { TrendArrow.UP -> KeelIcons.arrowUp; TrendArrow.DOWN -> KeelIcons.arrowDown; TrendArrow.SAME -> KeelIcons.equal },
                    null, tint = theme.muted, modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f, fill = false))
        if (value != null) {
            Text(
                buildAnnotatedString {
                    withStyle(KeelFont.serif(24.sp).toSpanStyle()) { append(ActivitiesModel.format(value, metric.decimal)) }
                    withStyle(KeelFont.caption.toSpanStyle()) { append(" ${metric.unit}") }
                },
                color = theme.heading, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text("No data yet", style = KeelFont.caption, color = theme.muted)
        }
        Text(metric.label, style = KeelFont.caption, color = theme.muted)
    }
}

@Composable
private fun AggregatedHealthSection(model: ActivitiesModel, onConnect: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("From Health Connect")
        ConnectHealthPrompt(model, onConnect)
        model.shownMetrics.forEach { AggregatedMetricCard(it, model) }
    }
}

/** One metric across the window: its total or average and a small bar per day. */
@Composable
private fun AggregatedMetricCard(metric: HealthMetric, model: ActivitiesModel) {
    val theme = LocalKeelTheme.current
    val roll = model.rollup(metric)
    Column(Modifier.card(theme).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(KeelIcons.forSymbolName(metric.symbol), null, tint = theme.accent, modifier = Modifier.size(16.dp))
            Text(metric.label, Modifier.weight(1f), style = KeelFont.body, color = theme.text)
            val summary = roll.summary
            if (summary != null) {
                Text(
                    buildAnnotatedString {
                        withStyle(KeelFont.serif(20.sp).toSpanStyle()) { append(ActivitiesModel.format(summary, metric.decimal)) }
                        withStyle(KeelFont.caption.toSpanStyle()) { append(if (metric.unit.isEmpty()) "" else " ${metric.unit}") }
                        withStyle(KeelFont.caption.toSpanStyle().merge(SpanStyle(color = theme.muted))) {
                            append(if (metric.aggregate == ActivityAggregation.Mode.TOTAL) " total" else " avg")
                        }
                    },
                    color = theme.heading, maxLines = 1,
                )
            } else {
                Text("No data yet", style = KeelFont.caption, color = theme.muted)
            }
        }
        BarChart(roll.perDay)
    }
}

/** Faint per-day bars against the window's own peak; empty days show a low track, never a value. */
@Composable
private fun BarChart(values: List<Double>) {
    val theme = LocalKeelTheme.current
    val peak = maxOf(values.maxOrNull() ?: 0.0, 1.0)
    Row(
        Modifier.fillMaxWidth().height(44.dp).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(if (values.size > 10) 2.dp else 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { v ->
            Box(
                Modifier.weight(1f).height(maxOf(3f, (v / peak * 44).toFloat()).dp).clip(RoundedCornerShape(2.dp))
                    .background(if (v > 0) theme.accent.copy(alpha = 0.65f) else theme.track.copy(alpha = 0.5f)),
            )
        }
    }
}

@Composable
private fun BodySection(model: ActivitiesModel) {
    val theme = LocalKeelTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Your body lately")
        if (model.hasVitals) {
            if (model.restingHeartRate.count >= 3) VitalRow("Resting heart rate", "bpm", model.restingHeartRate)
            if (model.heartRateVariability.count >= 3) VitalRow("Heart rate variability", "ms", model.heartRateVariability)
            if (model.weight.count >= 2) VitalRow("Weight", "kg", model.weight)
            if (model.skinTemperature.count >= 3) VitalRow("Overnight skin temperature", "°C", model.skinTemperature, showsAverage = false)
            Text(model.bodyNote, style = KeelFont.caption.lineSpacing(2f), color = theme.text.copy(alpha = 0.7f))
        } else {
            // The rows stay on screen before there's data, with empty tracks, never a guess.
            EmptyVitalRow("Resting heart rate")
            EmptyVitalRow("Heart rate variability")
            Text(
                "These fill in once Health Connect has a few days of data. Weight and overnight skin temperature show up here too, when Health Connect has them.",
                style = KeelFont.caption.lineSpacing(2f), color = theme.text.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun EmptyVitalRow(title: String) {
    val theme = LocalKeelTheme.current
    Row(Modifier.card(theme).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = KeelFont.body, color = theme.text)
            Text("No data yet", style = KeelFont.caption, color = theme.muted)
        }
        Box(Modifier.width(78.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(theme.track))
    }
}

@Composable
private fun VitalRow(title: String, unit: String, trend: VitalTrend, showsAverage: Boolean = true) {
    val theme = LocalKeelTheme.current
    Column(Modifier.card(theme).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = KeelFont.body, color = theme.text)
            val avg = trend.average
            if (showsAverage && avg != null) {
                Text(
                    buildAnnotatedString {
                        withStyle(KeelFont.serif(20.sp).toSpanStyle().merge(SpanStyle(color = theme.heading))) { append("~$avg") }
                        withStyle(KeelFont.caption.toSpanStyle().merge(SpanStyle(color = theme.muted))) { append(" $unit") }
                    },
                )
            } else if (!showsAverage) {
                Text("vs your usual", style = KeelFont.caption, color = theme.muted)
            }
        }
        // No verdict (steady, up, down): the number, its range and the graph speak for themselves.
        VitalLineChart(trend.points, theme.accent)
    }
}

@Composable
private fun EatingPanel(model: ActivitiesModel, set: (String, Boolean?) -> Unit) {
    val theme = LocalKeelTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Eating")
        Column(Modifier.card(theme).padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text(
                "Nourishing".uppercase(), Modifier.padding(top = 10.dp, bottom = 2.dp),
                style = KeelFont.eyebrow.copy(letterSpacing = 0.6.sp), color = theme.muted,
            )
            EatingCatalog.nourishment.forEach { EatingRow(it, model.eatingState(it.id), set) }
        }
        Text("Tap what fits, or leave it. Nothing here is scored or judged.", style = KeelFont.caption, color = theme.muted)
    }
}

@Composable
private fun EatingRow(item: EatingItem, state: Boolean?, set: (String, Boolean?) -> Unit) {
    val theme = LocalKeelTheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(item.label, Modifier.weight(1f), style = KeelFont.body, color = theme.text)
        // Tapping the active answer clears it back to "not logged".
        EatingPill("Yes", item.label, active = state == true) { set(item.id, if (state == true) null else true) }
        EatingPill("No", item.label, active = state == false) { set(item.id, if (state == false) null else false) }
    }
}

/** A neutral pill (no good or bad colour): the chosen answer is filled, the other outlined. */
@Composable
private fun EatingPill(label: String, item: String, active: Boolean, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = item, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(
            label,
            Modifier.clip(CircleShape).background(if (active) theme.accent.copy(alpha = 0.14f) else Color.Transparent)
                .border(1.dp, if (active) theme.accent else theme.border, CircleShape).padding(horizontal = 15.dp, vertical = 7.dp),
            style = KeelFont.sans(13.sp, FontWeight.Medium), color = if (active) theme.accent else theme.muted,
        )
    }
}

@Composable
private fun ManualSection(model: ActivitiesModel, setAmount: (String, Double) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("You logged")
        ActivityCatalog.all.filter { it.id == "water" }.forEach { activity ->
            val theme = LocalKeelTheme.current
            val value = model.amount(activity.id)
            val done = value > 0
            Row(Modifier.card(theme).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (done) theme.accent.copy(alpha = 0.12f) else theme.inputBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(KeelIcons.forSymbolName(activity.symbol), null, tint = if (done) theme.accent else theme.muted, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(activity.label, style = KeelFont.bodyLarge, color = theme.text)
                    if (done && activity.unit != null) {
                        Text("${ActivitiesModel.format(value, decimal = false)} ${activity.unit}", style = KeelFont.caption, color = theme.accent)
                    }
                }
                StepButton(KeelIcons.minus, "Decrease") { setAmount(activity.id, maxOf(0.0, value - activity.step)) }
                Text(ActivitiesModel.format(value, decimal = false), Modifier.widthIn(min = 26.dp), style = KeelFont.sans(15.sp, FontWeight.SemiBold).copy(textAlign = TextAlign.Center), color = theme.text)
                StepButton(KeelIcons.plus, "Increase") { setAmount(activity.id, value + activity.step) }
            }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(theme.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = theme.accent, modifier = Modifier.size(13.dp))
        }
    }
}

/** Week or month: how many of the days so far she checked in and logged water. */
@Composable
private fun PeriodSummaryCard(model: ActivitiesModel) {
    val theme = LocalKeelTheme.current
    val n = model.elapsedCount
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("You logged")
        Column(Modifier.card(theme).padding(horizontal = 14.dp, vertical = 4.dp)) {
            SummaryRow("sun.max.fill", "Checked in", "${model.checkInDays} of $n days")
            Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
            SummaryRow("drop.fill", "Water logged", "${model.waterDays} of $n days")
        }
    }
}

@Composable
private fun SummaryRow(symbol: String, label: String, value: String) {
    val theme = LocalKeelTheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(KeelIcons.forSymbolName(symbol), null, tint = theme.accent, modifier = Modifier.size(18.dp))
        Text(label, Modifier.weight(1f), style = KeelFont.body, color = theme.text)
        Text(value, style = KeelFont.sans(14.sp, FontWeight.Medium), color = theme.muted)
    }
}
