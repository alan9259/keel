package com.therecalibrationyears.keel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.therecalibrationyears.keel.checkin.CheckInRequest
import com.therecalibrationyears.keel.checkin.CheckInScreen
import com.therecalibrationyears.keel.checkin.EntrySheetContent
import com.therecalibrationyears.keel.designsystem.components.KeelToast
import com.therecalibrationyears.keel.designsystem.components.ToastData
import com.therecalibrationyears.keel.domain.Greeting
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.designsystem.LocalGoHome
import com.therecalibrationyears.keel.designsystem.LocalKeelTheme
import com.therecalibrationyears.keel.designsystem.components.KeelScreen
import com.therecalibrationyears.keel.medications.MedicationsScreen
import com.therecalibrationyears.keel.settings.ExactTimingDialog
import com.therecalibrationyears.keel.settings.RemindersScreen
import com.therecalibrationyears.keel.settings.rememberNotificationAccess
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.therecalibrationyears.keel.designsystem.KeelFont
import com.therecalibrationyears.keel.home.HomeActions
import com.therecalibrationyears.keel.home.HomeData
import com.therecalibrationyears.keel.home.HomeDestination
import com.therecalibrationyears.keel.home.HomeScreen
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** The app's screens (iOS `MainRoute`), added as each is ported. Home is the root. */
@Serializable
sealed interface MainRoute : NavKey {
    @Serializable data object Home : MainRoute
    @Serializable data object Cycle : MainRoute
    @Serializable data object Medications : MainRoute
    @Serializable data object Patterns : MainRoute
    @Serializable data object More : MainRoute
    @Serializable data object GpSummary : MainRoute
    /** Reached from Settings on iOS; Settings is ported in M6, so for now only by debug route. */
    @Serializable data object Reminders : MainRoute

    companion object {
        /** Debug launch routes (`--es uitRoute cycle`). */
        fun fromDebugName(name: String?): MainRoute? = when (name?.lowercase()) {
            "cycle" -> Cycle
            "medications" -> Medications
            "patterns" -> Patterns
            "more" -> More
            "gp_summary" -> GpSummary
            "reminders" -> Reminders
            else -> null
        }
    }
}

/**
 * Navigation for the app once onboarded (port of iOS `MainView`): Home at the root, the
 * feature screens pushed on top, Android's back pops, and every pushed screen's Home
 * button clears back to Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(controller: KeelAppController) {
    val backStack = rememberNavBackStack(MainRoute.Home)
    val goHome: () -> Unit = { while (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    val scope = rememberCoroutineScope()
    val pack by controller.moodPack.collectAsStateWithLifecycle()

    // The entry flow (iOS MainView): the mood slide for a day (and the entry being edited, if
    // any), then the detail screen; "Change" goes back to the slide keeping both.
    var slideDay by remember { mutableStateOf<LocalDate?>(null) }
    var slideEditingId by remember { mutableStateOf<String?>(null) }
    var request by remember { mutableStateOf<CheckInRequest?>(null) }
    var lastRequest by remember { mutableStateOf<CheckInRequest?>(null) }
    var toast by remember { mutableStateOf<ToastData?>(null) }
    LaunchedEffect(toast) { if (toast != null) { delay(2_400); toast = null } }

    val flow = remember(controller) {
        object : EntryFlow {
            override fun create(day: LocalDate) { slideEditingId = null; slideDay = day }
            override fun edit(checkInId: String) {
                scope.launch { controller.editRequest(checkInId)?.let { request = it; lastRequest = it } }
            }
        }
    }

    LaunchedEffect(Unit) {
        DebugHarness.initialRoute?.let { if (backStack.size == 1) backStack.add(it) }
    }

    CompositionLocalProvider(LocalGoHome provides goHome) {
        Box(Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                onBack = back,
                entryProvider = entryProvider {
                    entry<MainRoute.Home> { HomeRoute(controller, flow) { backStack.add(it) } }
                    entry<MainRoute.Cycle> { StandInScreen("Cycle", back) }
                    entry<MainRoute.Medications> { MedicationsScreen(controller.medicationsActions, back) }
                    entry<MainRoute.Patterns> { StandInScreen("Looking back", back) }
                    entry<MainRoute.More> { StandInScreen("More", back) }
                    entry<MainRoute.GpSummary> { StandInScreen("GP Visit Summary", back) }
                    entry<MainRoute.Reminders> { RemindersScreen(controller.remindersActions, back) }
                },
            )

            AnimatedVisibility(
                visible = request != null,
                enter = slideInVertically(tween(300)) { it },
                exit = slideOutVertically(tween(250)) { it },
            ) {
                lastRequest?.let { r ->
                    val now = controller.clock.now()
                    CheckInScreen(
                        request = r,
                        today = now.atZone(controller.clock.zone()).toLocalDate(),
                        zone = controller.clock.zone(),
                        pack = pack,
                        actions = controller.checkInActions,
                        onClose = { saved ->
                            request = null
                            if (saved) toast = ToastData(if (r.editingId != null) "Entry updated." else "Entry saved.", "Every entry sharpens the picture.")
                        },
                        onChangeMood = { request = null; slideEditingId = r.editingId; slideDay = r.day },
                        onDeleted = { request = null; toast = ToastData("Entry removed.", "It's gone from your log.") },
                    )
                }
            }

            ReminderExplainer(controller)
            ExactTimingOffer(controller)

            toast?.let {
                Box(
                    Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 96.dp),
                ) { KeelToast(it) }
            }
        }
    }

    slideDay?.let { day ->
        ModalBottomSheet(
            onDismissRequest = { slideDay = null },
            containerColor = LocalKeelTheme.current.background,
        ) {
            val now = controller.clock.now()
            EntrySheetContent(Greeting.forHour(now.atZone(controller.clock.zone()).hour), pack) { mood ->
                slideDay = null
                if (mood != null) CheckInRequest(mood, day, slideEditingId).let { request = it; lastRequest = it }
            }
        }
    }
}

/** How Home starts the entry flow. */
private interface EntryFlow {
    fun create(day: LocalDate)
    fun edit(checkInId: String)
}

