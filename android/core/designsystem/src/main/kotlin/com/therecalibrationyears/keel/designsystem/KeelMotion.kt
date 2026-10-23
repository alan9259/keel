package com.therecalibrationyears.keel.designsystem

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether she has turned animations off (Accessibility, "Remove animations", which sets the
 * animator duration scale to 0), Android's counterpart of iOS Reduce Motion. Compose already
 * runs one-off animations at that scale; a repeating one (the recording pulse) asks this and
 * stays still.
 */
fun animationsOff(resolver: ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** [animationsOff] for this screen. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { animationsOff(context.contentResolver) }
}
