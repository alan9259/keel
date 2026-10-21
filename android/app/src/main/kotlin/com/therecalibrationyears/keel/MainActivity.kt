package com.therecalibrationyears.keel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.therecalibrationyears.keel.designsystem.ColourMode
import com.therecalibrationyears.keel.designsystem.KeelTheme
import com.therecalibrationyears.keel.designsystem.KeelThemeProvider
import com.therecalibrationyears.keel.designsystem.ThemeCatalog
import com.therecalibrationyears.keel.designsystem.isDarkAppearance

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val controller = (application as KeelApplication).container.controller
        installSplashScreen().setKeepOnScreenCondition { !controller.isReady }
        super.onCreate(savedInstanceState)
        DebugHarness.apply(intent)
        lifecycleScope.launch { DebugHarness.seed((application as KeelApplication).container) }
        setContent {
            val appearance by controller.appearance.collectAsStateWithLifecycle()
            val themeId = DebugHarness.themeId ?: appearance?.themeId ?: ThemeCatalog.DEFAULT_ID
            val colourMode = DebugHarness.colourMode ?: ColourMode.fromRaw(appearance?.colourModeRaw)
            SystemBarsFollowKeel(themeId, colourMode)
            KeelThemeProvider(themeId = themeId, colourMode = colourMode, hapticsEnabled = appearance?.haptics ?: true) {
                if (!DebugHarness.content()) KeelRoot(controller)
            }
        }
    }
}

/**
 * Status and navigation bars follow Keel's colour mode and theme, not the system's: the
 * page draws behind them (edge-to-edge), so their icons must contrast with it. With
 * 3-button navigation (Android 9 and similar) the bar gets a scrim of the page colour.
 */
@Composable
private fun ComponentActivity.SystemBarsFollowKeel(themeId: String, colourMode: ColourMode) {
    val systemDark = isSystemInDarkTheme()
    val dark = isDarkAppearance(themeId, colourMode, systemDark)
    val scrim = KeelTheme.resolve(themeId, colourMode.isDark(systemDark)).background.copy(alpha = 0.9f).toArgb()
    DisposableEffect(dark, scrim) {
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(transparent, transparent) { dark },
            navigationBarStyle = SystemBarStyle.auto(scrim, scrim) { dark },
        )
        onDispose {}
    }
}
