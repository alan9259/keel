package com.therecalibrationyears.keel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelMark
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.ThemeCatalog
import com.therecalibrationyears.keel.designsystem.components.CalloutCard
import com.therecalibrationyears.keel.designsystem.components.EmojiGlyph
import com.therecalibrationyears.keel.designsystem.components.glyphOnly
import com.therecalibrationyears.keel.designsystem.components.HeroCard
import com.therecalibrationyears.keel.designsystem.components.InfoNoteCard
import com.therecalibrationyears.keel.designsystem.components.KeelPrimaryButton
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import com.therecalibrationyears.keel.designsystem.components.KeelSecondaryButton
import com.therecalibrationyears.keel.designsystem.components.KeelSegmented
import com.therecalibrationyears.keel.designsystem.components.KeelTextEditor
import com.therecalibrationyears.keel.designsystem.components.KeelTextField
import com.therecalibrationyears.keel.designsystem.components.KeelTextLink
import com.therecalibrationyears.keel.designsystem.components.KeelToast
import com.therecalibrationyears.keel.designsystem.components.RadioCard
import com.therecalibrationyears.keel.designsystem.components.VitalLineChart
import com.therecalibrationyears.keel.designsystem.components.StandardCard
import com.therecalibrationyears.keel.designsystem.components.SymptomChip
import com.therecalibrationyears.keel.designsystem.components.ToastData

/**
 * Debug-only gallery of every design-system component, for screenshots in each theme and
 * colour mode (milestone M0). Labels here are sample text for the gallery only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComponentGallery(scrollToBottom: Boolean) {
    val theme = LocalKeelTheme.current
    val scroll = rememberScrollState()
    if (scrollToBottom) LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    var segment by remember { mutableIntStateOf(1) }
    var field by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var radio by remember { mutableIntStateOf(1) }
    val levels = remember { mutableStateOf(listOf(0, 1, 2, 3)) }

    KeelScreen(title = "Component gallery", subtitle = "Design system (debug only)", onBack = {}, scrollState = scroll) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            KeelMark(Modifier.size(64.dp))
            Column {
                Text("Keel", style = KeelFont.h2, color = theme.heading)
                Text("PERIMENOPAUSE COMPANION", style = KeelFont.eyebrow, color = theme.plum)
            }
        }
        Text("Swatches", style = KeelFont.h3, color = theme.heading)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(theme.accent, theme.plum, theme.sage, theme.sand, theme.copper, theme.sageSoft, theme.attention)
                .forEach { Box(Modifier.size(32.dp).clip(CircleShape).background(it)) }
        }
        Text("Theme: ${ThemeCatalog.all.joinToString { it.name }}", style = KeelFont.caption, color = theme.muted)
        Text("Mood emoji (Twemoji)", style = KeelFont.h3, color = theme.heading)
        // The six iOS mood packs (Keel/Models/MoodPack.swift), worst to best.
        listOf(
            "😣😔😐🙂😊", "🥀🍂🌾🌿🌸", "⛈️🌀🌅🐚🌊", "⛈️🌧️⛅🌤️☀️", "🌑🌒🌓🌔🌕", "🐚🦔🐢🐦🦋",
        ).forEach { pack ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pack.glyphOnly().codePoints().toArray().forEach { EmojiGlyph(String(Character.toChars(it)), size = 30.dp) }
            }
        }
        KeelPrimaryButton("Primary button") {}
        KeelPrimaryButton("Disabled", isEnabled = false) {}
        KeelSecondaryButton("Secondary button") {}
        KeelTextLink("Text link", emphasized = " with emphasis") {}
        KeelSegmented(listOf("Week", "Month", "3 Months"), segment, { segment = it })
        Text("Symptom chips", style = KeelFont.h3, color = theme.heading)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Hot flushes", "Night sweats", "Brain fog", "Joint pain").forEachIndexed { i, name ->
                SymptomChip(name, levels.value[i]) {
                    levels.value = levels.value.toMutableList().also { it[i] = (it[i] + 1) % 4 }
                }
            }
        }
        RadioCard("On my own for now", "Radio card, not selected", isSelected = radio == 0) { radio = 0 }
        RadioCard("Working with a doctor", "Radio card, selected", isSelected = radio == 1) { radio = 1 }
        KeelTextField("First name (optional)", field, { field = it }, placeholder = "What should we call you?")
        KeelTextEditor(note, { note = it }, placeholder = "Anything on your mind…")
        StandardCard { Text("Standard card", style = KeelFont.body, color = theme.text) }
        HeroCard { Text("Hero card", style = KeelFont.body, color = theme.text) }
        CalloutCard { Text("Callout card", style = KeelFont.body, color = theme.text) }
        InfoNoteCard(lead = "Info note:", message = "sage tint with a bold lead-in.")
        // Sample readings (debug only): the vitals chart has no real data before Health Connect.
        StandardCard {
            val start = java.time.LocalDate.of(2026, 10, 1)
            VitalLineChart(
                listOf(62.0, 61.0, 63.0, 60.0, 64.0, 62.5, 61.0, 65.0, 63.0, 62.0).mapIndexed { i, v ->
                    com.therecalibrationyears.keel.domain.VitalTrend.Point(start.plusDays(i.toLong()), v)
                },
                theme.accent,
            )
        }
        Box(Modifier.align(Alignment.CenterHorizontally)) { KeelToast(ToastData("Entry saved.", "Toast preview")) }
    }
}
