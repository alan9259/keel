package com.therecalibrationyears.keel

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.TemperatureDelta
import com.therecalibrationyears.keel.domain.HealthSync
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.reflect.KClass

/**
 * Debug only (never ships): `--ez uitHealthSeed true` writes a known week into Health Connect
 * (replacing what an earlier seed wrote), and `--ez uitHealthProbe true` logs what Keel reads
 * back, as `KEEL_HEALTH` lines, so the reader can be checked against these values:
 *
 *   yesterday   steps 3000 + 2500 = 5500; exercise 45 min; heart rate 60/70/80 (avg 70); resting 58;
 *               HRV 40 and 50 (45); BP 120/80; breathing 14; blood oxygen 97 %; weight 68.4 kg;
 *               body temp 36.6; active energy 300 kcal; 5 flights; 3.2 km; period day with no level
 *   2 days ago  steps 4000; flow medium; skin temperature 34.0 + mean(0.2, 0.4) = 34.3 (night began here)
 *   3 days ago  flow light and heavy (heavy wins)
 *   sleep       night ending yesterday: 23:00 to 07:00 with stages, asleep 6 h 55 m (6.92);
 *               night ending 2 days ago: 22:30 to 06:00, no stages (7.5); a skin temperature with
 *               no baseline that night (ignored)
 *
 * Grant first (Android 14+): `adb shell pm grant <package> android.permission.health.READ_STEPS` etc.
 */
internal class DebugHealth(private val container: AppContainer) {
    private val clock = container.clock
    private val zone = clock.zone()
    private val client by lazy { HealthConnectClient.getOrCreate(container.appContext) }
    private val today = clock.now().atZone(zone).toLocalDate()
    private val meta get() = Metadata.manualEntry()

    private fun at(day: LocalDate, hour: Int, minute: Int = 0): Instant = day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant()
    private fun offset(instant: Instant) = zone.rules.getOffset(instant)

    private val types: List<KClass<out Record>> = listOf(
        StepsRecord::class, ExerciseSessionRecord::class, SleepSessionRecord::class, HeartRateRecord::class,
        RestingHeartRateRecord::class, HeartRateVariabilityRmssdRecord::class, BloodPressureRecord::class,
        RespiratoryRateRecord::class, OxygenSaturationRecord::class, WeightRecord::class, BodyTemperatureRecord::class,
        SkinTemperatureRecord::class, ActiveCaloriesBurnedRecord::class, FloorsClimbedRecord::class, DistanceRecord::class,
        MenstruationFlowRecord::class, MenstruationPeriodRecord::class,
    )

