package com.therecalibrationyears.keel.designsystem

import androidx.compose.ui.unit.dp

/** 8pt spacing scale (port of iOS `Spacing`). */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val xxxl = 64.dp

    /** Standard horizontal screen padding. */
    val screenH = 24.dp
}

/** Corner radii (port of iOS `Radius`). */
object Radius {
    val sm = 8.dp
    val input = 12.dp
    val md = 14.dp
    val card = 18.dp
    val lg = 16.dp
    val xl = 20.dp
}

/** Minimum touch target on Android (iOS uses 44pt; Material asks for 48dp). */
val MinTouchTarget = 48.dp

/**
 * iOS draws a disabled control in SwiftUI's plain button style at half the opacity it's given
 * (a future day, an empty night, the Delete button before "DELETE" is typed). Android
 * multiplies by this where iOS uses that style, so those controls look as faint as on iPhone.
 */
const val DisabledPlainAlpha = 0.5f
