package com.therecalibrationyears.keel.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.components.KeelPrimaryButton
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import com.therecalibrationyears.keel.designsystem.components.KeelTextField
import com.therecalibrationyears.keel.domain.Hysterectomy
import com.therecalibrationyears.keel.domain.ProfileForm
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the Profile screen asks of the app. */
interface ProfileActions {
    /** Her saved details and background, as the form shows them. */
    suspend fun load(): ProfileForm
    /** Save changes: her details and her background together. */
    suspend fun save(form: ProfileForm)
}

/** The form survives the screen being rebuilt (rotation, or Android reclaiming the app). */
private val FormSaver = listSaver<ProfileForm?, String>(
    save = { f ->
        if (f == null) emptyList()
        else listOf(f.firstName, f.lastName, f.age, f.mobile, f.email, f.hysterectomy?.raw.orEmpty(), f.hysterectomyYear, f.periodsNote)
    },
    restore = { v ->
        if (v.isEmpty()) null
        else ProfileForm(v[0], v[1], v[2], v[3], v[4], Hysterectomy.fromRaw(v[5].ifEmpty { null }), v[6], v[7])
    },
)

/**
 * Port of iOS `ProfileView` with `MyBackgroundFields`: her details, all optional and stored on
 * this phone, and her background (hysterectomy, periods), saved together by Save changes.
 */
@Composable
fun ProfileScreen(actions: ProfileActions, onBack: () -> Unit) {
    val theme = LocalKeelTheme.current
    val scope = rememberCoroutineScope()
    val haptic = KeelHaptics.rememberPerformer()
    var form by rememberSaveable(stateSaver = FormSaver) { mutableStateOf(null) }
    var justSaved by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (form == null) form = actions.load() }
    val scroll = rememberScrollState()
    LaunchedEffect(justSaved) {
        if (!justSaved) return@LaunchedEffect
        // The confirmation sits under the button: bring it into view, then let it fade after two seconds.
        withFrameNanos {}
        scroll.animateScrollTo(scroll.maxValue)
        delay(2_000)
        justSaved = false
    }

    KeelScreen(title = "Profile", subtitle = "Your details, stored on this phone", onBack = onBack, scrollState = scroll) {
        val f = form ?: return@KeelScreen // a moment before her profile is read
        fun edit(change: ProfileForm.() -> ProfileForm) { form = f.change() }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Heading("Your details")
            KeelTextField(
                "First name", f.firstName, { edit { copy(firstName = it) } }, placeholder = "What should we call you?",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), contentType = ContentType.PersonFirstName,
            )
            KeelTextField(
                "Last name (optional)", f.lastName, { edit { copy(lastName = it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), contentType = ContentType.PersonLastName,
            )
            KeelTextField(
                "Age (optional)", f.age, { edit { copy(age = it) } }, placeholder = "e.g. 48",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            KeelTextField(
                "Mobile (optional)", f.mobile, { edit { copy(mobile = it) } }, placeholder = "For your records",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), contentType = ContentType.PhoneNumber,
            )
            KeelTextField(
                "Email (optional)", f.email, { edit { copy(email = it) } }, placeholder = "you@example.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), contentType = ContentType.EmailAddress,
            )
        }

        MyBackground(f, onChange = { form = it }, modifier = Modifier.padding(top = 8.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KeelPrimaryButton("Save changes", Modifier.padding(top = 4.dp)) {
                scope.launch {
                    actions.save(f)
                    haptic(KeelHapticKind.SUCCESS)
                    justSaved = true
                }
            }
            AnimatedVisibility(justSaved, enter = fadeIn(), exit = fadeOut()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(KeelIcons.checkCircle, contentDescription = null, tint = theme.sage, modifier = Modifier.size(16.dp))
                    Text("Changes saved", style = KeelFont.caption, color = theme.sage)
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) =
    Text(text, Modifier.semantics { heading() }, style = KeelFont.serif(18.sp), color = LocalKeelTheme.current.heading)

/**
 * My background (iOS `MyBackgroundFields`): personal background she can add when she chooses.
 * Keel records it and changes nothing because of it; it reaches the GP summary only as
 * described there (the hysterectomy answer only if she turns it on).
 */
@Composable
private fun MyBackground(form: ProfileForm, onChange: (ProfileForm) -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Heading("My background")
            Text(
                "Answer only if you'd like to. Keel keeps this for your record and changes nothing because of it.",
                style = KeelFont.body, color = theme.muted,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(Hysterectomy.QUESTION, style = KeelFont.sans(15.sp, FontWeight.Medium), color = theme.text)
            val shape = RoundedCornerShape(Radius.card)
            Column(Modifier.fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape)) {
                Hysterectomy.entries.forEachIndexed { index, option ->
                    val selected = form.hysterectomy == option
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // Tapping her current answer again clears it (she can always leave it blank).
                            .selectable(selected = selected, role = Role.RadioButton) {
                                haptic(KeelHapticKind.SELECTION)
                                onChange(form.copy(hysterectomy = if (selected) null else option))
                            }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option.label, Modifier.weight(1f), style = KeelFont.body, color = theme.text)
                        Icon(
                            if (selected) KeelIcons.checkCircle else KeelIcons.radioUnchecked, contentDescription = null,
                            tint = if (selected) theme.accent else theme.border, modifier = Modifier.size(22.dp),
                        )
                    }
                    if (index < Hysterectomy.entries.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
                }
            }
            if (form.hysterectomy?.allowsYear == true) {
                KeelTextField(
                    "Year (if you know it)", form.hysterectomyYear, { onChange(form.copy(hysterectomyYear = it)) }, placeholder = "e.g. 2019",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            if (form.hysterectomy == Hysterectomy.YES_NOT_SURE_ABOUT_OVARIES) {
                Caption("Not being sure is common. It can be a useful question to take to your GP.")
            }
            Caption("It goes on your GP visit summary only if you turn it on when you make one.")
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Periods", style = KeelFont.sans(15.sp, FontWeight.Medium), color = theme.text)
            KeelTextField(
                "Periods no longer apply (optional)", form.periodsNote, { onChange(form.copy(periodsNote = it)) },
                placeholder = "e.g. after menopause or a hysterectomy",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Caption("If you fill this in, your GP visit summary notes it instead of period details.")
        }
    }
}

@Composable
private fun Caption(text: String) =
    Text(text, Modifier.padding(horizontal = 4.dp), style = KeelFont.caption, color = LocalKeelTheme.current.muted)
