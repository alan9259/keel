package com.therecalibrationyears.keel.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.designsystem.KeelHapticKind
import com.therecalibrationyears.keel.designsystem.KeelHaptics
import com.therecalibrationyears.keel.designsystem.KeelIcons
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.Radius
import com.therecalibrationyears.keel.designsystem.Spacing
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import com.therecalibrationyears.keel.designsystem.components.KeelToggleRow
import com.therecalibrationyears.keel.domain.AppLockRules
import com.therecalibrationyears.keel.domain.BiometricOutcome
import com.therecalibrationyears.keel.domain.LockState
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** What the app lock screens ask of the app (iOS `AppLockService`, as the views use it). */
interface AppLockActions {
    val state: Flow<LockState>
    /** A biometric is enrolled here, so it can be offered. */
    val biometricsAvailable: Boolean
    /** Lead with biometrics: her preference and one being enrolled. */
    fun useBiometrics(state: LockState): Boolean
    val canRecoverWithScreenLock: Boolean
    fun now(): Instant
    suspend fun setPIN(pin: String): Boolean
    suspend fun disable()
    suspend fun setBiometricUnlock(on: Boolean)
    suspend fun verifyPIN(pin: String): Boolean
    fun isLockedOut(): Boolean
    suspend fun unlockWithBiometrics(): BiometricOutcome
    /** Forgot PIN: the phone's own screen lock proves it's her. */
    suspend fun authenticateOwner(): Boolean
}

/**
 * Port of iOS `AppLockSettingsView`: turn the lock on (by setting a PIN), choose whether
 * biometrics unlock it, change the PIN. Android names no particular biometric (plan D15).
 */
@Composable
fun AppLockScreen(actions: AppLockActions, onBack: () -> Unit) {
    val theme = LocalKeelTheme.current
    val scope = rememberCoroutineScope()
    val haptic = KeelHaptics.rememberPerformer()
    val state by actions.state.collectAsStateWithLifecycle(LockState())
    var showSetup by rememberSaveable { mutableStateOf(false) }
    val available = actions.biometricsAvailable

    KeelScreen(title = "App lock", subtitle = "Keep your data private on this device", onBack = onBack) {
        LockGroup {
            KeelToggleRow(
                title = "App lock",
                detail = if (available) "Unlock with biometrics or a PIN" else "Unlock with a 4-digit PIN",
                checked = state.isEnabled,
                onCheckedChange = { on ->
                    haptic(KeelHapticKind.SELECTION)
                    if (on) showSetup = true else scope.launch { actions.disable() }
                },
                detailStyle = KeelFont.caption,
                contentPadding = PaddingValues(14.dp),
                leading = { RowIcon(if (available) KeelIcons.fingerprint else KeelIcons.lock) },
            )
        }
        if (state.isEnabled) {
            LockGroup {
                if (available) {
                    KeelToggleRow(
                        title = "Use biometric unlock",
                        detail = "Unlock with biometrics instead of typing your PIN",
                        checked = state.biometricUnlockEnabled,
                        onCheckedChange = { scope.launch { actions.setBiometricUnlock(it) } },
                        detailStyle = KeelFont.caption,
                        contentPadding = PaddingValues(14.dp),
                        leading = { RowIcon(KeelIcons.fingerprint) },
                    )
                    Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
                }
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { showSetup = true }.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RowIcon(KeelIcons.dialpad)
                    Text("Change PIN", Modifier.weight(1f), style = KeelFont.body, color = theme.text)
                    Icon(KeelIcons.chevronRight, contentDescription = null, tint = theme.muted, modifier = Modifier.size(20.dp))
                }
            }
        }
        Text(
            "Keel asks to unlock when you open it, and after you've been away for a while. Your PIN is the backup when biometrics aren't used. If you forget your PIN, you can reset it with your phone's screen lock.",
            Modifier.padding(horizontal = 4.dp), style = KeelFont.caption, color = theme.muted,
        )
    }

    if (showSetup) PINSetup(actions, onComplete = { showSetup = false }, onCancel = { showSetup = false })
}

@Composable
private fun RowIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) =
    Icon(icon, contentDescription = null, tint = LocalKeelTheme.current.accent, modifier = Modifier.size(20.dp))

@Composable
private fun LockGroup(content: @Composable () -> Unit) {
    val theme = LocalKeelTheme.current
    val shape = RoundedCornerShape(Radius.card)
    Column(Modifier.fillMaxWidth().clip(shape).background(theme.card).border(1.dp, theme.border, shape)) { content() }
}

/** A full-screen page over everything (the PIN setup, the lock): its own window, so nothing shows above it. */
@Composable
private fun FullScreen(onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val theme = LocalKeelTheme.current
    Dialog(
        onDismissRequest = { onBack?.invoke() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnBackPress = onBack != null, dismissOnClickOutside = false,
        ),
    ) {
        // This window draws behind the system bars: give them icons that show on the page.
        val view = LocalView.current
        val lightPage = theme.background.luminance() > 0.5f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightPage
                    isAppearanceLightNavigationBars = lightPage
                }
            }
        }
        Box(
            Modifier.fillMaxSize().background(theme.background).windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxSize().padding(horizontal = Spacing.screenH, vertical = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                content = content,
            )
        }
    }
}

