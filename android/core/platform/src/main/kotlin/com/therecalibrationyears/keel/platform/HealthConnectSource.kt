package com.therecalibrationyears.keel.platform

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
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
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.therecalibrationyears.keel.domain.FlowLevel
import com.therecalibrationyears.keel.domain.HealthAvailability
import com.therecalibrationyears.keel.domain.HealthDataSource
import com.therecalibrationyears.keel.domain.HealthFlow
import com.therecalibrationyears.keel.domain.HealthSleep
import com.therecalibrationyears.keel.domain.HealthSnapshot
import com.therecalibrationyears.keel.domain.KeelClock
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import kotlin.reflect.KClass

/**
 * What Keel reads from Health Connect (plan §3.2): the iOS metric ids, kept, and the Health
 * Connect permission for each. Read-only: Keel asks for no write permission and reads nothing in
 * the background. Single source for the request, the manifest check and the Health screen.
 */
object HealthConnectReads {
    /** Health Connect itself (the provider app on Android 9 to 13). */
    const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata" // not-copy: package name

    val STEPS = HealthPermission.getReadPermission(StepsRecord::class)
    val EXERCISE = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    val SLEEP = HealthPermission.getReadPermission(SleepSessionRecord::class)
    val HEART_RATE = HealthPermission.getReadPermission(HeartRateRecord::class)
    val RESTING_HEART_RATE = HealthPermission.getReadPermission(RestingHeartRateRecord::class)
    val HRV = HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)
    val BLOOD_PRESSURE = HealthPermission.getReadPermission(BloodPressureRecord::class)
    val RESPIRATORY_RATE = HealthPermission.getReadPermission(RespiratoryRateRecord::class)
    val OXYGEN = HealthPermission.getReadPermission(OxygenSaturationRecord::class)
    val WEIGHT = HealthPermission.getReadPermission(WeightRecord::class)
    val BODY_TEMPERATURE = HealthPermission.getReadPermission(BodyTemperatureRecord::class)
    val SKIN_TEMPERATURE = HealthPermission.getReadPermission(SkinTemperatureRecord::class)
    val ACTIVE_ENERGY = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    val FLOORS = HealthPermission.getReadPermission(FloorsClimbedRecord::class)
    val DISTANCE = HealthPermission.getReadPermission(DistanceRecord::class)
    val MENSTRUATION = HealthPermission.getReadPermission(MenstruationFlowRecord::class)
    const val HISTORY = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    /** Every read permission Keel declares, in the plan's order (the manifest must list exactly these). */
    val all: List<String> = listOf(
        STEPS, EXERCISE, SLEEP, HEART_RATE, RESTING_HEART_RATE, HRV, BLOOD_PRESSURE, RESPIRATORY_RATE, OXYGEN,
        WEIGHT, BODY_TEMPERATURE, SKIN_TEMPERATURE, ACTIVE_ENERGY, FLOORS, DISTANCE, MENSTRUATION, HISTORY,
    )

    /** The vital ids Keel reads (iOS `readVitalTypeIDs`, with RMSSD for HRV, plan D13). */
    val vitalTypeIds = listOf(
        "heartRate", "restingHeartRate", "hrvRmssd", "bloodPressureSystolic", "bloodPressureDiastolic", "respiratoryRate",
        "oxygenSaturation", "bodyMass", "bodyTemperature", "wristTemperature", "activeEnergy", "flights", "distance",
    )
    val activityIds = listOf("steps", "exercise") // and sleep, its own read
}

/**
 * Health Connect as Keel's health source (plan §3): availability, permissions, and a year of
 * daily values. Daily totals and averages come from Health Connect's aggregates, which remove
 * duplicates across apps (plan D7); sleep, HRV, breathing, blood oxygen, temperatures and periods
 * from the records themselves. A type she hasn't allowed (or that fails) reads as empty.
 */
