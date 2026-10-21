package com.therecalibrationyears.keel.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port of KeelTests/RootRoutingTests.swift: the onboarding-vs-app routing decision, the
 * normal paths and the regression where finishing onboarding under the force-onboarding
 * screenshot flag failed to enter the app.
 */
class RootRoutingTests {
    private fun show(completed: Boolean, onboarded: Boolean, forcedOnboarded: Boolean = false, force: Boolean = false) =
        RootRouting.shouldShowMain(completed, onboarded, forcedOnboarded, force)

    @Test fun freshUserSeesOnboarding() = assertFalse(show(completed = false, onboarded = false))

    @Test fun onboardedUserSeesMainApp() = assertTrue(show(completed = false, onboarded = true))

    @Test fun forcedOnboardedSkipsToMainApp() =
        assertTrue(show(completed = false, onboarded = false, forcedOnboarded = true))

    @Test fun forceOnboardingPinsOnboardingForAnOnboardedUser() =
        assertFalse(show(completed = false, onboarded = true, force = true))

    /** Regression: "Start with Keel" must reach the app even when force-onboarding is set. */
    @Test fun completingOnboardingUnderForceFlagEntersApp() =
        assertTrue(show(completed = true, onboarded = true, force = true))

    /** And completing it in a normal fresh session enters the app (finishing records it). */
    @Test fun completingOnboardingFreshEntersApp() = assertTrue(show(completed = true, onboarded = true))

    /** Regression: onboarding, then "Delete all my data" in the same launch, returns to onboarding. */
    @Test fun deleteAllMyDataAfterOnboardingThisLaunchReturnsToOnboarding() =
        assertFalse(show(completed = true, onboarded = false))
}
