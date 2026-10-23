package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.HealthDatabase
import com.therecalibrationyears.keel.data.db.HealthSampleEntity
import com.therecalibrationyears.keel.domain.DayAmount
import com.therecalibrationyears.keel.domain.FlowLevel
import com.therecalibrationyears.keel.domain.KeelClock
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Reads of the imported health store, for the screens that show it beside her own record. */
class HealthRepository(private val health: HealthDatabase, private val clock: KeelClock) {
    private fun day(at: java.time.Instant): LocalDate = at.atZone(clock.zone()).toLocalDate()

    /** Imported activity (sleep, steps, exercise), live. */
    fun observeActivity(): Flow<List<DayAmount>> =
        health.activity().observeLive().map { rows -> rows.map { DayAmount(it.activityID, day(it.date), it.amount) } }

    fun observeActivity(activityId: String): Flow<List<DayAmount>> =
        health.activity().observeLiveOf(activityId).map { rows -> rows.map { DayAmount(it.activityID, day(it.date), it.amount) } }

    /** Imported readings (vitals, energy, distance, flights), live. */
    fun observeReadings(): Flow<List<Pair<HealthSampleEntity, LocalDate>>> =
        health.samples().observeLive().map { rows -> rows.map { it to day(it.day) } }

    /** Imported period days, live: shown on the Cycle screen, never used for an estimate. */
    fun observeFlow(): Flow<Map<LocalDate, FlowLevel>> =
        health.flow().observeLive().map { rows -> rows.associate { day(it.date) to it.flowLevel } }

    /** Anything imported at all (live rows only, as the iOS Health screen's note counts). */
    fun observeHasImportedData(): Flow<Boolean> =
        combine(health.activity().observeLiveCount(), health.samples().observeLiveCount(), health.flow().observeLiveCount()) { a, s, f ->
            a + s + f > 0
        }

    suspend fun hasImportedData(): Boolean = observeHasImportedData().first()
}