class HealthConnectSource internal constructor(
    private val clock: KeelClock,
    private val sdkStatus: () -> Int,
    clientFactory: () -> HealthConnectClient,
) : HealthDataSource {
    constructor(context: Context, clock: KeelClock) : this(
        clock,
        sdkStatus = { HealthConnectClient.getSdkStatus(context, HealthConnectReads.PROVIDER_PACKAGE) },
        clientFactory = { HealthConnectClient.getOrCreate(context) },
    )

    private val client: HealthConnectClient by lazy(clientFactory)

    override suspend fun availability(): HealthAvailability = when (sdkStatus()) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
        else -> HealthAvailability.UNAVAILABLE
    }

    private fun featureAvailable(feature: Int): Boolean = runCatching {
        client.features.getFeatureStatus(feature) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)

    override suspend fun requestablePermissions(): Set<String> {
        if (availability() != HealthAvailability.AVAILABLE) return emptySet()
        return HealthConnectReads.all.filter {
            when (it) {
                HealthConnectReads.SKIN_TEMPERATURE -> featureAvailable(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE)
                HealthConnectReads.HISTORY -> featureAvailable(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY)
                else -> true
            }
        }.toSet()
    }

    override suspend fun grantedPermissions(): Set<String> {
        if (availability() != HealthAvailability.AVAILABLE) return emptySet()
        return safely("granted", emptySet()) { client.permissionController.getGrantedPermissions() }
    }

    override suspend fun readsHistory(): Boolean = HealthConnectReads.HISTORY in grantedPermissions()

    override suspend fun revokeAll() {
        if (availability() != HealthAvailability.AVAILABLE) return
        safely("revoke", Unit) { client.permissionController.revokeAllPermissions() }
    }

    override suspend fun snapshot(lastDays: Int, flowWindowStart: LocalDate?): HealthSnapshot {
        if (availability() != HealthAvailability.AVAILABLE) return HealthSnapshot()
        val granted = grantedPermissions()
        if (granted.isEmpty()) return HealthSnapshot()
        val zone = clock.zone()
        val today = clock.now().atZone(zone).toLocalDate()
        val firstDay = today.minusDays(maxOf(lastDays, 1).toLong())
        val readFrom = firstDay.atStartOfDay(zone).toInstant()
        val now = clock.now()
        fun has(permission: String) = permission in granted

        val activity = mutableMapOf<String, Map<LocalDate, Double>>()
        val vitals = mutableListOf<HealthSnapshot.VitalSeries>()
        fun vital(typeId: String, unit: String, byDay: Map<LocalDate, Double>) {
            if (byDay.isNotEmpty()) vitals += HealthSnapshot.VitalSeries(typeId, unit, byDay)
        }

        if (has(HealthConnectReads.STEPS)) {
            daily(firstDay, today, StepsRecord.COUNT_TOTAL) { it.toDouble() }.takeIf { it.isNotEmpty() }?.let { activity["steps"] = it }
        }
        if (has(HealthConnectReads.EXERCISE)) {
            daily(firstDay, today, ExerciseSessionRecord.EXERCISE_DURATION_TOTAL) { it.toMillis() / 60_000.0 }
                .takeIf { it.isNotEmpty() }?.let { activity["exercise"] = it }
        }
        if (has(HealthConnectReads.HEART_RATE)) vital("heartRate", "bpm", daily(firstDay, today, HeartRateRecord.BPM_AVG) { it.toDouble() })
        if (has(HealthConnectReads.RESTING_HEART_RATE)) vital("restingHeartRate", "bpm", daily(firstDay, today, RestingHeartRateRecord.BPM_AVG) { it.toDouble() })
        if (has(HealthConnectReads.BLOOD_PRESSURE)) {
            vital("bloodPressureSystolic", "mmHg", daily(firstDay, today, BloodPressureRecord.SYSTOLIC_AVG) { it.inMillimetersOfMercury })
            vital("bloodPressureDiastolic", "mmHg", daily(firstDay, today, BloodPressureRecord.DIASTOLIC_AVG) { it.inMillimetersOfMercury })
        }
        if (has(HealthConnectReads.WEIGHT)) vital("bodyMass", "kg", daily(firstDay, today, WeightRecord.WEIGHT_AVG) { it.inKilograms })
        if (has(HealthConnectReads.ACTIVE_ENERGY)) vital("activeEnergy", "kcal", daily(firstDay, today, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL) { it.inKilocalories })
        if (has(HealthConnectReads.FLOORS)) vital("flights", "count", daily(firstDay, today, FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL) { it })
        if (has(HealthConnectReads.DISTANCE)) vital("distance", "km", daily(firstDay, today, DistanceRecord.DISTANCE_TOTAL) { it.inKilometers })

        if (has(HealthConnectReads.HRV)) {
            vital("hrvRmssd", "ms", dailyMean(records(HeartRateVariabilityRmssdRecord::class, readFrom, now).map { it.time to it.heartRateVariabilityMillis }))
        }
        if (has(HealthConnectReads.RESPIRATORY_RATE)) {
            vital("respiratoryRate", "br/min", dailyMean(records(RespiratoryRateRecord::class, readFrom, now).map { it.time to it.rate }))
        }
        if (has(HealthConnectReads.OXYGEN)) {
            // Health Connect already gives a percentage (no x100, unlike HealthKit's fraction).
            vital("oxygenSaturation", "%", dailyMean(records(OxygenSaturationRecord::class, readFrom, now).map { it.time to it.percentage.value }))
        }
        if (has(HealthConnectReads.BODY_TEMPERATURE)) {
            vital("bodyTemperature", "°C", dailyMean(records(BodyTemperatureRecord::class, readFrom, now).map { it.time to it.temperature.inCelsius }))
        }
        if (has(HealthConnectReads.SKIN_TEMPERATURE)) {
            // Only where Health Connect has a baseline: Keel never invents an absolute value.
            val nights = records(SkinTemperatureRecord::class, readFrom, now).mapNotNull { record ->
                val baseline = record.baseline ?: return@mapNotNull null
                val deltas = record.deltas.map { it.delta.inCelsius }
                record.startTime to baseline.inCelsius + (if (deltas.isEmpty()) 0.0 else deltas.average())
            }
            vital("wristTemperature", "°C", dailyMean(nights))
        }

        val sleep = if (has(HealthConnectReads.SLEEP)) sleepByDay(readFrom, now, firstDay) else emptyMap()
        // Null when the read failed (no access from the background, Health Connect's rate limit, a
        // dropped connection): that isn't "no periods", so nothing is reconciled against it.
        val flow = if (has(HealthConnectReads.MENSTRUATION)) menstrualFlow(readFrom, now) else null
        return HealthSnapshot(
            sleepByDay = sleep,
            activityAmounts = activity,
            vitals = vitals,
            menstrualFlow = flow.orEmpty(),
            // Reconcile deleted period days only when periods are allowed and were read here.
            flowWindowStart = if (flow != null) flowWindowStart else null,
        )
    }

    /** A night's sleep belongs to the day its session ended; a session that began the evening before the window still counts. */
    private suspend fun sleepByDay(readFrom: Instant, now: Instant, firstDay: LocalDate): Map<LocalDate, Double> {
        val sessions = records(SleepSessionRecord::class, readFrom.minus(java.time.Duration.ofDays(1)), now).map { record ->
            HealthSleep.Session(
                record.startTime, record.endTime,
                record.stages.map { HealthSleep.Segment(it.startTime, it.endTime, stage(it.stage)) },
            )
        }
        return HealthSleep.hoursByDay(sessions, clock.zone()).filterKeys { it >= firstDay }
    }

    /** Period days, or null if either read failed. */
    private suspend fun menstrualFlow(readFrom: Instant, now: Instant): Map<LocalDate, FlowLevel>? {
        val zone = clock.zone()
        val flow = recordsOrNull(MenstruationFlowRecord::class, readFrom, now)?.mapNotNull { record ->
            flowLevel(record.flow)?.let { record.time.atZone(zone).toLocalDate() to it }
        } ?: return null
        val periods = recordsOrNull(MenstruationPeriodRecord::class, readFrom, now)?.map { record ->
            // A period's end is exclusive: a record ending at midnight doesn't cover the next day.
            record.startTime.atZone(zone).toLocalDate()..record.endTime.minusNanos(1).atZone(zone).toLocalDate()
        } ?: return null
        return HealthFlow.byDay(flow, periods)
    }

    /** One aggregate per local day, in ranges Health Connect accepts; days with no data are left out. */
    private suspend fun <T : Any> daily(firstDay: LocalDate, today: LocalDate, metric: AggregateMetric<T>, value: (T) -> Double): Map<LocalDate, Double> {
        val out = mutableMapOf<LocalDate, Double>()
        var from = firstDay
        while (from <= today) {
            val to = minOf(from.plusDays(CHUNK_DAYS - 1), today)
            val groups = safely("aggregate", emptyList()) {
                client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(metric),
                        timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                        timeRangeSlicer = Period.ofDays(1),
                    ),
                )
            }
            groups.forEach { group -> valueOf(group.result, metric)?.let { out[group.startTime.toLocalDate()] = value(it) } }
            from = to.plusDays(1)
        }
        return out
    }

    private fun <T : Any> valueOf(result: AggregationResult, metric: AggregateMetric<T>): T? = result[metric]

    private fun dailyMean(points: List<Pair<Instant, Double>>): Map<LocalDate, Double> =
        points.groupBy({ it.first.atZone(clock.zone()).toLocalDate() }, { it.second }).mapValues { (_, values) -> values.average() }

    /** Every record of a type in the range, page by page. */
    private suspend fun <T : Record> records(type: KClass<T>, from: Instant, to: Instant): List<T> = recordsOrNull(type, from, to).orEmpty()

    /** Every record in the range, paged; null if the read failed. */
    private suspend fun <T : Record> recordsOrNull(type: KClass<T>, from: Instant, to: Instant): List<T>? = safely<List<T>?>("read ${type.simpleName}", null) {
        val out = mutableListOf<T>()
        var page: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(from, to), pageSize = PAGE_SIZE, pageToken = page))
            out += response.records
            page = response.pageToken
        } while (!page.isNullOrEmpty())
        out
    }

    /** A type she hasn't allowed, or a failed read, reads as empty: the sync then keeps trying later. */
    private suspend fun <R> safely(what: String, fallback: R, block: suspend () -> R): R = try {
        block()
    } catch (e: SecurityException) {
        Log.w(TAG, "$what: not allowed", e) // not-copy: log
        fallback
    } catch (e: IllegalStateException) {
        Log.w(TAG, "$what: failed", e) // not-copy: log
        fallback
    } catch (e: java.io.IOException) {
        Log.w(TAG, "$what: failed", e) // not-copy: log
        fallback
    } catch (e: android.os.RemoteException) {
        Log.w(TAG, "$what: failed", e) // not-copy: log
        fallback
    }

    companion object {
        private const val TAG = "KeelHealth" // not-copy: log tag
        /** Aggregates are read in three-month pieces (a year in one request can be refused). */
        private const val CHUNK_DAYS = 90L
        private const val PAGE_SIZE = 1000

        /** Health Connect's sleep stages, as Keel counts them (plan §3.3). */
        fun stage(type: Int): HealthSleep.Stage = when (type) {
            SleepSessionRecord.STAGE_TYPE_SLEEPING, SleepSessionRecord.STAGE_TYPE_LIGHT,
            SleepSessionRecord.STAGE_TYPE_DEEP, SleepSessionRecord.STAGE_TYPE_REM -> HealthSleep.Stage.ASLEEP
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> HealthSleep.Stage.IN_BED
            else -> HealthSleep.Stage.OTHER
        }

        /** Health Connect's flow levels; "unknown" is a period day with no level given (plan §3.4). */
        fun flowLevel(flow: Int): FlowLevel? = when (flow) {
            MenstruationFlowRecord.FLOW_LIGHT -> FlowLevel.LIGHT
            MenstruationFlowRecord.FLOW_MEDIUM -> FlowLevel.MEDIUM
            MenstruationFlowRecord.FLOW_HEAVY -> FlowLevel.HEAVY
            MenstruationFlowRecord.FLOW_UNKNOWN -> FlowLevel.UNSPECIFIED
            else -> null
        }
    }
}
