package com.therecalibrationyears.keel.domain

/** The onboarding-vs-app routing decision (port of iOS `RootView.shouldShowMain`). */
object RootRouting {
    /**
     * @param completedThisLaunch she just finished onboarding in this session; enters the app
     *   even under the force flag, while still onboarded.
     * @param hasOnboarded recorded on her profile in the database; uninstalling clears it.
     * @param forcedOnboarded debug `uitOnboarded`: skip straight to the app for screenshots.
     * @param forceOnboarding debug `uitForceOnboarding`: pin onboarding on (until she finishes it).
     */
    fun shouldShowMain(
        completedThisLaunch: Boolean,
        hasOnboarded: Boolean,
        forcedOnboarded: Boolean,
        forceOnboarding: Boolean,
    ): Boolean {
        // Finishing onboarding records it before setting completedThisLaunch, so both are
        // true then. If the record is gone again ("Delete all my data" in the same launch),
        // she goes back to onboarding.
        if (completedThisLaunch && hasOnboarded) return true
        if (forceOnboarding) return false
        return hasOnboarded || forcedOnboarded
    }
}
