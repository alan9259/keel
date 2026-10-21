package com.therecalibrationyears.keel.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextAlign
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Spacing
import com.therecalibrationyears.keel.designsystem.components.KeelPrimaryButton
import com.therecalibrationyears.keel.designsystem.components.RadioCard
import com.therecalibrationyears.keel.domain.Pathway
import kotlinx.coroutines.launch

/** Port of iOS `PathwayView`. Nothing is pre-selected: she taps her answer. */
@Composable
internal fun PathwayScreen(setPathway: suspend (Pathway) -> Unit, onContinue: () -> Unit) {
    val theme = LocalKeelTheme.current
    val scope = rememberCoroutineScope()
    var selection by rememberSaveable { mutableStateOf<Pathway?>(null) }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier
                .weight(1f)
                .clipToBounds()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenH)
                // Breathing room so the last option clears the pinned button.
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.md))
            OnboardingTitle("How are you managing things at the moment?", align = TextAlign.Start)
            OnboardingSubtitle(
                "There's no right answer, and you can change this any time.",
                color = theme.muted,
                modifier = Modifier.padding(bottom = Spacing.md),
                align = TextAlign.Start,
            )
            Pathway.entries.forEach { pathway ->
                RadioCard(pathway.title, pathway.detail, isSelected = selection == pathway) { selection = pathway }
            }
        }
        KeelPrimaryButton(
            "Continue",
            Modifier
                .fillMaxWidth()
                .background(theme.background)
                .padding(start = Spacing.screenH, end = Spacing.screenH, top = Spacing.sm, bottom = Spacing.xl),
            isEnabled = selection != null,
        ) {
            scope.launch {
                selection?.let { setPathway(it) }
                onContinue()
            }
        }
    }
}