@Composable
private fun HomeRoute(controller: KeelAppController, flow: EntryFlow, push: (MainRoute) -> Unit) {
    val theme = LocalKeelTheme.current
    val scope = rememberCoroutineScope()
    val data by controller.homeData.collectAsStateWithLifecycle(initialValue = null)
    // "Today" and the greeting refresh whenever she comes back to the app.
    var now by remember { mutableStateOf(controller.clock.now()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { now = controller.clock.now() }

    val actions = remember(controller, flow) {
        object : HomeActions {
            override fun navigate(destination: HomeDestination) = push(
                when (destination) {
                    HomeDestination.CYCLE -> MainRoute.Cycle
                    HomeDestination.MEDICATIONS -> MainRoute.Medications
                    HomeDestination.PATTERNS -> MainRoute.Patterns
                    HomeDestination.MORE -> MainRoute.More
                    HomeDestination.GP_SUMMARY -> MainRoute.GpSummary
                },
            )

            override fun createEntry(day: LocalDate) = flow.create(day)

            override fun editEntry(checkInId: String) = flow.edit(checkInId)

            override fun toggleMedication(medication: MedicationEntity, day: LocalDate, currentlyTaken: Boolean) {
                scope.launch { controller.toggleMedicationFromHome(medication, day, currentlyTaken) }
            }
        }
    }

    val loaded: HomeData? = data
    if (loaded == null) {
        Box(Modifier.fillMaxSize().background(theme.background)) // a moment before the first read
    } else {
        HomeScreen(loaded, now, controller.clock.zone(), actions)
    }
}

// FAKE: stands in for a feature screen not ported yet — shows only its iOS title, never sample data; remove as each screen is built (M3 to M6)
@Composable
private fun StandInScreen(title: String, onBack: () -> Unit) {
    KeelScreen(title = title, onBack = onBack) {}
}

/**
 * "Gentle reminders" (iOS MainView): once onboarding is done, Home explains reminders in one
 * line before Android's own prompt. It comes before a permission prompt, so "Continue" is its
 * only button (App Review 5.1.1(iv), applied to Android); the choice is on Android's dialog.
 * Below Android 13 there's no prompt, and Continue just lays the reminders out.
 */
@Composable
private fun ReminderExplainer(controller: KeelAppController) {
    val theme = LocalKeelTheme.current
    val scope = rememberCoroutineScope()
    val pending by controller.notificationExplainerPending.collectAsStateWithLifecycle(false)
    val asked by controller.notificationPermissionAsked.collectAsStateWithLifecycle(false)
    var showing by remember { mutableStateOf(false) }
    // Let Home settle first so the explanation doesn't land mid-transition.
    LaunchedEffect(pending) {
        showing = false
        if (pending) { delay(800); showing = true }
    }
    // Whatever she answers on Android's dialog, the explanation has been given.
    val finish: () -> Unit = { scope.launch { controller.answerNotificationExplainer() } }
    val access = rememberNotificationAccess(onAsked = controller::markNotificationPermissionAsked, onBlocked = finish)
    if (showing && pending) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            containerColor = theme.card,
            title = { Text("Gentle reminders", style = KeelFont.serif(20.sp), color = theme.heading) },
            text = {
                Text(
                    "Keel can remind you to check in and take your medicines. You can change this any time in Settings.",
                    style = KeelFont.body, color = theme.text,
                )
            },
            confirmButton = {
                TextButton(onClick = { showing = false; access.whenAllowed(asked, finish) }) {
                    Text("Continue", style = KeelFont.sans(15.sp, FontWeight.Medium), color = theme.accent)
                }
            },
        )
    }
}

/**
 * "Reminders on time" (Android only, plan D25): after she saves a medicine whose reminders
 * Android would deliver late, once. It waits for the form to close, so it doesn't land on top
 * of it.
 */
@Composable
private fun ExactTimingOffer(controller: KeelAppController) {
    val scope = rememberCoroutineScope()
    val pending by controller.exactTimingOfferPending.collectAsStateWithLifecycle()
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(pending) {
        showing = false
        if (pending) { delay(500); showing = true }
    }
    if (showing && pending) {
        ExactTimingDialog(onContinue = { showing = false; scope.launch { controller.answerExactTimingOffer() } })
    }
}
