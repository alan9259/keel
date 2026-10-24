package com.therecalibrationyears.keel

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Once a day, lay the reminders out again (plan D11). Medication reminders are armed a few weeks
 * ahead and extended each time Keel opens; on iOS they run out if she doesn't open the app for
 * longer than that. Android keeps them going: this re-arms them from her records without logging
 * anything (auto-logged doses wait until she opens Keel, D22).
 */
class ReminderTopUpWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as KeelApplication).container.controller.rearmReminders()
        return Result.success()
    }

    companion object {
        /** One daily job, kept across launches (enqueueing again changes nothing). */
        const val NAME = "keel.reminderTopUp" // not-copy: work name

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<ReminderTopUpWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
