package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.MedicationDao
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.data.db.MedicationLogEntity
import com.therecalibrationyears.keel.domain.DoseSchedule
import com.therecalibrationyears.keel.domain.DoseUnit
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.domain.MedicationMethod
import com.therecalibrationyears.keel.domain.MedicationSchedules
import com.therecalibrationyears.keel.domain.ScheduleColumns
import com.therecalibrationyears.keel.domain.ScheduleWords
import com.therecalibrationyears.keel.domain.SyncStatus
import com.therecalibrationyears.keel.domain.TreatmentDraft
import com.therecalibrationyears.keel.domain.TreatmentKind
import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A dose auto-logged as taken, so its now-redundant reminder can be cancelled. */
data class LoggedDose(val medicationId: String, val slot: String?)

/**
 * Port of iOS `MedicationRepository` (Keel/Persistence/Repositories/MedicationRepository.swift).
 * Days are her local days, from the injected clock's zone. [words] gives the locale's time
 * text for the `timing` column, as iOS writes `DoseSlot.label` at save time.
 */
class MedicationRepository(
    private val dao: MedicationDao,
    private val ownerId: suspend () -> String,
    private val clock: KeelClock,
    private val words: () -> ScheduleWords? = { null },
) {
    private val writes = Mutex()
    private val sameName = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }

    /** Active (not stopped, not deleted) medicines, oldest first. */
    fun observeActive(): Flow<List<MedicationEntity>> = dao.observeActive()

    /** Treatments she has marked stopped (kept, not deleted), oldest first. */
    fun observeStopped(): Flow<List<MedicationEntity>> = dao.observeStopped()

    fun observeLogs(): Flow<List<MedicationLogEntity>> = dao.observeLiveLogs()

    suspend fun active(): List<MedicationEntity> = dao.active()
    suspend fun active(kind: TreatmentKind): List<MedicationEntity> = active().filter { it.kind == kind }
    suspend fun stoppedTreatments(): List<MedicationEntity> = dao.stopped()

    /** An active medicine by id (for the reminder handler and auto-log). */
    suspend fun active(id: String): MedicationEntity? = active().firstOrNull { it.id.equals(id, ignoreCase = true) }

    /** Any live medicine by id, stopped ones included (for the edit form). */
    suspend fun byId(id: String): MedicationEntity? = dao.byId(id)?.takeIf { it.deletedAt == null }

    /**
     * Case-insensitive match against what she's taking now. Stopped entries don't count:
     * adding something back starts a fresh row, so the old history stays where it belongs.
     */
    suspend fun activeNamed(name: String): MedicationEntity? {
        val trimmed = name.trim()
        return active().firstOrNull { sameName.compare(it.name, trimmed) == 0 }
    }

    /**
     * Adds what she's taking, or updates it if it's already on her list: she has just told
     * us its current details, so those win. Dates are only ever ones she entered.
     */
    suspend fun add(draft: TreatmentDraft): MedicationEntity {
        activeNamed(draft.name)?.let { existing -> return update(existing.id, draft) ?: existing }
        return writes.withLock {
            val now = clock.now()
            val entity = MedicationEntity(
                id = UUID.randomUUID().toString().uppercase(),
                name = draft.name.trim(),
                ownerID = ownerId(),
                createdAt = now,
                updatedAt = now,
            ).applying(draft, now)
            dao.upsert(entity)
            entity
        }
    }

    /** Edit in place. Stopping keeps the record and carries her date; reactivating clears it. */
    suspend fun update(id: String, draft: TreatmentDraft): MedicationEntity? = writes.withLock {
        val existing = dao.byId(id) ?: return@withLock null
        val now = clock.now()
        val updated = existing.applying(draft, now).copy(
            date = existing.date ?: draft.date,
            updatedAt = now,
            syncStatusRaw = PENDING,
        )
        dao.upsert(updated)
        updated
    }

    private fun MedicationEntity.applying(draft: TreatmentDraft, now: Instant): MedicationEntity {
        val zone = clock.zone()
        val cols = MedicationSchedules.write(draft.schedule, zone, now)
        val w = words()
        return copy(
            name = draft.name.trim(),
            dosage = draft.doseText,
            doseAmount = draft.doseAmount,
            doseUnitRaw = draft.doseUnit?.raw,
            timing = if (w != null) draft.timingText(w) else draft.legacyTiming,
            methodRaw = draft.effectiveMethod?.raw,
            kindRaw = draft.kind.raw,
            catalogGroupID = draft.catalogGroupID,
            date = draft.date,
            doseChangedAt = draft.doseChangedAt,
            isActive = draft.isActive,
            stoppedAt = if (draft.isActive) null else draft.stoppedAt,
            note = draft.note.trim().ifEmpty { null },
            isOffLabel = draft.isOffLabel,
            isCompounded = draft.isCompounded,
            autoLogDoses = draft.autoLogDoses,
        ).withColumns(cols)
    }

    /** Removes it from her list (soft delete; its logs stay with it). */
    suspend fun archive(id: String) = writes.withLock {
        val existing = dao.byId(id) ?: return@withLock
        val now = clock.now()
        dao.upsert(existing.copy(isActive = false, deletedAt = now, updatedAt = now, syncStatusRaw = PENDING))
    }

    /** The Medications-screen tick: whether it shows in Home's log. Not a dose record. */
    suspend fun setTracked(id: String, tracked: Boolean) = touch(id) { it.copy(isTracked = tracked) }

    /** Turn auto-logging of doses on or off for this medicine. */
    suspend fun setAutoLog(id: String, on: Boolean) = touch(id) { it.copy(autoLogDoses = on) }

    private suspend fun touch(id: String, change: (MedicationEntity) -> MedicationEntity) = writes.withLock {
        val existing = dao.byId(id) ?: return@withLock
        dao.upsert(change(existing).copy(updatedAt = clock.now(), syncStatusRaw = PENDING))
    }

    /**
     * Older entries (and restored backups) kept the picker's brand example in the name, e.g.
     * "Oral micronised progesterone (e.g. Prometrium)". Tidy them to the generic name.
     * Idempotent; only touches names that end in an "(e.g. ...)".
     */
    suspend fun stripExampleBrandsFromNames() = writes.withLock {
        val now = clock.now()
        dao.live().forEach { m ->
            val cleaned = MedicationSchedules.nameWithoutExample(m.name)
            if (cleaned != m.name) dao.upsert(m.copy(name = cleaned, updatedAt = now, syncStatusRaw = PENDING))
        }
    }

    /** Carry entries made before schedules existed onto one, once (iOS `migrateLegacySchedules`). */
    suspend fun migrateLegacySchedules() = writes.withLock {
        val stale = dao.active().filter { !it.columns.hasSchedule }
        if (stale.isEmpty()) return@withLock
        val zone = clock.zone()
        val now = clock.now()
        val w = words()
        stale.forEach { m ->
            val start = (m.date ?: m.createdAt).atZone(zone).toLocalDate()
            val schedule = MedicationSchedules.legacySchedule(m.frequencyRaw, m.timeOfDayRaw, start)
            val firstTime = schedule.sortedSlots.firstOrNull { it.hasTime }
            dao.upsert(
                m.withColumns(MedicationSchedules.write(schedule, zone, now)).copy(
                    timing = if (firstTime != null && w != null) w.time(firstTime) else m.timing,
                    updatedAt = now,
                    syncStatusRaw = PENDING,
                ),
            )
        }
    }

    /**
     * Log today's arrived doses for every medicine set to auto-log: only today, only doses
     * whose time has passed, only once, by slot id. Returns what it logged.
     */
    suspend fun autoLogTodaysDueDoses(now: Instant = clock.now()): List<LoggedDose> {
        val zone = clock.zone()
        val here = now.atZone(zone)
        val today = here.toLocalDate()
        val logged = mutableListOf<LoggedDose>()
        for (m in active().filter { it.autoLogDoses }) {
            val taken = loggedSlots(m, today).toSet()
            val slots = MedicationSchedules.autoLogSlots(m.schedule(zone), here) { it in taken }
            slots.forEach { slot ->
                setTaken(m, today, slot, taken = true)
                logged += LoggedDose(m.id, slot)
            }
        }
        return logged
    }

    /** Whether a dose (or the whole day when [slot] is null) is logged taken. */
    suspend fun isTaken(medication: MedicationEntity, day: LocalDate, slot: String?): Boolean =
        logsOn(medication, day).firstOrNull { it.slot == slot }?.taken ?: false

    /**
     * Records taken or not for one dose (or the whole day when [slot] is null). Unticking
     * removes the record (soft delete), so it's no longer on the log (iOS `setTaken`).
     */
    suspend fun setTaken(medication: MedicationEntity, day: LocalDate, slot: String?, taken: Boolean) = writes.withLock {
        val now = clock.now()
        val existing = logsOn(medication, day).firstOrNull { it.slot == slot }
        when {
            existing != null && taken ->
                dao.upsertLog(existing.copy(taken = true, updatedAt = now, syncStatusRaw = PENDING))
            existing != null ->
                dao.upsertLog(existing.copy(deletedAt = now, updatedAt = now, syncStatusRaw = PENDING))
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
            dao.upsertLog(it.copy(deletedAt = now, updatedAt = now, syncStatusRaw = PENDING))
        }
    }

    /** The doses logged today (slot ids, null for the whole day), for reminder skipping. */
    suspend fun loggedSlots(medication: MedicationEntity, day: LocalDate): List<String?> =
        logsOn(medication, day).filter { it.taken }.map { it.slot }

    private suspend fun logsOn(medication: MedicationEntity, day: LocalDate): List<MedicationLogEntity> {
        val zone = clock.zone()
        return dao.liveLogsBetween(
            medication.id, day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant(),
        )
    }

    private companion object {
        val PENDING = SyncStatus.PENDING_UPLOAD.raw
    }
}