/**
 * Port of iOS `PINSetupView`: create a PIN, then confirm it. On a match it is stored (hashed)
 * and the lock turns on.
 */
@Composable
fun PINSetup(actions: AppLockActions, onComplete: () -> Unit, onCancel: () -> Unit) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    var first by remember { mutableStateOf("") }
    var digits by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val length = AppLockRules.PIN_LENGTH

    fun reset(message: String) { error = message; first = ""; digits = ""; confirming = false }

    LaunchedEffect(digits) {
        if (digits.length < length) return@LaunchedEffect
        delay(120) // let the last dot fill
        when {
            !confirming -> { first = digits; digits = ""; confirming = true }
            digits == first -> if (actions.setPIN(digits)) {
                haptic(KeelHapticKind.SUCCESS)
                onComplete()
            } else {
                reset("Please choose a $length-digit PIN.")
            }
            else -> reset("Those didn't match. Let's try again.")
        }
    }

    FullScreen(onBack = onCancel) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                "Cancel",
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onCancel).padding(vertical = 12.dp, horizontal = 4.dp),
                style = KeelFont.body, color = theme.accent,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (confirming) "Confirm your PIN" else "Create a PIN",
            Modifier.semantics { heading() }, style = KeelFont.serif(24.sp), color = theme.heading,
        )
        Text(
            "Keel will ask for this to unlock, when biometrics aren't used.",
            style = KeelFont.body.copy(textAlign = TextAlign.Center), color = theme.muted,
        )
        PINDots(filled = digits.length, total = length, modifier = Modifier.padding(vertical = Spacing.md))
        ErrorLine(error)
        Spacer(Modifier.weight(1f))
        PINPad(
            onDigit = { if (digits.length < length) { error = null; digits += it } },
            onDelete = { digits = digits.dropLast(1) },
        )
    }
}

/**
 * Port of iOS `LockGate`: covers everything while Keel is locked. With biometric unlock on it
 * leads with that (and asks straight away), the PIN behind "Enter PIN instead"; otherwise the
 * PIN pad directly. Back does nothing here: Keel stays covered until she unlocks.
 */
@Composable
fun LockGate(actions: AppLockActions, state: LockState) {
    val theme = LocalKeelTheme.current
    val scope = rememberCoroutineScope()
    val haptic = KeelHaptics.rememberPerformer()
    var digits by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showPad by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showNoRecovery by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(actions.now()) }
    val length = AppLockRules.PIN_LENGTH
    val lockedOut = state.lockedOutUntil?.let { now.isBefore(it) } ?: false
    val biometrics = actions.useBiometrics(state)
    val padVisible = showPad || !biometrics

    // A one-second tick for the lockout countdown.
    LaunchedEffect(lockedOut) { while (lockedOut) { delay(1_000); now = actions.now() } }
    // Ask for biometrics once, as she arrives. "Enter PIN instead" on Android's prompt opens the pad here.
    LaunchedEffect(Unit) {
        if (biometrics && actions.unlockWithBiometrics() == BiometricOutcome.USE_PIN) showPad = true
    }
    LaunchedEffect(digits) {
        if (digits.length < length) return@LaunchedEffect
        delay(120) // let the last dot fill
        if (actions.verifyPIN(digits)) {
            haptic(KeelHapticKind.SUCCESS) // the gate goes as she unlocks
        } else {
            digits = ""
            now = actions.now()
            error = if (actions.isLockedOut()) "Too many attempts." else "Incorrect PIN. Try again."
            haptic(KeelHapticKind.ERROR)
        }
    }

    val subtitle = when {
        lockedOut -> "Too many attempts. Try again in ${AppLockRules.secondsLeft(state.lockedOutUntil, now)}s."
        biometrics && showPad -> "Use biometrics, or enter your PIN."
        biometrics -> "Unlock with biometrics."
        else -> "Enter your PIN to unlock."
    }

    FullScreen(onBack = null) {
        BackHandler {} // Back never reveals what's behind the lock
        Spacer(Modifier.weight(1f))
        Icon(if (biometrics) KeelIcons.fingerprint else KeelIcons.lock, contentDescription = null, tint = theme.accent, modifier = Modifier.size(46.dp))
        Text("Keel is locked", Modifier.semantics { heading() }, style = KeelFont.serif(24.sp), color = theme.heading)
        Text(subtitle, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = KeelFont.body.copy(textAlign = TextAlign.Center), color = theme.muted)
        if (padVisible) {
            PINDots(filled = digits.length, total = length, modifier = Modifier.padding(vertical = Spacing.sm).alpha(if (lockedOut) 0.4f else 1f))
            ErrorLine(error)
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (biometrics) {
                LinkButton("Use biometrics", enabled = !lockedOut, icon = KeelIcons.fingerprint) {
                    scope.launch { if (actions.unlockWithBiometrics() == BiometricOutcome.USE_PIN) showPad = true }
                }
            }
            if (padVisible) {
                PINPad(
                    enabled = !lockedOut,
                    onDigit = { if (!lockedOut && digits.length < length) { error = null; digits += it } },
                    onDelete = { digits = digits.dropLast(1) },
                )
            } else {
                LinkButton("Enter PIN instead") { showPad = true }
            }
            LinkButton("Forgot PIN?", small = true) {
                scope.launch {
                    if (!actions.canRecoverWithScreenLock) showNoRecovery = true
                    else if (actions.authenticateOwner()) showReset = true
                }
            }
        }
    }

    // She proved it's her phone: set a new PIN, or cancel to turn the lock off (her data is untouched).
    if (showReset) {
        PINSetup(actions, onComplete = { showReset = false }, onCancel = { showReset = false; scope.launch { actions.disable() } })
    }
    if (showNoRecovery) {
        AlertDialog(
            onDismissRequest = { showNoRecovery = false },
            containerColor = theme.card,
            title = { Text("Reset your PIN", style = KeelFont.serif(20.sp), color = theme.heading) },
            text = {
                Text(
                    "Set a screen lock for your phone in Settings to reset your Keel PIN. Without one, the only way to reset is to reinstall Keel, which erases the data on this device unless you have a backup.",
                    style = KeelFont.body, color = theme.text,
                )
            },
            confirmButton = {
                TextButton(onClick = { showNoRecovery = false }) { Text("OK", style = KeelFont.sans(15.sp, FontWeight.Medium), color = theme.accent) }
            },
        )
    }
}

