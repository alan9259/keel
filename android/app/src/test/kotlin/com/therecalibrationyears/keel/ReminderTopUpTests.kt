package com.therecalibrationyears.keel

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Android-only (plan D11): one daily top-up, however many times Keel starts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderTopUpTests {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    @Test fun theTopUpIsScheduledOnceADay() {
        repeat(3) { ReminderTopUpWorker.schedule(context) } // each launch asks again
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork(ReminderTopUpWorker.NAME).get()
        assertEquals(1, work.size)
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
        assertEquals(TimeUnit.DAYS.toMillis(1), work.single().periodicityInfo?.repeatIntervalMillis)
    }
}
