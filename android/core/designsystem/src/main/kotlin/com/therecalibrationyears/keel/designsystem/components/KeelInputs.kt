package com.therecalibrationyears.keel.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.Spacing

/**
 * Labelled single-line field: input fill, 14 radius, accent focus border (port of iOS
 * `KeelTextField`). Keel has no passwords, so there is no secure variant.
 */
@Composable
fun KeelTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    /** Autofill hint (iOS `textContentType`), e.g. [ContentType.PersonFirstName]. */
    contentType: ContentType? = null,
) {
    val theme = LocalKeelTheme.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val borderColour by animateColorAsState(if (focused) theme.accent else theme.border, tween(150), label = "fieldBorder")
    val borderWidth by animateDpAsState(if (focused) 2.dp else 1.dp, tween(150), label = "fieldBorderWidth")
    val shape = RoundedCornerShape(Radius.md)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = KeelFont.bodyLarge.copy(color = theme.text),
        cursorBrush = SolidColor(theme.accent),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = source,
        modifier = modifier
            .then(if (contentType != null) Modifier.semantics { this.contentType = contentType } else Modifier)
            .fillMaxWidth(),
        // The label sits inside the field (as in Material's text fields) so TalkBack reads it
        // with the field, not as a separate line before an unnamed edit box.
        decorationBox = { inner ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(label, style = KeelFont.body.copy(fontWeight = FontWeight.Medium), color = theme.text)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(theme.inputBackground)
                        .border(borderWidth, borderColour, shape)
                        .padding(16.dp),
                ) {
                    // One line, like the iOS placeholder, so the field doesn't change height as she types.
                    if (value.isEmpty()) Text(placeholder, style = KeelFont.bodyLarge, color = theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
            }
        },
    )
}

/**
 * Multi-line note editor with a placeholder and a trailing accessory slot (the voice
 * mic), port of iOS `KeelTextEditor`.
 */
@Composable
fun KeelTextEditor(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 110.dp,
    accessory: @Composable BoxScope.() -> Unit = {},
) {
    val theme = LocalKeelTheme.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val borderColour by animateColorAsState(if (focused) theme.accent else theme.border, tween(150), label = "editorBorder")
    val borderWidth by animateDpAsState(if (focused) 2.dp else 1.dp, tween(150), label = "editorBorderWidth")
    val shape = RoundedCornerShape(Radius.card)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(shape)
            .background(theme.card)
            .border(borderWidth, borderColour, shape),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = KeelFont.body.copy(color = theme.text),
            cursorBrush = SolidColor(theme.accent),
            interactionSource = source,
            modifier = Modifier.fillMaxWidth().heightIn(min = minHeight).padding(16.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = KeelFont.body, color = theme.muted)
                    inner()
                }
            },
        )
        Box(Modifier.align(Alignment.BottomEnd).padding(10.dp), content = accessory)
    }
}
