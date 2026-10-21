package com.therecalibrationyears.keel

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

    companion object {
        /** Debug launch routes (`--es uitRoute cycle`). */
        fun fromDebugName(name: String?): MainRoute? = when (name?.lowercase()) {
            "cycle" -> Cycle
            "medications" -> Medications
            "patterns" -> Patterns
            "more" -> More
            "gp_summary" -> GpSummary
            else -> null
        }
    }
}

/**
 * Navigation for the app once onboarded (port of iOS `MainView`): Home at the root, the
 * feature screens pushed on top, Android's back pops, and every pushed screen's Home
 * button clears back to Home.
 */
@Composable
fun MainShell(controller: KeelAppController) {
    val backStack = rememberNavBackStack(MainRoute.Home)
    val goHome: () -> Unit = { while (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    LaunchedEffect(Unit) {
        DebugHarness.initialRoute?.let { if (backStack.size == 1) backStack.add(it) }
    }

    CompositionLocalProvider(LocalGoHome provides goHome) {
        NavDisplay(
            backStack = backStack,
            onBack = back,
            entryProvider = entryProvider {
                entry<MainRoute.Home> { HomeRoute(controller) { backStack.add(it) } }
                entry<MainRoute.Cycle> { StandInScreen("Cycle", back) }
                entry<MainRoute.Medications> { StandInScreen("Medications and supplements", back) }
                entry<MainRoute.Patterns> { StandInScreen("Looking back", back) }
                entry<MainRoute.More> { StandInScreen("More", back) }
                entry<MainRoute.GpSummary> { StandInScreen("GP Visit Summary", back) }
            },
        )
    }
}

@Composable
private fun HomeRoute(controller: KeelAppController, push: (MainRoute) -> Unit) {
    val theme = LocalKeelTheme.current
    val scope = rememberCoroutineScope()
    val data by controller.homeData.collectAsStateWithLifecycle(initialValue = null)
    // "Today" and the greeting refresh whenever she comes back to the app.
    var now by remember { mutableStateOf(controller.clock.now()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { now = controller.clock.now() }

    val actions = remember(controller) {
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

            // FAKE: stands in for the check-in flow (iOS EntrySheet → CheckInModal) — remove when it's ported (next step of M2; tapping does nothing yet)
            override fun createEntry(day: LocalDate) = Unit

            // FAKE: stands in for editing an entry (iOS CheckInModal) — remove when the check-in flow is ported (next step of M2)
            override fun editEntry(checkInId: String) = Unit

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
