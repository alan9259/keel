package com.therecalibrationyears.keel.onboarding

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Spacing
import com.therecalibrationyears.keel.designsystem.components.KeelPrimaryButton
import com.therecalibrationyears.keel.designsystem.components.KeelTextField
import kotlinx.coroutines.launch

/**
 * Port of iOS `CreateAccountView`: what Keel should call her. First name only, optional,
 * stored on this phone. There are no accounts (no sign-in, no sync, no server).
 */
@Composable
internal fun CreateAccountScreen(saveFirstName: suspend (String) -> Unit, onContinue: () -> Unit) {
    val theme = LocalKeelTheme.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var name by rememberSaveable { mutableStateOf("") }

    fun finish() {
        focus.clearFocus()
        scope.launch {
            saveFirstName(name.trim())
            onContinue()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(Spacing.xl))
        OnboardingTitle("Let's set you up", align = TextAlign.Start)
        OnboardingSubtitle(
            "Just your first name, if you'd like. It's stored on this phone, and you can change it any time in Profile.",
            color = theme.muted,
            modifier = Modifier.padding(bottom = Spacing.sm),
            align = TextAlign.Start,
        )
        KeelTextField(
            label = "First name (optional)",
            value = name,
            onValueChange = { name = it },
            placeholder = "What should we call you?",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { finish() }),
            contentType = ContentType.PersonFirstName,
        )
        KeelPrimaryButton("Continue", Modifier.padding(top = Spacing.xs), onClick = ::finish)
        Text(
            "We never sell your data. Ever.",
            Modifier.fillMaxWidth().padding(top = Spacing.xs),
            style = KeelFont.caption.copy(textAlign = TextAlign.Center),
            color = theme.muted,
        )
    }
}
