package com.therecalibrationyears.keel.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.MinTouchTarget
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.components.EmojiGlyph
import com.therecalibrationyears.keel.domain.Mood
import com.therecalibrationyears.keel.domain.MoodPack

/**
 * The quick mood step (port of iOS `EntrySheet`, a bottom sheet). Picking a mood hands
 * over to the detail screen with that mood set: this slide is the only mood picker.
 * [onPick] gets null for "Remind me later".
 */
@Composable
fun EntrySheetContent(greeting: String, pack: MoodPack, onPick: (Mood?) -> Unit) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    Column(
        Modifier.fillMaxWidth().padding(start = 30.dp, end = 30.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(greeting, style = KeelFont.serif(28.sp).copy(textAlign = TextAlign.Center), color = theme.heading)
            Text("How are you feeling right now?", style = KeelFont.bodyLarge.copy(textAlign = TextAlign.Center), color = theme.muted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Mood.entries.forEach { mood ->
                val shape = RoundedCornerShape(Radius.card)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(theme.card)
                        .border(2.dp, theme.border, shape)
                        .clickable(role = Role.Button) { haptic(KeelHapticKind.SELECTION); onPick(mood) }
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EmojiGlyph(pack.emojiFor(mood), Modifier.heightIn(min = 34.dp), size = 30.dp)
                    Text(mood.label, style = KeelFont.sans(11.sp), color = theme.muted, maxLines = 1)
                }
            }
        }
        Text(
            "Remind me later",
            Modifier.heightIn(min = MinTouchTarget).clickable(role = Role.Button) { onPick(null) }.padding(horizontal = 12.dp, vertical = 12.dp),
            style = KeelFont.body,
            color = theme.muted,
        )
    }
}
