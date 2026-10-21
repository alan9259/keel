package com.therecalibrationyears.keel.insights

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.therecalibrationyears.keel.designsystem.KeelDateFormats
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelTheme
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import com.therecalibrationyears.keel.designsystem.components.KeelSegmented
import com.therecalibrationyears.keel.domain.Mood
import com.therecalibrationyears.keel.domain.StatsPeriod
import com.therecalibrationyears.keel.domain.VitalTrend
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/** What the Stats screen asks of the app. */
interface StatsActions {
    val data: Flow<StatsData>
    val zone: ZoneId
    fun now(): Instant
}

/**
 * Port of iOS `ReportsView` ("Stats"): what she has recorded, for her own reflection. Counts
 * are days, never entries; no streaks and no broken runs. Nothing clinician-facing is shared
 * from here: that goes through the GP Visit Summary and its review step.
 */
@Composable
fun StatsScreen(actions: StatsActions, onBack: () -> Unit) {
    val context = LocalContext.current
    val formats = remember(actions.zone) { KeelDateFormats(context, zone = actions.zone) }
    val data by actions.data.collectAsStateWithLifecycle(initialValue = null)
    var period by rememberSaveable { mutableStateOf(StatsPeriod.MONTH) }
    val today = actions.now().atZone(actions.zone).toLocalDate()

    KeelScreen(title = "Stats", onBack = onBack, subtitle = "What you have recorded", titleSize = 28.sp) {
        val loaded = data ?: return@KeelScreen
        val model = StatsModel(loaded, today, period)
        val theme = LocalKeelTheme.current
        KeelSegmented(
            options = StatsPeriod.entries.map { it.label }, selection = period.ordinal,
            onSelect = { period = StatsPeriod.entries[it] }, modifier = Modifier.widthIn(max = 260.dp),
        )
        Text(model.checkInLine, Modifier.padding(horizontal = 4.dp), style = KeelFont.body, color = theme.text.copy(alpha = 0.8f))
        Tiles(model)
        MoodSection(model)
        SymptomsSection(model)
        MedicinesSection(model)
        SleepSection(model, formats)
        BodySection(model)
    }
}

@Composable
private fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = KeelFont.eyebrow.copy(letterSpacing = 0.6.sp), color = LocalKeelTheme.current.muted)
}

private fun Modifier.statCard(theme: KeelTheme): Modifier {
    val shape = RoundedCornerShape(Radius.lg)
    return fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape)
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val theme = LocalKeelTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = KeelFont.serif(17.sp), color = theme.heading)
        Column(Modifier.statCard(theme).padding(16.dp), content = content)
    }
}

@Composable
private fun Tiles(model: StatsModel) {
    val theme = LocalKeelTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        model.tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value, caption) ->
                    Column(Modifier.weight(1f).statCard(theme).padding(14.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Eyebrow(label)
                        Text(value, style = KeelFont.serif(24.sp), color = theme.heading)
                        Text(caption, style = KeelFont.caption, color = theme.muted)
                    }
                }
            }
        }
    }
}

