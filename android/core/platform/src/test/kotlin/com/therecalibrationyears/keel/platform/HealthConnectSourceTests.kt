package com.therecalibrationyears.keel.platform

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ReadRecordsResponse
import com.therecalibrationyears.keel.domain.KeelClock
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android-only: an empty period read lets Keel remove imported period days she deleted in Health
 * Connect, so a read that *failed* (Health Connect refusing a read from the background, its rate
 * limit, a dropped connection) must not look like an empty one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // for android.util.Log in the failure path
class HealthConnectSourceTests {
    private val clock = object : KeelClock {
        override fun now(): Instant = Instant.parse("2026-10-21T01:00:00Z")
        override fun zone(): ZoneId = ZoneId.of("Australia/Sydney")
    }
    private val window = LocalDate.of(2026, 9, 21)

    /** A Health Connect client that allows periods only, and fails or returns no records. */
    private fun client(readFails: Boolean): HealthConnectClient {
        val permissions = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PermissionController::class.java)) { _, method, _ ->
            when (method.name) {
                "getGrantedPermissions" -> setOf(HealthConnectReads.MENSTRUATION)
                else -> error("unexpected ${method.name}")
            }
        } as PermissionController
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(HealthConnectClient::class.java)) { _, method, args ->
            when (method.name) {
                "getPermissionController" -> permissions
                "readRecords" -> {
                    if (readFails) throw SecurityException("Caller doesn't have permission to read data in background")
                    check(args!![0] is ReadRecordsRequest<*>)
                    // The response's constructor is internal to the library.
                    ReadRecordsResponse::class.java.declaredConstructors.single().apply { isAccessible = true }
                        .newInstance(emptyList<Record>(), null)
                }
                else -> error("unexpected ${method.name}")
            }
        } as HealthConnectClient
    }

    private fun source(readFails: Boolean) =
        HealthConnectSource(clock, sdkStatus = { HealthConnectClient.SDK_AVAILABLE }, clientFactory = { client(readFails) })

    @Test fun aFailedPeriodReadReconcilesNothing() = runBlocking {
        val snapshot = source(readFails = true).snapshot(365, window)
        assertTrue(snapshot.menstrualFlow.isEmpty())
        assertNull("a failed read must not remove her imported period days", snapshot.flowWindowStart)
    }

    @Test fun anEmptyPeriodReadReconcilesTheWindow() = runBlocking {
        val snapshot = source(readFails = false).snapshot(365, window)
        assertTrue(snapshot.menstrualFlow.isEmpty())
        assertEquals(window, snapshot.flowWindowStart)
    }
}