    suspend fun seed() {
        val from = today.minusDays(10).atStartOfDay(zone).toInstant()
        val to = clock.now()
        // Only what Keel wrote goes: Health Connect never lets an app delete another's records.
        types.forEach { runCatching { client.deleteRecords(it, TimeRangeFilter.between(from, to)) }.onFailure { e -> log("delete ${it.simpleName}: $e") } }

        val d1 = today.minusDays(1)
        val d2 = today.minusDays(2)
        val d3 = today.minusDays(3)
        fun stage(start: Instant, end: Instant, type: Int) = SleepSessionRecord.Stage(start, end, type)
        val records = mutableListOf<Record>(
            StepsRecord(at(d1, 8), offset(at(d1, 8)), at(d1, 9), offset(at(d1, 9)), 3000, meta),
            StepsRecord(at(d1, 12), offset(at(d1, 12)), at(d1, 13), offset(at(d1, 13)), 2500, meta),
            StepsRecord(at(d2, 10), offset(at(d2, 10)), at(d2, 11), offset(at(d2, 11)), 4000, meta),
            ExerciseSessionRecord(at(d1, 7), offset(at(d1, 7)), at(d1, 7, 45), offset(at(d1, 7, 45)), meta, ExerciseSessionRecord.EXERCISE_TYPE_WALKING),
            SleepSessionRecord(
                at(d2, 23), offset(at(d2, 23)), at(d1, 7), offset(at(d1, 7)), meta,
                stages = listOf(
                    stage(at(d2, 23), at(d2, 23, 20), SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED),
                    stage(at(d2, 23, 20), at(d1, 2), SleepSessionRecord.STAGE_TYPE_LIGHT),
                    stage(at(d1, 2), at(d1, 3, 30), SleepSessionRecord.STAGE_TYPE_DEEP),
                    stage(at(d1, 3, 30), at(d1, 3, 45), SleepSessionRecord.STAGE_TYPE_AWAKE),
                    stage(at(d1, 3, 45), at(d1, 6, 30), SleepSessionRecord.STAGE_TYPE_REM),
                    stage(at(d1, 6, 30), at(d1, 7), SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED),
                ),
            ),
            SleepSessionRecord(at(d3, 22, 30), offset(at(d3, 22, 30)), at(d2, 6), offset(at(d2, 6)), meta),
            HeartRateRecord(
                at(d1, 9), offset(at(d1, 9)), at(d1, 9, 10), offset(at(d1, 9, 10)),
                listOf(60L, 70L, 80L).mapIndexed { i, bpm -> HeartRateRecord.Sample(at(d1, 9, i * 3), bpm) }, meta,
            ),
            RestingHeartRateRecord(at(d1, 6), offset(at(d1, 6)), 58, meta),
            HeartRateVariabilityRmssdRecord(at(d1, 5), offset(at(d1, 5)), 40.0, meta),
            HeartRateVariabilityRmssdRecord(at(d1, 6), offset(at(d1, 6)), 50.0, meta),
            BloodPressureRecord(at(d1, 8), offset(at(d1, 8)), meta, Pressure.millimetersOfMercury(120.0), Pressure.millimetersOfMercury(80.0)),
            RespiratoryRateRecord(at(d1, 4), offset(at(d1, 4)), 14.0, meta),
            OxygenSaturationRecord(at(d1, 4), offset(at(d1, 4)), Percentage(97.0), meta),
            WeightRecord(at(d1, 7), offset(at(d1, 7)), Mass.kilograms(68.4), meta),
            BodyTemperatureRecord(at(d1, 7), offset(at(d1, 7)), meta, Temperature.celsius(36.6)),
            SkinTemperatureRecord(
                at(d2, 23), offset(at(d2, 23)), at(d1, 7), offset(at(d1, 7)), meta,
                deltas = listOf(SkinTemperatureRecord.Delta(at(d1, 1), TemperatureDelta.celsius(0.2)), SkinTemperatureRecord.Delta(at(d1, 4), TemperatureDelta.celsius(0.4))),
                baseline = Temperature.celsius(34.0),
            ),
            SkinTemperatureRecord(
                at(d3, 23), offset(at(d3, 23)), at(d2, 6), offset(at(d2, 6)), meta,
                deltas = listOf(SkinTemperatureRecord.Delta(at(d2, 1), TemperatureDelta.celsius(0.5))),
            ),
            ActiveCaloriesBurnedRecord(at(d1, 7), offset(at(d1, 7)), at(d1, 8), offset(at(d1, 8)), Energy.kilocalories(300.0), meta),
            FloorsClimbedRecord(at(d1, 10), offset(at(d1, 10)), at(d1, 11), offset(at(d1, 11)), 5.0, meta),
            DistanceRecord(at(d1, 7), offset(at(d1, 7)), at(d1, 7, 45), offset(at(d1, 7, 45)), Length.kilometers(3.2), meta),
            MenstruationFlowRecord(at(d3, 8), offset(at(d3, 8)), meta, MenstruationFlowRecord.FLOW_LIGHT),
            MenstruationFlowRecord(at(d3, 20), offset(at(d3, 20)), meta, MenstruationFlowRecord.FLOW_HEAVY),
            MenstruationFlowRecord(at(d2, 8), offset(at(d2, 8)), meta, MenstruationFlowRecord.FLOW_MEDIUM),
            // Covers 3 days ago to yesterday (the end is exclusive): yesterday has no level.
            MenstruationPeriodRecord(at(d3, 0), offset(at(d3, 0)), at(today, 0), offset(at(today, 0)), meta),
        )
        runCatching { client.insertRecords(records) }
            .onSuccess { log("seeded ${it.recordIdsList.size} records for $d3 to $d1 ($zone)") }
            .onFailure { log("seed failed: $it") }
    }

    suspend fun probe() {
        val source = container.healthSource
        log("availability ${source.availability()}")
        val requestable = source.requestablePermissions()
        val granted = source.grantedPermissions()
        log("requestable ${requestable.size}: ${requestable.map { it.substringAfterLast('.') }.sorted()}")
        log("granted ${granted.size}: ${granted.map { it.substringAfterLast('.') }.sorted()}")
        log("history ${source.readsHistory()}")
        val snapshot = source.snapshot(HealthSync.IMPORT_DAYS, flowWindowStart = today.minusDays(HealthSync.IMPORT_DAYS.toLong()))
        log("sleep ${snapshot.sleepByDay.toSortedMap()}")
        snapshot.activityAmounts.toSortedMap().forEach { (id, byDay) -> log("activity $id ${byDay.toSortedMap()}") }
        snapshot.vitals.sortedBy { it.typeId }.forEach { log("vital ${it.typeId} ${it.unit} ${it.byDay.toSortedMap()}") }
        log("flow ${snapshot.menstrualFlow.toSortedMap()} window ${snapshot.flowWindowStart}")
        log("probe done")
    }

    private fun log(line: String) = Log.i("KEEL_HEALTH", line)
}
