package com.therecalibrationyears.keel

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

/** A `FragmentActivity` (plan §1.1) because Android's biometric prompt is shown from one. */
class MainActivity : FragmentActivity() {
    /**
     * Keel is the app in front. False while the app switcher (or another app's screen, such as
     * a permission prompt) is over it, even when Android keeps Keel resumed underneath, as it
     * does for the switcher's live card (Android 10+).
     */
    private val inFront = mutableStateOf(true)

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        inFront.value = isTopResumedActivity
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < 29) inFront.value = true // no top-resumed callback before 10
    }

    override fun onPause() {
        super.onPause()
        inFront.value = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleReminderTap(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val controller = (application as KeelApplication).container.controller
        installSplashScreen().setKeepOnScreenCondition { !controller.isReady }
        super.onCreate(savedInstanceState)
        DebugHarness.apply(intent)
        lifecycleScope.launch { DebugHarness.seed((application as KeelApplication).container) }
        if (savedInstanceState == null) handleReminderTap(intent)
        (application as KeelApplication).container.biometrics.attach(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                controller.lock.state.collect { hideInRecents(it?.isEnabled == true) }
            }
        }
        setContent {
            val appearance by controller.appearance.collectAsStateWithLifecycle()
            val themeId = DebugHarness.themeId ?: appearance?.themeId ?: ThemeCatalog.DEFAULT_ID
            val colourMode = DebugHarness.colourMode ?: ColourMode.fromRaw(appearance?.colourModeRaw)
            SystemBarsFollowKeel(themeId, colourMode)
            KeelThemeProvider(themeId = themeId, colourMode = colourMode, hapticsEnabled = appearance?.haptics ?: true) {
                if (!DebugHarness.content()) KeelRoot(controller, inFront = inFront.value)
            }
        }
    }
}

/**
 * While the lock is on, Keel's screen stays out of the app switcher (iOS covers it while
 * inactive; plan D16): Android 13+ shows a blank card; before 13 the window is marked secure,
 * which also blocks screenshots of Keel.
 */
private fun MainActivity.hideInRecents(on: Boolean) {
    if (Build.VERSION.SDK_INT >= 33) {
        setRecentsScreenshotEnabled(!on)
    } else if (on) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/**
 * A tap on a medication reminder's body marks the dose taken, as on iOS (D20). The intent is
 * then replaced, so recreating the screen can't log it twice.
 */
private fun MainActivity.handleReminderTap(intent: Intent?) {
    if (intent?.action != ReminderPoster.ACTION_TAP_MEDICATION) return
    val payload = com.therecalibrationyears.keel.platform.ReminderPayload.from(intent) ?: return
    val medicationId = payload.medicationId ?: return
    val controller = (application as KeelApplication).container.controller
    setIntent(Intent(this, MainActivity::class.java))
    lifecycleScope.launch { controller.markMedicationTaken(medicationId, payload.slotId, controller.reminders.doseDay(payload)) }
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
