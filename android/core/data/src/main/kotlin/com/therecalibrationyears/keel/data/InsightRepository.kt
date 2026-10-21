package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.CheckInDao
import com.therecalibrationyears.keel.data.db.CycleDao
import com.therecalibrationyears.keel.data.db.InsightDao
import com.therecalibrationyears.keel.data.db.InsightEntity
import com.therecalibrationyears.keel.domain.Insight
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.domain.PatternEngine
import com.therecalibrationyears.keel.domain.SyncStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Port of iOS `InsightRepository`: Looking back's pattern cards, derived from her own data
 * and rewritten at each launch. Honest by design: only real findings become cards, and none
 * until she has checked in on [MIN_DAYS] days.
 */
class InsightRepository(
    private val dao: InsightDao,
    private val checkIns: CheckInDao,
    private val cycle: CycleDao,
    private val ownerId: suspend () -> String = { "" },
    private val clock: KeelClock = KeelClock.System,
) {
    fun observeAll(): Flow<List<Insight>> = dao.observeLive().map { list -> list.map { it.insight } }

    /** Re-derive the cards from the pattern engine and replace the stored set. */
    suspend fun refreshDerived() {
        val zone = clock.zone()
        val now = clock.now()
        val today = now.atZone(zone).toLocalDate()
        val floor = today.minusDays((PatternEngine.WINDOW - 1).toLong())
        val since = floor.atStartOfDay(zone).toInstant()
        val engine = PatternEngine.build(
            checkInDays = checkIns.liveDatesSince(since).map { it.atZone(zone).toLocalDate() },
            cycleDays = cycle.liveBetween(since, today.plusDays(1).atStartOfDay(zone).toInstant()).map { it.date.atZone(zone).toLocalDate() to it.day },
            today = today,
        )
        val owner = ownerId()
        val cards = if (engine.loggedDayCount < MIN_DAYS) emptyList() else engine.findings().map {
            InsightEntity(
                id = UUID.randomUUID().toString().uppercase(), title = it.title, detail = it.detail, timeframe = it.timeframe,
                iconKey = it.icon, accentRaw = it.accent.raw, generatedAt = now,
                ownerID = owner, createdAt = now, updatedAt = now, syncStatusRaw = SyncStatus.SYNCED.raw,
            )
        }
        dao.replaceAll(cards)
    }

    companion object {
        /** Days of check-ins before Keel attempts a real pattern. */
        const val MIN_DAYS = 5
    }
}
