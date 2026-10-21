package com.therecalibrationyears.keel.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.therecalibrationyears.keel.designsystem.KeelDateFormats
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.components.HeroCard
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import com.therecalibrationyears.keel.designsystem.components.StandardCard
import com.therecalibrationyears.keel.designsystem.lineSpacing
import com.therecalibrationyears.keel.domain.Insight
import com.therecalibrationyears.keel.domain.InsightAccent
import com.therecalibrationyears.keel.domain.MonthSummary
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/** What Looking back shows, read live by the app. */
data class LookingBackData(
    val summary: MonthSummary,
    /** Whether she has written anything ever ("See all notes" covers every month). */
    val hasAnyNotes: Boolean,
    val insights: List<Insight>,
)

/** What Looking back and Your notes ask of the app. */
interface LookingBackActions {
    val data: Flow<LookingBackData>
    /** Every note she has written, newest first. */
    val allNotes: Flow<List<MonthSummary.Note>>
    val zone: ZoneId
    fun now(): Instant
}

/**
 * Port of iOS `PatternsView` ("Looking back"): a fixed-wording summary of this month from her
 * own check-ins, what she wrote, any pattern cards, and the GP Visit Summary. No generated text.
 */
@Composable
fun LookingBackScreen(actions: LookingBackActions, onBack: () -> Unit, onSeeAllNotes: () -> Unit, onGpSummary: () -> Unit) {
    val data by actions.data.collectAsStateWithLifecycle(initialValue = null)
    KeelScreen(title = "Looking back", onBack = onBack) {
        val loaded = data ?: return@KeelScreen
        MonthCard(loaded.summary)
        NotesSection(loaded.summary, loaded.hasAnyNotes, actions, onSeeAllNotes)
        if (loaded.insights.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { loaded.insights.forEach { InsightCard(it) } }
        }
        GpSummaryButton(onGpSummary)
    }
}

/** Every note she has written, newest first (iOS `AllNotesView`). */
@Composable
fun AllNotesScreen(actions: LookingBackActions, onBack: () -> Unit) {
    val theme = LocalKeelTheme.current
    val notes by actions.allNotes.collectAsStateWithLifecycle(initialValue = null)
    KeelScreen(title = "Your notes", onBack = onBack) {
        val loaded = notes ?: return@KeelScreen
        if (loaded.isEmpty()) {
            Text("Nothing written yet.", style = KeelFont.body, color = theme.muted)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { loaded.forEach { NoteCard(it, actions) } }
        }
    }
}

@Composable
private fun MonthCard(summary: MonthSummary) {
    val theme = LocalKeelTheme.current
    HeroCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(summary.heading.uppercase(), style = KeelFont.eyebrow.copy(letterSpacing = 1.sp), color = theme.muted)
                Text(summary.checkInLine, style = KeelFont.serif(19.sp), color = theme.heading)
            }
            if (summary.daysCheckedIn > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Symptoms you logged most often", style = KeelFont.sans(15.sp, FontWeight.Medium), color = theme.text)
                    if (summary.topSymptoms.isEmpty()) {
                        Text("No symptoms logged this month.", style = KeelFont.body, color = theme.muted)
                    } else {
                        summary.topSymptoms.forEach { item ->
                            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, Modifier.weight(1f), style = KeelFont.body, color = theme.text)
                                Text(MonthSummary.days(item.days), style = KeelFont.body.copy(fontFeatureSettings = "tnum"), color = theme.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesSection(summary: MonthSummary, hasAnyNotes: Boolean, actions: LookingBackActions, onSeeAllNotes: () -> Unit) {
    val theme = LocalKeelTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("What you wrote", style = KeelFont.serif(18.sp), color = theme.heading)
        if (summary.notes.isEmpty()) {
            Text("Nothing written this month.", style = KeelFont.body, color = theme.muted)
        } else {
            summary.notes.take(MonthSummary.NOTE_PREVIEW_LIMIT).forEach { NoteCard(it, actions, maxLines = 4) }
        }
        if (hasAnyNotes) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onSeeAllNotes),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("See all notes", Modifier.weight(1f), style = KeelFont.sans(15.sp, FontWeight.Medium), color = theme.accent)
                Icon(KeelIcons.chevronRight, null, tint = theme.accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** One of her notes with its date; the year shows only when it isn't this year. */
@Composable
private fun NoteCard(note: MonthSummary.Note, actions: LookingBackActions, maxLines: Int = Int.MAX_VALUE) {
    val theme = LocalKeelTheme.current
    val context = LocalContext.current
    val formats = remember(actions.zone) { KeelDateFormats(context, zone = actions.zone) }
    val day = note.date.atZone(actions.zone).toLocalDate()
    val sameYear = day.year == actions.now().atZone(actions.zone).year
    StandardCard(padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (sameYear) formats.weekdayMonthDay(day) else formats.weekdayMonthDayYear(day), style = KeelFont.caption, color = theme.muted)
            Text(
                note.text, style = KeelFont.body.lineSpacing(2f), color = theme.text.copy(alpha = 0.85f),
                maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InsightCard(insight: Insight) {
    val theme = LocalKeelTheme.current
    val accent: Color = when (insight.accent) {
        InsightAccent.TERRACOTTA -> theme.accent
        InsightAccent.SAGE -> theme.sage
        InsightAccent.WARM_GREY -> theme.heading
    }
    StandardCard(padding = 20.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                Icon(KeelIcons.forSymbolName(insight.iconKey), null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(insight.title, style = KeelFont.serif(18.sp), color = theme.heading)
                Text(insight.detail, style = KeelFont.body.lineSpacing(2f), color = theme.text.copy(alpha = 0.8f))
                Text(insight.timeframe, style = KeelFont.caption.copy(fontStyle = FontStyle.Italic), color = theme.muted)
            }
        }
    }
}

/** Always shown: Looking back leads to the GP Visit Summary. */
@Composable
private fun GpSummaryButton(onClick: () -> Unit) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(theme.accentTint), contentAlignment = Alignment.Center) {
            Icon(KeelIcons.document, null, tint = theme.accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Seeing your GP soon?", style = KeelFont.sans(16.sp, FontWeight.Medium), color = theme.text)
            Text("Turn this into a GP Visit Summary", style = KeelFont.caption, color = theme.muted)
        }
        Icon(KeelIcons.chevronRight, null, tint = theme.muted, modifier = Modifier.size(18.dp))
    }
}
