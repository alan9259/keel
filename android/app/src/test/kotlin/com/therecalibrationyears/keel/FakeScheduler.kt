package com.therecalibrationyears.keel

import com.therecalibrationyears.keel.platform.ReminderPayload
import com.therecalibrationyears.keel.platform.ReminderScheduling
import java.time.ZonedDateTime

/** Records what would be armed, in place of the alarm service. */
class FakeScheduler : ReminderScheduling {
    val armed = linkedMapOf<String, Pair<ReminderPayload, ZonedDateTime>>()
    override suspend fun scheduledIds(): Set<String> = armed.keys.toSet()
    override suspend fun schedule(payload: ReminderPayload, at: ZonedDateTime) { armed[payload.id] = payload to at }
    override suspend fun cancel(ids: Collection<String>) { ids.forEach { armed.remove(it) } }
    override suspend fun cancelAll() = armed.clear()
    fun ids(prefix: String) = armed.keys.filter { it.startsWith(prefix) }
}