/** Calm mood tones from the theme; amber is Keel's alert colour, so it isn't used here. */
private fun moodColour(mood: Mood, theme: KeelTheme): Color = when (mood) {
    Mood.GREAT -> theme.sage
    Mood.GOOD -> theme.sageSoft
    Mood.OKAY -> theme.sand
    Mood.LOW -> theme.copper
    Mood.DIFFICULT -> theme.accent
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodSection(model: StatsModel) {
    val theme = LocalKeelTheme.current
    Section("Mood & Energy") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Eyebrow("Mood distribution")
            val shown = Mood.entries.filter { model.moodFraction(it) > 0 }
            if (shown.isEmpty()) {
                Text("No entries yet.", style = KeelFont.body, color = theme.muted)
            } else {
                Row(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(10.dp)).semantics { contentDescription = shown.joinToString(", ") { model.moodLabel(it) } }) {
                    shown.forEach { Box(Modifier.weight(model.moodFraction(it).toFloat()).fillMaxHeight().background(moodColour(it, theme))) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    shown.forEach { mood ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(moodColour(mood, theme)))
                            Text(model.moodLabel(mood), style = KeelFont.sans(12.sp), color = theme.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SymptomsSection(model: StatsModel) {
    val theme = LocalKeelTheme.current
    Section("Symptoms") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Eyebrow("Frequency: days reported")
            model.vasomotorLine?.let { Text(it, style = KeelFont.caption, color = theme.text.copy(alpha = 0.7f)) }
            val pages = model.symptomPages
            when {
                pages.isEmpty() -> Text("No symptoms logged.", style = KeelFont.body, color = theme.muted)
                pages.size == 1 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { pages[0].forEach { SymptomRow(it, model) } }
                else -> {
                    // More than ten: pages she swipes between.
                    val pager = rememberPagerState { pages.size }
                    HorizontalPager(pager, verticalAlignment = Alignment.Top) { page ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { pages[page].forEach { SymptomRow(it, model) } }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                        pages.indices.forEach { i ->
                            Box(Modifier.size(7.dp).clip(CircleShape).background(if (i == pager.currentPage) theme.text.copy(alpha = 0.6f) else theme.track))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SymptomRow(item: Pair<String, Int>, model: StatsModel) {
    val theme = LocalKeelTheme.current
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row {
            Text(item.first, Modifier.weight(1f), style = KeelFont.body, color = theme.text.copy(alpha = 0.8f))
            Text(model.symptomDays(item.second), style = KeelFont.body, color = theme.muted)
        }
        val fraction = (item.second.toFloat() / maxOf(model.period.days, 1)).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(theme.track)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(CircleShape).background(theme.accent))
        }
    }
}

@Composable
private fun MedicinesSection(model: StatsModel) {
    val theme = LocalKeelTheme.current
    Section("Medications and supplements") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (model.data.activeMedicineCount == 0) {
                Text("No medications or supplements tracked.", style = KeelFont.body, color = theme.muted)
            } else {
                Text(model.medicinesLine, style = KeelFont.body, color = theme.text.copy(alpha = 0.85f))
                Text(model.medicinesCountLine, style = KeelFont.caption, color = theme.muted)
            }
        }
    }
}

@Composable
private fun SleepSection(model: StatsModel, formats: KeelDateFormats) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    var selected by remember(model.period) { mutableStateOf<Int?>(null) }
    Section("Sleep") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Hours per night", Modifier.weight(1f))
                if (model.sleepAverage > 0) Text("avg ${model.hours(model.sleepAverage)}", style = KeelFont.sans(12.sp), color = theme.text.copy(alpha = 0.6f))
            }
            if (!model.hasSleep) {
                Text("No sleep logged yet. Add it in a check-in, or connect to Health Connect.", style = KeelFont.body, color = theme.muted)
            } else {
                Text(model.sleepDetail(selected, formats::weekdayMonthDay), style = KeelFont.sans(13.sp, FontWeight.Medium), color = theme.text)
                Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    model.sleepSeries.forEachIndexed { i, h ->
                        val isSelected = selected == i
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .clickable(enabled = h > 0, role = Role.Button, onClickLabel = formats.weekdayMonthDay(model.sleepDays[i])) {
                                    haptic(KeelHapticKind.SELECTION)
                                    selected = if (isSelected) null else i
                                },
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                Modifier.fillMaxWidth().height(maxOf(h / 10 * 84, 3.0).dp).clip(RoundedCornerShape(3.dp))
                                    .background(theme.plum.copy(alpha = if (h > 0) (if (isSelected) 1f else 0.8f) else 0.15f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BodySection(model: StatsModel) {
    val theme = LocalKeelTheme.current
    Section("Body") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (model.hasVitals) {
                if (model.restingHeartRate.count >= 3) VitalLine("Resting heart rate", "bpm", model.restingHeartRate, model)
                if (model.heartRateVariability.count >= 3) VitalLine("Heart rate variability", "ms", model.heartRateVariability, model)
                if (model.weight.count >= 2) VitalLine("Weight", "kg", model.weight, model)
                model.bloodPressure?.let { bp ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Blood pressure", Modifier.weight(1f), style = KeelFont.body, color = theme.text.copy(alpha = 0.8f))
                        Text("avg $bp mmHg", style = KeelFont.sans(13.sp, FontWeight.Medium), color = theme.heading)
                    }
                }
                Text(model.vitalsFooter, style = KeelFont.caption, color = theme.muted)
            } else {
                Text(model.vitalsEmpty, style = KeelFont.body, color = theme.muted)
            }
        }
    }
}

@Composable
private fun VitalLine(title: String, unit: String, trend: VitalTrend, model: StatsModel) {
    val theme = LocalKeelTheme.current
    Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f), style = KeelFont.body, color = theme.text.copy(alpha = 0.8f))
        trend.average?.let { avg ->
            Text("avg $avg $unit", style = KeelFont.sans(13.sp, FontWeight.Medium), color = theme.heading)
            model.range(trend)?.let { Text(it, style = KeelFont.caption, color = theme.muted) }
        }
    }
}
