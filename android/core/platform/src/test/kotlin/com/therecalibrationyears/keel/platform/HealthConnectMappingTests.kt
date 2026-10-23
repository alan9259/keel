package com.therecalibrationyears.keel.platform

import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.SleepSessionRecord
import com.therecalibrationyears.keel.domain.FlowLevel
import com.therecalibrationyears.keel.domain.HealthSleep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Health Connect's sleep stages and flow levels as Keel counts them (plan §3.3, §3.4). Android
 * only: HealthKit's categories map in `HealthKitService`, which has no test of its own.
 */
class HealthConnectMappingTests {
    @Test fun sleepingStagesCountAsAsleep() {
        listOf(
            SleepSessionRecord.STAGE_TYPE_SLEEPING, SleepSessionRecord.STAGE_TYPE_LIGHT,
            SleepSessionRecord.STAGE_TYPE_DEEP, SleepSessionRecord.STAGE_TYPE_REM,
        ).forEach { assertEquals(HealthSleep.Stage.ASLEEP, HealthConnectSource.stage(it)) }
    }

    @Test fun awakeInBedIsInBed() {
        assertEquals(HealthSleep.Stage.IN_BED, HealthConnectSource.stage(SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED))
    }

    @Test fun awakeOutOfBedAndUnknownAreNeither() {
        listOf(
            SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
            SleepSessionRecord.STAGE_TYPE_UNKNOWN, 99,
        ).forEach { assertEquals(HealthSleep.Stage.OTHER, HealthConnectSource.stage(it)) }
    }

    @Test fun flowLevelsMapAndUnknownIsAPeriodDayWithNoLevel() {
        assertEquals(FlowLevel.LIGHT, HealthConnectSource.flowLevel(MenstruationFlowRecord.FLOW_LIGHT))
        assertEquals(FlowLevel.MEDIUM, HealthConnectSource.flowLevel(MenstruationFlowRecord.FLOW_MEDIUM))
        assertEquals(FlowLevel.HEAVY, HealthConnectSource.flowLevel(MenstruationFlowRecord.FLOW_HEAVY))
        assertEquals(FlowLevel.UNSPECIFIED, HealthConnectSource.flowLevel(MenstruationFlowRecord.FLOW_UNKNOWN))
        assertNull(HealthConnectSource.flowLevel(42)) // a level Health Connect adds later is left out, not guessed
    }
}