// MARK: Entity <-> rules

/** The stored schedule parts. */
val MedicationEntity.columns: ScheduleColumns
    get() = ScheduleColumns(scheduleKindRaw, weekdaysMask, cycleLength, cyclePauseDays, cycleAnchor, doseTimesJSON, doseTime)

fun MedicationEntity.withColumns(c: ScheduleColumns): MedicationEntity = copy(
    scheduleKindRaw = c.scheduleKindRaw, weekdaysMask = c.weekdaysMask, cycleLength = c.cycleLength,
    cyclePauseDays = c.cyclePauseDays, cycleAnchor = c.cycleAnchor, doseTimesJSON = c.doseTimesJSON, doseTime = c.doseTime,
)

/** The stored parts, as the thing that knows the rules. */
fun MedicationEntity.schedule(zone: ZoneId): DoseSchedule = MedicationSchedules.read(columns, zone)

val MedicationEntity.hasSchedule: Boolean get() = scheduleKindRaw != null
val MedicationEntity.kind: TreatmentKind get() = TreatmentKind.fromRaw(kindRaw)
val MedicationEntity.method: MedicationMethod? get() = MedicationMethod.fromRaw(methodRaw)
val MedicationEntity.doseUnit: DoseUnit? get() = DoseUnit.fromRaw(doseUnitRaw)

