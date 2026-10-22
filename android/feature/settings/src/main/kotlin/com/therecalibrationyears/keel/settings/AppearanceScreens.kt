package com.therecalibrationyears.keel.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.therecalibrationyears.keel.designsystem.ColourMode
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.KeelThemeOption
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.ThemeCatalog
import com.therecalibrationyears.keel.designsystem.components.EmojiGlyph
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import com.therecalibrationyears.keel.domain.Mood
import com.therecalibrationyears.keel.domain.MoodPack
import com.therecalibrationyears.keel.domain.MoodPacks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** What the theme, colour mode and mood icon screens ask of the app. */
interface AppearanceActions {
    /** Her chosen ids; null until she chooses (the defaults then apply). */
    val themeId: Flow<String?>
    val colourModeRaw: Flow<String?>
    val moodPackId: Flow<String?>
    suspend fun setThemeId(id: String)
    suspend fun setColourMode(mode: ColourMode)
    suspend fun setMoodPackId(id: String)
}

/** Port of iOS `ThemesView`. Every theme is hers to use (plan D23: no purchases yet). */
@Composable
fun ThemesScreen(actions: AppearanceActions, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = KeelHaptics.rememberPerformer()
    val chosen by actions.themeId.collectAsStateWithLifecycle(null)
    val active = ThemeCatalog.option(chosen ?: ThemeCatalog.DEFAULT_ID)
    KeelScreen(title = "Themes", subtitle = "Make Keel feel like yours", onBack = onBack) {
        ActiveThemeCard(active)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemeCatalog.all.forEach { option ->
                ThemeRow(option, isActive = option.id == active.id) {
                    haptic(KeelHapticKind.SELECTION)
                    scope.launch { actions.setThemeId(option.id) }
                }
            }
        }
    }
}

@Composable
private fun ActiveThemeCard(active: KeelThemeOption) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(theme.accent.copy(alpha = 0.06f)).border(1.dp, theme.accentBorder, shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionEyebrow("Current theme", tracking = 1.5.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(active.name, style = KeelFont.serif(20.sp), color = theme.heading)
                Text(active.detail, style = KeelFont.caption, color = theme.muted)
            }
            Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                active.swatches.forEach { Swatch(it, 18.dp) }
            }
        }
    }
}

@Composable
private fun ThemeRow(option: KeelThemeOption, isActive: Boolean, onSelect: () -> Unit) {
    val theme = LocalKeelTheme.current
    SelectableCard(isActive, onSelect) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { option.swatches.take(3).forEach { Swatch(it, 12.dp) } }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(option.name, style = KeelFont.bodyLarge, color = theme.text)
                    if (isActive) ActiveBadge()
                }
                Text(option.detail, style = KeelFont.caption, color = theme.muted)
            }
        }
    }
}

@Composable
private fun Swatch(colour: Color, size: Dp) = Box(Modifier.size(size).clip(CircleShape).background(colour))

/** Port of iOS `ColourModeView`. */
@Composable
fun ColourModeScreen(actions: AppearanceActions, onBack: () -> Unit) {
    val theme = LocalKeelTheme.current
    val scope = rememberCoroutineScope()
    val haptic = KeelHaptics.rememberPerformer()
    val raw by actions.colourModeRaw.collectAsStateWithLifecycle(null)
    val mode = ColourMode.fromRaw(raw)
    val showing = if (mode.isDark(isSystemInDarkTheme())) "Dark" else "Light"
    KeelScreen(title = "Colour Mode", subtitle = "Currently showing: $showing", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ColourMode.entries.forEach { option ->
                ColourModeRow(option, isActive = option == mode) {
                    haptic(KeelHapticKind.SELECTION)
                    scope.launch { actions.setColourMode(option) }
                }
            }
            Text(
                "Your preference is saved and applied immediately across the whole app.",
                Modifier.fillMaxWidth().padding(top = 8.dp),
                style = KeelFont.caption.copy(textAlign = TextAlign.Center),
                color = theme.muted,
            )
        }
    }
}

