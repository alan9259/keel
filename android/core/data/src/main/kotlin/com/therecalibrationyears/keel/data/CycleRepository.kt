package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.CycleDao
import com.therecalibrationyears.keel.data.db.CycleEntryEntity
import com.therecalibrationyears.keel.domain.CycleDay
import com.therecalibrationyears.keel.domain.CycleEntryType
import com.therecalibrationyears.keel.domain.CyclePhase
import com.therecalibrationyears.keel.domain.CycleStats
import com.therecalibrationyears.keel.domain.FlowLevel
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.domain.SyncStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Port of iOS `CycleRepository`: her period days, one entry per day, and the cycle
 * arithmetic over them. Only her own entries: imported period days never feed an estimate.
 */
class CycleRepository(
    private val dao: CycleDao,
    private val ownerId: suspend () -> String = { "" },
    private val clock: KeelClock = KeelClock.System,
) {
    private val writes = Mutex()

    private fun start(day: LocalDate): Instant = day.atStartOfDay(clock.zone()).toInstant()
    private fun dayOf(entry: CycleEntryEntity): LocalDate = entry.date.atZone(clock.zone()).toLocalDate()

    /** Every live entry as (her day, type and level), oldest first, kept up to date. */
    fun observeDays(): Flow<List<Pair<LocalDate, CycleDay>>> = dao.observeLive().map { list -> list.map { dayOf(it) to it.day } }

    /** Entries from [from] to [to], both days included. */
    suspend fun entries(from: LocalDate, to: LocalDate): List<CycleEntryEntity> = dao.liveBetween(start(from), start(to.plusDays(1)))

    private suspend fun periodEntry(day: LocalDate): CycleEntryEntity? = dao.liveBetween(start(day), start(day.plusDays(1))).firstOrNull()

    suspend fun isPeriodDay(day: LocalDate): Boolean = periodEntry(day) != null

    /** The heaviness she logged for a day, or null if it isn't a period day. */
    suspend fun flow(day: LocalDate): FlowLevel? = periodEntry(day)?.flowLevel

    /**
     * Set (or clear) a day's flow. Null removes the period day; a level upserts it, tagged
     * as hers (an import never silently replaces it).
     */
    suspend fun setFlow(level: FlowLevel?, day: LocalDate) = writes.withLock {
        val existing = periodEntry(day)
        val now = clock.now()
        when {
            level != null && existing != null -> dao.upsert(
                existing.copy(flowLevelRaw = level.raw, sourceRaw = MANUAL, updatedAt = now, syncStatusRaw = SyncStatus.PENDING_UPLOAD.raw),
            )
            level != null -> dao.upsert(newEntry(day, level, now))
            existing != null -> dao.upsert(existing.softDeleted(now))
        }
    }

    /** Tap a day to log or unlog it as a plain period day (no level). */
    suspend fun togglePeriodDay(day: LocalDate) = writes.withLock {
        val existing = periodEntry(day)
        val now = clock.now()
        if (existing != null) dao.upsert(existing.softDeleted(now)) else dao.upsert(newEntry(day, FlowLevel.UNSPECIFIED, now))
    }

    /**
     * Cycle arithmetic over the last [lookbackDays] (iOS `stats`): real menstruation only, so
     * a stray spotting day never starts a "cycle" (spotting still shows on the timeline).
     */
    suspend fun stats(lookbackDays: Int = 400, today: LocalDate = today()): CycleStats =
        CycleStats.fromEntries(entries(today.minusDays(lookbackDays.toLong()), today).map { dayOf(it) to it.day })

    /** The start of the current cycle: the anchor for "Day N", the estimate and the phase. */
    suspend fun cycleStart(before: LocalDate): LocalDate? = stats(today = before).currentCycleStart(before)

    /** Gentle phase estimate (a guide, not a prediction). */
    suspend fun estimatedPhase(day: LocalDate): CyclePhase = stats(today = day).phase(day)

    private fun today(): LocalDate = clock.now().atZone(clock.zone()).toLocalDate()

    private suspend fun newEntry(day: LocalDate, level: FlowLevel, now: Instant) = CycleEntryEntity(
        id = UUID.randomUUID().toString().uppercase(), date = start(day), typeRaw = CycleEntryType.PERIOD_START.raw,
        flowLevelRaw = level.raw, sourceRaw = MANUAL, ownerID = ownerId(), createdAt = now, updatedAt = now,
    )

    private fun CycleEntryEntity.softDeleted(now: Instant) =
        copy(deletedAt = now, updatedAt = now, syncStatusRaw = SyncStatus.PENDING_UPLOAD.raw)

    private companion object {
        const val MANUAL = "manual"
    }
}