/** "400mg · Every day at 8:00 am", skipping whatever she left blank (iOS `detailLine`). */
fun MedicationEntity.detailLine(words: ScheduleWords, zone: ZoneId): String = MedicationSchedules.detailLine(
    isCompounded = isCompounded,
    dosage = dosage,
    scheduleSummary = if (hasSchedule) schedule(zone).summary(words) else null,
    timing = timing,
)

/** Fill the form from what she already has recorded (iOS `TreatmentDraft.init(_:)`). */
fun MedicationEntity.toDraft(zone: ZoneId): TreatmentDraft = TreatmentDraft(
    name = name,
    kind = kind,
    catalogGroupID = catalogGroupID,
    method = method,
    doseAmount = doseAmount,
    doseUnit = doseUnit,
    schedule = if (hasSchedule) schedule(zone) else DoseSchedule.everyDay,
    // Free text only survives while there's nothing structured to replace it.
    legacyDosage = if (doseAmount == null) dosage else "",
    legacyTiming = if (doseTime == null) timing else "",
    date = date,
    doseChangedAt = doseChangedAt,
    isActive = isActive,
    stoppedAt = stoppedAt,
    note = note.orEmpty(),
    isOffLabel = isOffLabel,
    isCompounded = isCompounded,
    autoLogDoses = autoLogDoses,
)
