package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.ActivityLogDao
import com.therecalibrationyears.keel.data.db.ActivityLogEntity
import com.therecalibrationyears.keel.data.db.CheckInDao
import com.therecalibrationyears.keel.data.db.CheckInEntity
import com.therecalibrationyears.keel.data.db.CheckInSymptomEntity
import com.therecalibrationyears.keel.data.db.LoggedSymptom
import com.therecalibrationyears.keel.domain.DayAmount
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.domain.Mood
import com.therecalibrationyears.keel.domain.SyncStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A symptom picked on a check-in, with how strongly she felt it (1..3). */
data class SymptomPick(val symptomId: String, val severity: Int)

/** Port of iOS `CheckInRepository`. */
class CheckInRepository(
    private val dao: CheckInDao,
    private val ownerId: suspend () -> String = { "" },
    private val clock: KeelClock = KeelClock.System,
) {
    private val writes = Mutex()

    /** Live check-ins, newest first. */
    fun observeLive(): Flow<List<CheckInEntity>> = dao.observeLive()

    fun observeLoggedSymptoms(): Flow<List<LoggedSymptom>> = dao.observeLoggedSymptoms()

    suspend fun entry(id: String): CheckInEntity? = dao.byId(id)

    /** The symptoms on an entry and their severity (for editing). */
    suspend fun picks(checkInId: String): List<SymptomPick> =
        dao.liveLinks(checkInId).mapNotNull { l -> l.symptomID?.let { SymptomPick(it, l.severity) } }

    suspend fun create(mood: Mood, energy: Int, notes: String?, symptoms: List<SymptomPick>, date: Instant): CheckInEntity =
        writes.withLock {
            val owner = ownerId()
            val now = clock.now()
            val entry = CheckInEntity(
                id = newId(), date = date, moodRaw = mood.raw, energy = energy, notes = cleaned(notes),
                ownerID = owner, createdAt = now, updatedAt = now,
            )
            dao.upsert(entry)
            symptoms.forEach { dao.upsertLink(CheckInSymptomEntity(newId(), entry.id, it.symptomId, it.severity, ownerID = owner, createdAt = now, updatedAt = now)) }
            entry
        }

    /**
     * Edit in place. Symptom links are reconciled: a deselected one is tombstoned (history
     * stays), a new one added, a kept one's severity updated (iOS `update`).
     */
    suspend fun update(id: String, mood: Mood, energy: Int, notes: String?, symptoms: List<SymptomPick>) = writes.withLock {
        val entry = dao.byId(id) ?: return@withLock
        val now = clock.now()
        val wanted = symptoms.associate { it.symptomId to it.severity }.toMutableMap()
        dao.liveLinks(id).forEach { link ->
            val sid = link.symptomID ?: return@forEach
            val severity = wanted.remove(sid)
            when {
                severity == null -> dao.upsertLink(link.copy(deletedAt = now, updatedAt = now, syncStatusRaw = pending))
                severity != link.severity -> dao.upsertLink(link.copy(severity = severity, updatedAt = now, syncStatusRaw = pending))
            }
        }
        val owner = ownerId()
        wanted.forEach { (sid, severity) ->
            dao.upsertLink(CheckInSymptomEntity(newId(), id, sid, severity, ownerID = owner, createdAt = now, updatedAt = now))
        }
        dao.upsert(entry.copy(moodRaw = mood.raw, energy = energy, notes = cleaned(notes), updatedAt = now, syncStatusRaw = pending))
    }

    /** Alcohol count, or null for not recorded; zero is a real answer; never negative. */
    suspend fun setAlcohol(id: String, count: Int?) = writes.withLock {
        val entry = dao.byId(id) ?: return@withLock
        val value = count?.let { maxOf(0, it) }
        if (entry.alcoholCount == value) return@withLock
        dao.upsert(entry.copy(alcoholCount = value, updatedAt = clock.now(), syncStatusRaw = pending))
    }

    /** Soft-delete the entry and tombstone its links: gone from every view, still syncable. */
    suspend fun delete(id: String) = writes.withLock {
        val entry = dao.byId(id) ?: return@withLock
        val now = clock.now()
        dao.liveLinks(id).forEach { dao.upsertLink(it.copy(deletedAt = now, updatedAt = now, syncStatusRaw = pending)) }
        dao.upsert(entry.copy(deletedAt = now, updatedAt = now, syncStatusRaw = pending))
    }

    private val pending = SyncStatus.PENDING_UPLOAD.raw
    private fun newId() = UUID.randomUUID().toString().uppercase()

    /** Trimmed of spaces and new lines; blank is no note (iOS `nilIfEmpty`). */
    private fun cleaned(notes: String?): String? = notes?.trim()?.ifEmpty { null }
}

/** Her own activity amounts (iOS `ActivityLog`), as days in her zone. */
class ActivityRepository(
    private val dao: ActivityLogDao,
    private val clock: KeelClock,
    private val ownerId: suspend () -> String = { "" },
) {
    private val writes = Mutex()

    fun observeAmounts(activityId: String): Flow<List<DayAmount>> = dao.observeLive(activityId).map { logs ->
        logs.map { DayAmount(it.activityID, it.date.atZone(clock.zone()).toLocalDate(), it.amount) }
    }

    /** Her own sleep entry for a night (the check-in's editable value), if she made one. */
    suspend fun manualSleep(day: LocalDate): Double? =
        dao.liveOn(SLEEP, day.atStartOfDay(clock.zone()).toInstant())?.takeIf { it.sourceRaw == MANUAL }?.amount

    /**
     * Save her sleep for a night from the check-in: set or update her own entry, or remove
     * it when she cleared it (iOS `saveSleepIfNeeded`). An imported value is never touched.
     */
    suspend fun saveManualSleep(day: LocalDate, hours: Double?) = writes.withLock {
        val start = day.atStartOfDay(clock.zone()).toInstant()
        val existing = dao.liveOn(SLEEP, start)
        val now = clock.now()
        when {
            hours != null && existing != null ->
                dao.upsert(existing.copy(amount = hours, sourceRaw = MANUAL, updatedAt = now, syncStatusRaw = SyncStatus.PENDING_UPLOAD.raw))
            hours != null -> dao.upsert(
                ActivityLogEntity(UUID.randomUUID().toString().uppercase(), start, SLEEP, hours, MANUAL, ownerId(), now, now),
            )
            existing != null && existing.sourceRaw == MANUAL ->
                dao.upsert(existing.copy(deletedAt = now, updatedAt = now, syncStatusRaw = SyncStatus.PENDING_UPLOAD.raw))
        }
    }

    private companion object {
        const val SLEEP = "sleep"
        const val MANUAL = "manual"
    }
}
