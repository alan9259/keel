package com.therecalibrationyears.keel.platform

import com.therecalibrationyears.keel.domain.HealthSyncCatalog
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port of KeelTests/SubmissionPackATests.swift (A3, the health read types). Health Connect
 * only shows its dialog for permissions the manifest declares, and Play reviews every one
 * against a feature: the manifest, the request and the sync controls must agree.
 */
class HealthConnectManifestTests {
    private val manifest = File("src/main/AndroidManifest.xml")

    private fun declaredHealthPermissions(): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(manifest)
        val nodes = doc.getElementsByTagName("uses-permission")
        return (0 until nodes.length)
            .map { nodes.item(it).attributes.getNamedItemNS("http://schemas.android.com/apk/res/android", "name").nodeValue }
            .filter { it.startsWith("android.permission.health.") }
            .toSet()
    }

    @Test fun readsOnlyTheTypesKeelShows() {
        assertEquals(
            setOf(
                "heartRate", "restingHeartRate", "hrvRmssd", "respiratoryRate", "oxygenSaturation",
                "bloodPressureSystolic", "bloodPressureDiastolic", "bodyMass",
                "bodyTemperature", "wristTemperature", "activeEnergy", "flights", "distance",
            ),
            HealthConnectReads.vitalTypeIds.toSet(),
        )
        assertFalse("basalBodyTemperature" in HealthConnectReads.vitalTypeIds)
        assertEquals(setOf("steps", "exercise"), HealthConnectReads.activityIds.toSet()) // + sleep, its own read
    }

    @Test fun syncControlsOnlyCoverReadTypes() {
        val offered = HealthSyncCatalog.all.flatMap { it.vitalTypeIds }.toSet()
        assertTrue(HealthConnectReads.vitalTypeIds.toSet().containsAll(offered))
        val activity = HealthSyncCatalog.all.flatMap { it.activityIds }.toSet()
        assertTrue((HealthConnectReads.activityIds + "sleep").toSet().containsAll(activity))
    }

    @Test fun manifestDeclaresExactlyTheReads() {
        assertEquals(HealthConnectReads.all.toSet(), declaredHealthPermissions())
        assertEquals("no permission listed twice", HealthConnectReads.all.size, HealthConnectReads.all.toSet().size)
    }

    @Test fun asksForNoWriteOrBackgroundAccess() {
        val declared = declaredHealthPermissions()
        assertTrue(declared.none { it.contains(".WRITE_") })
        assertFalse("android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND" in declared)
    }

    @Test fun canFindHealthConnectOnOlderAndroid() {
        assertTrue(manifest.readText().contains("<package android:name=\"${HealthConnectReads.PROVIDER_PACKAGE}\" />"))
    }
}