/** iOS `ColourMode.label` / `detail` / `symbol`, in its order (light, dark, system). */
private val ColourMode.label get() = when (this) {
    ColourMode.LIGHT -> "Light"
    ColourMode.DARK -> "Dark"
    ColourMode.SYSTEM -> "System"
}

private val ColourMode.detail get() = when (this) {
    ColourMode.LIGHT -> "Warm and bright, all day."
    ColourMode.DARK -> "Easy on the eyes at night."
    ColourMode.SYSTEM -> "Follow your device setting."
}

private val ColourMode.icon get() = when (this) {
    ColourMode.LIGHT -> KeelIcons.sun
    ColourMode.DARK -> KeelIcons.moon
    ColourMode.SYSTEM -> KeelIcons.gear
}

@Composable
private fun ColourModeRow(mode: ColourMode, isActive: Boolean, onSelect: () -> Unit) {
    val theme = LocalKeelTheme.current
    SelectableCard(isActive, onSelect) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(theme.inputBackground),
                contentAlignment = Alignment.Center,
            ) { Icon(mode.icon, contentDescription = null, tint = if (isActive) theme.accent else theme.muted, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(mode.label, style = KeelFont.bodyLarge, color = theme.text)
                Text(mode.detail, style = KeelFont.body, color = theme.muted)
            }
            if (isActive) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(theme.accent), contentAlignment = Alignment.Center) {
                    Icon(KeelIcons.check, contentDescription = null, tint = theme.background, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

/** Port of iOS `MoodIconsView`. */
@Composable
fun MoodIconsScreen(actions: AppearanceActions, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = KeelHaptics.rememberPerformer()
    val chosen by actions.moodPackId.collectAsStateWithLifecycle(null)
    val active = MoodPacks.pack(chosen)
    KeelScreen(title = "Mood Icons", subtitle = "Choose how your moods look", onBack = onBack) {
        MoodPreviewCard(active)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MoodPacks.all.forEach { pack ->
                MoodPackRow(pack, isActive = pack.id == active.id) {
                    haptic(KeelHapticKind.SELECTION)
                    scope.launch { actions.setMoodPackId(pack.id) }
                }
            }
        }
    }
}

@Composable
private fun MoodPreviewCard(active: MoodPack) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionEyebrow("Preview: active set", tracking = 1.5.sp)
        Row {
            Mood.entries.forEach { mood ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EmojiGlyph(active.emojiFor(mood), size = 30.dp)
                    Text(mood.label, style = KeelFont.sans(11.sp), color = theme.muted)
                }
            }
        }
    }
}

@Composable
private fun MoodPackRow(pack: MoodPack, isActive: Boolean, onSelect: () -> Unit) {
    val theme = LocalKeelTheme.current
    SelectableCard(isActive, onSelect) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(pack.name, style = KeelFont.bodyLarge, color = theme.text)
                if (isActive) ActiveBadge()
            }
            Text(pack.detail, style = KeelFont.caption, color = theme.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { pack.emoji.forEach { EmojiGlyph(it, size = 22.dp) } }
        }
    }
}

/** A card she taps to choose; the chosen one has a 2dp accent outline. */
@Composable
private fun SelectableCard(isActive: Boolean, onSelect: () -> Unit, content: @Composable () -> Unit) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.card)
            .border(if (isActive) 2.dp else 1.dp, if (isActive) theme.accent else theme.border, shape)
            .selectable(selected = isActive, role = Role.RadioButton, onClick = onSelect)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun ActiveBadge() {
    val theme = LocalKeelTheme.current
    Text(
        "Active",
        Modifier.clip(RoundedCornerShape(50)).background(theme.accent.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp),
        style = KeelFont.sans(11.sp), color = theme.accent,
    )
}
