package com.therecalibrationyears.keel.platform

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.therecalibrationyears.keel.domain.RepeatingReminder
import java.io.File
import java.nio.file.Files
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class TestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}

/** Reminders on the alarm service, and the registry that lets a day or a dose be cancelled. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderSchedulerTests {
    private val context = RuntimeEnvironment.getApplication()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private lateinit var scheduler: AlarmReminderScheduler
    private val t = ZonedDateTime.of(2026, 8, 12, 20, 0, 0, 0, ZoneOffset.UTC)

    @Before fun setUp() {
        val dir: File = Files.createTempDirectory("keel-reminders").toFile()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { File(dir, "r.preferences_pb") }
        scheduler = AlarmReminderScheduler(context, store, TestReceiver::class.java)
    }

    private fun med(slot: String, day: String) = ReminderPayload(
        id = "keel.med.MED.${slot.take(8)}.d$day", kind = "medication", title = "Time for your medication",
        body = "A gentle reminder. Tap to mark it taken.", medicationId = "MED", slotId = slot, dayKey = day,
    )

    @Test fun schedulingArmsAnAlarmAtItsTimeAndRecordsIt() = runBlocking {
        scheduler.schedule(med("SLOTAAAA-1", "20260812"), t)
        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals(t.toInstant().toEpochMilli(), alarms.scheduledAlarms.single().triggerAtMs)
        assertEquals(setOf("keel.med.MED.SLOTAAAA.d20260812"), scheduler.scheduledIds())
    }

    @Test fun theSameIdIsReplacedNotDoubled() = runBlocking {
        scheduler.schedule(med("SLOTAAAA-1", "20260812"), t)
        scheduler.schedule(med("SLOTAAAA-1", "20260812"), t.plusMinutes(30))
        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals(t.plusMinutes(30).toInstant().toEpochMilli(), alarms.scheduledAlarms.single().triggerAtMs)
    }

    @Test fun aLoggedDoseCancelsOnlyThatDoseThatDay() = runBlocking {
        scheduler.schedule(med("SLOTAAAA-1", "20260812"), t)
        scheduler.schedule(med("SLOTBBBB-1", "20260812"), t.plusHours(1))
        scheduler.schedule(med("SLOTAAAA-1", "20260813"), t.plusDays(1))
        scheduler.cancelMedication("MED", dayKey = "20260812", slotId = "SLOTAAAA-1")
        assertEquals(setOf("keel.med.MED.SLOTBBBB.d20260812", "keel.med.MED.SLOTAAAA.d20260813"), scheduler.scheduledIds())
        assertEquals(2, alarms.scheduledAlarms.size)
        // The whole day (a Home tick), then everything for the medicine.
        scheduler.cancelMedication("MED", dayKey = "20260812")
        assertEquals(setOf("keel.med.MED.SLOTAAAA.d20260813"), scheduler.scheduledIds())
        scheduler.cancelMedication("MED")
        assertTrue(scheduler.scheduledIds().isEmpty())
        assertEquals(0, alarms.scheduledAlarms.size)
    }

    @Test fun cancelAllLeavesNothingArmed() = runBlocking {
        scheduler.schedule(med("SLOTAAAA-1", "20260812"), t)
        scheduler.scheduleRepeating(RepeatingReminder("keel.winddown", "winddown", "Time to wind down", "b", 21, 30), t)
        scheduler.cancelAll()
        assertEquals(0, alarms.scheduledAlarms.size)
        assertTrue(scheduler.scheduledIds().isEmpty())
    }

    @Test fun aFiredOneOffLeavesTheRegistry() = runBlocking {
        scheduler.schedule(med("SLOTAAAA-1", "20260812"), t)
        scheduler.fired("keel.med.MED.SLOTAAAA.d20260812")
        assertTrue(scheduler.scheduledIds().isEmpty())
    }

    @Test fun payloadSurvivesTheIntent() {
        val p = med("SLOTAAAA-1", "20260812").copy(autoLog = true)
        assertEquals(p, ReminderPayload.from(p.toIntent(Intent())))
        assertNull(p.repeating())
        val r = RepeatingReminder("keel.movement.2", "movement", "A little movement?", "b", 14, 0, weekday = 2)
        assertEquals(r, ReminderPayload.from(ReminderPayload.of(r).toIntent(Intent()))!!.repeating())
        assertNull(ReminderPayload.from(Intent()))
    }
}
