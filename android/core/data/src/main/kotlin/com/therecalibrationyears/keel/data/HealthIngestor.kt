package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.HealthActivitySampleEntity
import com.therecalibrationyears.keel.data.db.HealthDatabase
import com.therecalibrationyears.keel.data.db.HealthFlowSampleEntity
import com.therecalibrationyears.keel.data.db.HealthSampleEntity
import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.domain.FlowLevel
import com.therecalibrationyears.keel.domain.HealthSnapshot
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.domain.oneDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Puts a Health Connect snapshot into the separate health store (port of iOS `HealthIngestor`):
 * activity into imported activity, readings into health samples, period days into imported
 * flow. Read-only toward her records: nothing imported is written into them, symptoms are never
 * imported, and a day she logged herself still wins when the two are shown together.
 * Idempotent: rows are found by their natural key (type and day) and refreshed to the latest.
 */
class HealthIngestor(
    private val health: HealthDatabase,
    private val main: MainDatabase,
    private val ownerId: suspend () -> String,
    private val clock: KeelClock,
) {
    private val writes = Mutex()

    data class Summary(val activity: Int = 0, val vitals: Int = 0, val flow: Int = 0) {
        val total: Int get() = activity + vitals + flow
    }

    private fun start(day: LocalDate): Instant = day.atStartOfDay(clock.zone()).toInstant()
    private fun dayOf(at: Instant): LocalDate = at.atZone(clock.zone()).toLocalDate()

    suspend fun ingest(snapshot: HealthSnapshot): Summary = writes.withLock {
        val amounts = if (snapshot.sleepByDay.isEmpty()) snapshot.activityAmounts else snapshot.activityAmounts + ("sleep" to snapshot.sleepByDay)
        var activity = 0
        amounts.forEach { (id, byDay) -> activity += ingestActivity(id, byDay) }
        var vitals = 0
        snapshot.vitals.forEach { vitals += ingestVitals(it) }
        Summary(activity, vitals, ingestFlow(snapshot.menstrualFlow, snapshot.flowWindowStart))
    }

    private suspend fun ingestActivity(activityId: String, byDay: Map<LocalDate, Double>): Int {
        val now = clock.now()
        val owner = ownerId()
        val existing = health.activity().liveOf(activityId).associateBy { dayOf(it.date) }
        val rows = byDay.filterValues { it > 0 }.mapNotNull { (day, raw) ->
            val value = oneDecimal(raw)
            val row = existing[day]
            when {
                // Health-authored: refreshed to the latest (Health Connect revises recent days).
                row != null -> if (row.amount == value) null else row.copy(amount = value, updatedAt = now)
                else -> HealthActivitySampleEntity(newId(), start(day), activityId, value, owner, now, now)
            }
        }
        if (rows.isNotEmpty()) health.activity().upsertAll(rows)
        return rows.size
    }

    private suspend fun ingestVitals(series: HealthSnapshot.VitalSeries): Int {
        val now = clock.now()
        val owner = ownerId()
        val existing = health.samples().liveOfType(series.typeId).associateBy { dayOf(it.day) }
        val rows = series.byDay.mapNotNull { (day, raw) ->
            val value = oneDecimal(raw)
            val row = existing[day]
            when {
                row != null -> if (row.value == value) null else row.copy(value = value, updatedAt = now)
                else -> HealthSampleEntity(newId(), series.typeId, start(day), value, series.unit, ownerID = owner, createdAt = now, updatedAt = now)
            }
        }
        if (rows.isNotEmpty()) health.samples().upsertAll(rows)
        return rows.size
    }

    /** With a window start, a day in the window that Health Connect no longer has is removed. */
    private suspend fun ingestFlow(byDay: Map<LocalDate, FlowLevel>, windowStart: LocalDate?): Int {
        val now = clock.now()
        val existing = health.flow().live()
        var wrote = 0
        if (windowStart != null) {
            val gone = existing.filter { val d = dayOf(it.date); d >= windowStart && d !in byDay }
            if (gone.isNotEmpty()) wrote += health.flow().delete(gone.map { it.id })
        }
        if (byDay.isEmpty()) return wrote
        val owner = ownerId()
        val existingByDay = existing.associateBy { dayOf(it.date) }
        val rows = byDay.mapNotNull { (day, level) ->
            val row = existingByDay[day]
            when {
                row != null -> if (row.flowLevel == level) null else row.copy(flowRaw = level.raw, updatedAt = now)
                else -> HealthFlowSampleEntity(newId(), start(day), level.raw, owner, now, now)
            }
        }
        if (rows.isNotEmpty()) health.flow().upsertAll(rows)
        return wrote + rows.size
    }

    /**
     * The launch clean-up (iOS `purgeDiscontinuedHealthImports`): imports older iOS builds wrote
     * into her own records, readings Keel no longer reads, and imported mindful minutes. They
     * only arrive here in a restored iPhone backup. Idempotent; returns the rows removed.
     */
    suspend fun purgeDiscontinuedHealthImports(): Int = writes.withLock {
        val legacy = main.legacyImports()
        legacy.deleteImportedSymptomLinks() + legacy.deleteImportedCycleEntries() + legacy.deleteImportedActivityLogs() +
            health.samples().deleteDiscontinued(DISCONTINUED_VITAL_TYPE_IDS.toList()) + health.activity().deleteDiscontinued()
    }

    /** "Remove imported Health Connect data": every imported row. Her own entries are untouched. */
    suspend fun purgeAllImportedHealthData(): Int = writes.withLock {
        health.activity().deleteAll() + health.samples().deleteAll() + health.flow().deleteAll()
    }

    private fun newId() = UUID.randomUUID().toString().uppercase()

    companion object {
        /** Readings Keel used to import but never showed, so no longer reads (iOS A3). */
        val DISCONTINUED_VITAL_TYPE_IDS = setOf("basalBodyTemperature")
    }
}
