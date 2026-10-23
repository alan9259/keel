package com.therecalibrationyears.keel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.therecalibrationyears.keel.designsystem.ColourMode
import com.therecalibrationyears.keel.designsystem.KeelThemeProvider
import com.therecalibrationyears.keel.designsystem.ThemeCatalog

/**
 * What Health Connect opens from its permission dialog and its list of apps ("Read privacy
 * policy", plan §3.5): Keel's Your privacy. It shows nothing of hers, so it isn't behind the
 * app lock, and back returns to Health Connect.
 */
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = (application as KeelApplication).container.controller
        setContent {
            val appearance by controller.appearance.collectAsStateWithLifecycle()
            val themeId = appearance?.themeId ?: ThemeCatalog.DEFAULT_ID
            val colourMode = ColourMode.fromRaw(appearance?.colourModeRaw)
            SystemBarsFollowKeel(themeId, colourMode)
            KeelThemeProvider(themeId = themeId, colourMode = colourMode, hapticsEnabled = appearance?.haptics ?: true) {
                PhoneWidth { YourPrivacyRoute(controller.appInfo, onBack = ::finish) }
            }
        }
    }
}
