package com.therecalibrationyears.keel

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.therecalibrationyears.keel.data.MedicationRepository
import com.therecalibrationyears.keel.data.SettingsStore
import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.domain.DoseSchedule
import com.therecalibrationyears.keel.domain.DoseSlot
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.domain.ReminderConfig
import com.therecalibrationyears.keel.domain.ReminderIds
import com.therecalibrationyears.keel.domain.ReminderKinds
import com.therecalibrationyears.keel.domain.TreatmentDraft
import com.therecalibrationyears.keel.domain.TreatmentKind
import com.therecalibrationyears.keel.platform.ReminderPayload
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Laying out and answering reminders (iOS `AppEnvironment` reminder methods), with a fake
 * alarm service. A fixed Sydney clock at 12:00 on Wednesday 21 Oct 2026.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class KeelRemindersTests {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: MainDatabase
    private lateinit var settings: SettingsStore
    private lateinit var meds: MedicationRepository
    private lateinit var reminders: KeelReminders
    private val scheduler = FakeScheduler()
    private var allowed = true
    private val sydney = ZoneId.of("Australia/Sydney")
    private val clock = object : KeelClock {
        override fun now(): Instant = Instant.parse("2026-10-21T01:00:00Z")
        override fun zone(): ZoneId = sydney
    }
    private val today = LocalDate.of(2026, 10, 21)

    @Before fun setUp() {
        db = MainDatabase.inMemory(RuntimeEnvironment.getApplication())
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { File(tmp.root, "k.preferences_pb") })
        meds = MedicationRepository(db.medications(), { "o" }, clock)
        reminders = KeelReminders(settings, meds, scheduler, clock) { allowed }
    }

    @After fun tearDown() = db.close()

    private suspend fun med(name: String = "Prometrium", hour: Int? = 20, autoLog: Boolean = false) = meds.add(
        TreatmentDraft(name = name, kind = TreatmentKind.TREATMENT, autoLogDoses = autoLog, schedule = DoseSchedule(slots = listOf(DoseSlot(hour = hour)))),
    )

    @Test fun medicationRemindersFollowTheSchedule() = runBlocking {
        val m = med()
        reminders.refreshMedicationReminders()
        val armed = scheduler.ids(ReminderIds.medicationPrefix(m.id))
        assertEquals(24, armed.size)
        val first = scheduler.armed.values.minBy { it.second }
        assertEquals(today.atTime(20, 0).atZone(sydney), first.second)
        assertEquals("Time for your medication", first.first.title) // names stay off the lock screen by default
        assertEquals("medication", first.first.kind)
    }

    @Test fun namesShowOnlyWhenSheChoosesIt() = runBlocking {
        med()
        settings.setMedicineNamesInReminders(true)
        reminders.refreshMedicationReminders()
        assertTrue(scheduler.armed.values.all { it.first.title == "Time for Prometrium" })
    }

    @Test fun anAutoLoggedMedicineSaysSo() = runBlocking {
        med(autoLog = true)
        reminders.refreshMedicationReminders()
        assertTrue(scheduler.armed.values.all { it.first.autoLog && it.first.body.startsWith("A gentle reminder. Keel will record") })
    }

    @Test fun aLoggedDoseIsNotRemindedToday() = runBlocking {
        val m = med()
        meds.setTaken(m, today, null, taken = true)
        reminders.refreshMedicationReminders()
        assertFalse(scheduler.armed.keys.any { it.endsWith(".d20261021") })
        assertEquals(24, scheduler.armed.size)
    }

    /** Plan D2: Android honours the "Medication reminders" toggle (iOS ignores it). */
    @Test fun offWithTheMasterSwitchOrTheMedicationToggle() = runBlocking {
        med()
        reminders.refreshMedicationReminders()
        settings.setReminderEnabled(ReminderKinds.MEDICATION, false)
        reminders.refreshMedicationReminders()
        assertTrue(scheduler.armed.isEmpty())
        settings.setReminderEnabled(ReminderKinds.MEDICATION, true)
        reminders.refreshMedicationReminders()
        assertEquals(24, scheduler.armed.size)
        reminders.setPushNotificationsEnabled(false)
        assertTrue(scheduler.armed.isEmpty())
    }

    @Test fun aMedicineNoLongerTakenLosesItsReminders() = runBlocking {
        val m = med()
        val other = med(name = "Vitamin D", hour = 8)
        reminders.refreshMedicationReminders()
        meds.archive(m.id)
        reminders.refreshMedicationReminders()
        assertTrue(scheduler.ids(ReminderIds.medicationPrefix(m.id)).isEmpty())
        assertTrue(scheduler.ids(ReminderIds.medicationPrefix(other.id)).isNotEmpty())
    }

    @Test fun noTimeMeansNoReminder() = runBlocking {
        med(hour = null)
        reminders.refreshMedicationReminders()
        assertTrue(scheduler.armed.isEmpty())
    }

    @Test fun everydayRemindersOnlyOnceAndroidWillShowThem() = runBlocking {
        allowed = false
        reminders.refreshLifestyleReminders()
        assertTrue(scheduler.armed.isEmpty()) // never leads to a prompt at launch
        allowed = true
        reminders.refreshLifestyleReminders()
        assertEquals(listOf(ReminderIds.CHECK_IN), scheduler.armed.keys.toList()) // on by default, as iOS
        assertEquals(today.plusDays(1).atTime(9, 0).atZone(sydney), scheduler.armed.values.single().second) // 9:00 has passed
    }

    @Test fun turningOneOffClearsEveryTimeItHad() = runBlocking {
        settings.setReminderEnabled(ReminderKinds.HYDRATION, true)
        reminders.refreshLifestyleReminders()
        assertEquals(7, scheduler.ids(ReminderIds.HYDRATION_PREFIX).size)
        settings.setReminderConfig(ReminderConfig(hydrationIntervalHours = 3))
        reminders.rescheduleLifestyleReminder(ReminderKinds.HYDRATION)
        assertEquals(listOf(8, 11, 14, 17, 20).map(ReminderIds::hydration), scheduler.ids(ReminderIds.HYDRATION_PREFIX))
        settings.setReminderEnabled(ReminderKinds.HYDRATION, false)
        reminders.refreshLifestyleReminders()
        assertTrue(scheduler.ids(ReminderIds.HYDRATION_PREFIX).isEmpty())
    }

    @Test fun aFiredEverydayReminderArmsItsNextTime() = runBlocking {
        reminders.refreshLifestyleReminders()
        val (payload, at) = scheduler.armed.values.single()
        scheduler.armed.clear()
        assertTrue(reminders.onReminderFired(payload) {})
        assertEquals(at, scheduler.armed[payload.id]!!.second) // the next 9:00 after now
    }

    @Test fun aFiredReminderIsNotShownOnceSwitchedOffOrRemoved() = runBlocking {
        val m = med()
        reminders.refreshMedicationReminders()
        val payload = scheduler.armed.values.first().first
        var fired = ""
        assertTrue(reminders.onReminderFired(payload) { fired = it })
        assertEquals(payload.id, fired)
        meds.archive(m.id)
        assertFalse(reminders.onReminderFired(payload) {})
        val checkIn = ReminderPayload(ReminderIds.CHECK_IN, ReminderKinds.DAILY_CHECK_IN, "t", "b", hour = 9, minute = 0)
        settings.setReminderEnabled(ReminderKinds.DAILY_CHECK_IN, false)
        assertFalse(reminders.onReminderFired(checkIn) {})
        settings.setPushNotifications(false)
        assertFalse(reminders.onReminderFired(payload) {})
    }

    @Test fun aDoseIsLoggedOnTheDayItWasFor() {
        val payload = ReminderPayload("x", "medication", "t", "b", medicationId = "M", slotId = "S", dayKey = "20261019")
        assertEquals(LocalDate.of(2026, 10, 19), reminders.doseDay(payload)) // D10: not the day she tapped
        assertEquals(today, reminders.doseDay(payload.copy(dayKey = null)))
    }

    // Port of KeelTests/ReminderExplainerTests.swift (the record step; no system prompt in tests).

    @Test fun continueLeavesRemindersOn() = runBlocking {
        settings.setNotificationExplainerPending(true)
        reminders.recordNotificationExplainerAnswer(allow = true)
        assertFalse(settings.notificationExplainerPending.first())
        assertTrue(settings.pushNotifications.first())
    }

    @Test fun notNowTurnsRemindersOffWithoutAsking() = runBlocking {
        settings.setNotificationExplainerPending(true)
        reminders.refreshLifestyleReminders()
        reminders.recordNotificationExplainerAnswer(allow = false)
        assertFalse(settings.notificationExplainerPending.first())
        assertFalse(settings.pushNotifications.first())
        assertTrue(scheduler.armed.isEmpty())
    }
}
