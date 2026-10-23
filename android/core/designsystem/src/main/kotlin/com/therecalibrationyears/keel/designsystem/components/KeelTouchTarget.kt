package com.therecalibrationyears.keel.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role

/**
 * A tap target sized by [modifier] (at least 48 dp, Android's minimum) around a smaller shape
 * drawn [inset] from its edges, for grids of days where the iOS shape is under 48 dp and the
 * cells touch, so Compose can't widen the target itself. The press ripple stays on the shape;
 * [visual] styles it (background, border), clipped to [shape]. TalkBack's semantics go on
 * [modifier], which is the whole target.
 */
@Composable
fun InsetTouchTarget(
    onClick: () -> Unit,
    inset: PaddingValues,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    visual: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Box(modifier.clickable(source, indication = null, enabled = enabled, role = role, onClick = onClick)) {
        Box(
            Modifier.fillMaxSize().padding(inset).clip(shape).then(visual).indication(source, ripple()),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}

/**
 * Keeps taps on this layer from reaching what's drawn beneath it. Compose hands a tap to
 * whatever under the finger handles taps, so a layer drawn over another screen in the same
 * window (the check-in over Home, the floating header over a list) passes taps on its empty
 * parts through to the hidden screen unless it claims them. Its own buttons still work.
 */
fun Modifier.blocksTouchesBelow(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}