@Composable
private fun ErrorLine(error: String?) {
    // A line kept even when empty, so the pad doesn't jump (iOS shows " ").
    Text(
        error ?: " ",
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = KeelFont.caption, color = LocalKeelTheme.current.attention,
    )
}

@Composable
private fun LinkButton(
    text: String,
    enabled: Boolean = true,
    small: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    val theme = LocalKeelTheme.current
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp).alpha(if (enabled) 1f else 0.4f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = theme.accent, modifier = Modifier.size(20.dp))
        Text(text, style = if (small) KeelFont.caption else KeelFont.body, color = if (small) theme.muted else theme.accent)
    }
}

/**
 * What shows in place of Keel while the lock is on and Keel isn't in front (iOS covers it while
 * inactive): the page colour and the lock's symbol, so the app switcher never shows her records.
 */
@Composable
fun PrivacyCover(biometrics: Boolean) {
    val theme = LocalKeelTheme.current
    Box(Modifier.fillMaxSize().background(theme.background), contentAlignment = Alignment.Center) {
        Icon(
            if (biometrics) KeelIcons.fingerprint else KeelIcons.lock, contentDescription = null,
            tint = theme.accent.copy(alpha = 0.5f), modifier = Modifier.size(44.dp),
        )
    }
}

/** Port of iOS `PINDots`: one circle per digit, filled as she types. */
@Composable
fun PINDots(filled: Int, total: Int, modifier: Modifier = Modifier) {
    val theme = LocalKeelTheme.current
    Row(
        modifier.clearAndSetSemantics { contentDescription = "$filled of $total digits entered" },
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        repeat(total) { i ->
            val on = i < filled
            val fill by animateColorAsState(if (on) theme.accent else Color.Transparent, label = "pinDot")
            Box(Modifier.size(16.dp).clip(CircleShape).background(fill).border(1.5.dp, if (on) theme.accent else theme.border, CircleShape))
        }
    }
}

/** Port of iOS `PINPad`: 1 to 9, then a blank, 0 and delete. Big keys. */
@Composable
fun PINPad(enabled: Boolean = true, onDigit: (String) -> Unit, onDelete: () -> Unit) {
    val theme = LocalKeelTheme.current
    val haptic = KeelHaptics.rememberPerformer()
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", DELETE_KEY))
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { key ->
                    val keyModifier = Modifier.weight(1f).height(72.dp)
                    when (key) {
                        "" -> Spacer(keyModifier)
                        DELETE_KEY -> Box(
                            keyModifier.clip(CircleShape)
                                .clickable(enabled = enabled, role = Role.Button) { haptic(KeelHapticKind.SELECTION); onDelete() }
                                .semantics { contentDescription = "Delete" },
                            contentAlignment = Alignment.Center,
                        ) { Icon(KeelIcons.backspace, contentDescription = null, tint = theme.muted, modifier = Modifier.size(24.dp)) }
                        else -> Box(
                            keyModifier,
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(72.dp).clip(CircleShape).background(theme.card).border(1.dp, theme.border, CircleShape)
                                    .clickable(
                                        enabled = enabled, role = Role.Button,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = androidx.compose.material3.ripple(),
                                    ) { haptic(KeelHapticKind.SELECTION); onDigit(key) },
                                contentAlignment = Alignment.Center,
                            ) { Text(key, style = KeelFont.serif(28.sp, FontWeight.Normal), color = theme.text) }
                        }
                    }
                }
            }
        }
    }
}

private const val DELETE_KEY = "⌫" // not-copy: key id, never shown
