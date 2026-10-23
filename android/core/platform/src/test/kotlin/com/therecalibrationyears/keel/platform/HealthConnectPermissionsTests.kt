package com.therecalibrationyears.keel.platform

import android.app.Application
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Opening Health Connect's pages (plan §3.5, §3.6): from the activity on screen, or as a new task. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthConnectPermissionsTests {
    private val app: Application = RuntimeEnvironment.getApplication()

    private fun Intent.isNewTask() = flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0

    /** Regression: "Manage access in Health Connect" crashed Keel, started from the app context without a new task. */
    @Test fun manageAccessWithoutAnActivityStartsANewTask() {
        HealthConnectPermissions(app).openManageAccess()
        val started = shadowOf(app).nextStartedActivity
        assertEquals(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS, started.action)
        assertTrue(started.isNewTask())
    }

    /**
     * Regression: it then crashed on Android 14+ asking for Health Connect's per-app page, which
     * Android refuses to apps. It opens Health Connect itself, and a refusal never crashes Keel.
     */
    @Test fun manageAccessNeverUsesTheSystemOnlyPage() {
        HealthConnectPermissions(app).openManageAccess()
        val started = shadowOf(app).nextStartedActivity
        // Android 14's own Health Connect home (the library's settings action, on 34+).
        assertEquals("android.health.connect.action.HEALTH_HOME_SETTINGS", started.action)
        assertTrue(started.action != "android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
    }

    @Test fun manageAccessOpensFromTheActivityOnScreen() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val permissions = HealthConnectPermissions(app).apply { attach(controller.get()) }
        controller.start().resume()
        permissions.openManageAccess()
        val started = shadowOf(controller.get()).nextStartedActivity
        assertEquals(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS, started.action)
    }

    /** Android 9 to 13 without Health Connect: its Play Store page, at Health Connect's onboarding. */
    @Test fun installOpensHealthConnectInThePlayStore() {
        HealthConnectPermissions(app).openInstall()
        val started = shadowOf(app).nextStartedActivity
        assertEquals("com.android.vending", started.`package`)
        assertEquals(
            "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding",
            started.dataString,
        )
        assertTrue(started.isNewTask())
    }

    /** Without an activity to answer, nothing is asked (the caller treats it as not shown). */
    @Test fun noActivityMeansNoRequest() = kotlinx.coroutines.runBlocking {
        assertEquals(null, HealthConnectPermissions(app).request(setOf(HealthConnectReads.STEPS)))
    }
}
