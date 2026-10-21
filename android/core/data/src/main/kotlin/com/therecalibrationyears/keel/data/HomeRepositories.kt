package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.ActivityLogDao
import com.therecalibrationyears.keel.data.db.CheckInDao
import com.therecalibrationyears.keel.data.db.CheckInEntity
import com.therecalibrationyears.keel.data.db.LoggedSymptom
import com.therecalibrationyears.keel.data.db.MedicationDao
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.data.db.MedicationLogEntity
import com.therecalibrationyears.keel.domain.DayAmount
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.domain.SyncStatus
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Read side of iOS `CheckInRepository` that Home uses; writes arrive with the check-in flow. */
class CheckInRepository(private val dao: CheckInDao) {
    /** Live check-ins, newest first. */
    fun observeLive(): Flow<List<CheckInEntity>> = dao.observeLive()

    fun observeLoggedSymptoms(): Flow<List<LoggedSymptom>> = dao.observeLoggedSymptoms()
}

/**
 * The Home medicines log (port of the taken/untaken parts of iOS `MedicationRepository`).
 * Days are her local days, from the injected clock's zone.
 */
class MedicationRepository(
    private val dao: MedicationDao,
    private val ownerId: suspend () -> String,
    private val clock: KeelClock,
) {
    private val writes = Mutex()

    /** Active (not stopped, not deleted) medicines, oldest first. */
    fun observeActive(): Flow<List<MedicationEntity>> = dao.observeActive()

    fun observeLogs(): Flow<List<MedicationLogEntity>> = dao.observeLiveLogs()

    /**
     * Records taken or not for one dose (or the whole day when [slot] is null). Unticking
     * removes the record (soft delete), so it's no longer on the log (iOS `setTaken`).
     */
    suspend fun setTaken(medication: MedicationEntity, day: LocalDate, slot: String?, taken: Boolean) = writes.withLock {
        val now = clock.now()
        val existing = logsOn(medication, day).firstOrNull { it.slot == slot }
        when {
            existing != null && taken ->
                dao.upsertLog(existing.copy(taken = true, updatedAt = now, syncStatusRaw = SyncStatus.PENDING_UPLOAD.raw))
            existing != null ->
                dao.upsertLog(existing.copy(deletedAt = now, updatedAt = now, syncStatusRaw = SyncStatus.PENDING_UPLOAD.raw))
            taken -> dao.upsertLog(
                MedicationLogEntity(
                    id = UUID.randomUUID().toString().uppercase(),
                    date = day.atStartOfDay(clock.zone()).toInstant(),
                    slot = slot, taken = true, medicationID = medication.id,
                    ownerID = ownerId(), createdAt = now, updatedAt = now,
                ),
            )
        }
    }

    /**
     * Removes every taken log for this medicine on the day, across all dose slots, so
     * nothing lingers when she unticks it on Home (iOS `clearTaken`).
     */
    suspend fun clearTaken(medication: MedicationEntity, day: LocalDate) = writes.withLock {
        val now = clock.now()
        logsOn(medication, day).forEach {
            dao.upsertLog(it.copy(deletedAt = now, updatedAt = now, syncStatusRaw = SyncStatus.PENDING_UPLOAD.raw))
        }
    }

    private suspend fun logsOn(medication: MedicationEntity, day: LocalDate): List<MedicationLogEntity> {
        val zone = clock.zone()
        return dao.liveLogsBetween(
            medication.id, day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant(),
        )
    }
}

/** Her own activity amounts (iOS `ActivityLog`), as days in her zone. */
class ActivityRepository(private val dao: ActivityLogDao, private val clock: KeelClock) {
    fun observeAmounts(activityId: String): Flow<List<DayAmount>> = dao.observeLive(activityId).map { logs ->
        logs.map { DayAmount(it.activityID, it.date.atZone(clock.zone()).toLocalDate(), it.amount) }
    }
}
