package com.therecalibrationyears.keel

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.therecalibrationyears.keel.platform.ReminderChannels
import com.therecalibrationyears.keel.platform.ReminderPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** What a reminder looks like when it's shown (iOS categories and actions). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderPosterTests {
    private val app = RuntimeEnvironment.getApplication()
    private val manager get() = app.getSystemService(NotificationManager::class.java)

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ReminderChannels.ensure(app)
    }

    private val med = ReminderPayload(
        id = "keel.med.MED.SLOTAAAA.d20261021", kind = "medication", title = "Time for your medication",
        body = "A gentle reminder. Tap to mark it taken.", medicationId = "MED", slotId = "SLOTAAAA-1", dayKey = "20261021",
    )

    @Test fun aMedicationReminderOffersMarkTakenAndAlwaysMarkTaken() {
        ReminderPoster.post(app, med)
        val n = shadowOf(manager).allNotifications.single()
        assertEquals("Time for your medication", n.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals("A gentle reminder. Tap to mark it taken.", n.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
        assertEquals(listOf("Mark taken", "Always mark taken"), n.actions.map { it.title.toString() })
        assertEquals("keel.medication", n.channelId)
        assertTrue(n.contentIntent != null)
    }

    @Test fun anAutoLoggedReminderHasNothingToTap() {
        ReminderPoster.post(app, med.copy(autoLog = true, body = "A gentle reminder. Keel will record it as taken unless you change it."))
        assertEquals(null, shadowOf(manager).allNotifications.single().actions)
    }

    @Test fun everydayRemindersUseTheirOwnChannel() {
        ReminderPoster.post(app, ReminderPayload("keel.hydration.8", "hydration", "A glass of water?", "A small sip counts.", hour = 8, minute = 0))
        val n = shadowOf(manager).allNotifications.single()
        assertEquals("keel.hydration", n.channelId)
        assertEquals(null, n.actions)
    }

    @Test fun channelsAreNamedAsTheIosRemindersScreen() {
        assertEquals(
            listOf("Daily check-in", "Medication reminders", "Hydration", "Movement", "Wind-down"),
            listOf("dailyCheckIn", "medication", "hydration", "movement", "winddown").map { manager.getNotificationChannel(ReminderChannels.id(it)).name.toString() },
        )
    }

    @Test fun nothingIsShownWithoutPermission() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ReminderPoster.post(app, med)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
