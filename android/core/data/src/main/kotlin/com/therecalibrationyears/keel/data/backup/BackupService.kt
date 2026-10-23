package com.therecalibrationyears.keel.data.backup

import com.therecalibrationyears.keel.data.db.HealthDatabase
import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.data.db.RecordSnapshot
import com.therecalibrationyears.keel.domain.KeelClock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A backup made by a newer Keel, which this one can't read (the iOS message, verbatim). */
class UnsupportedBackupVersionException(val version: Int) :
    Exception("This backup was made by a newer version of Keel (format $version). Update the app to restore it.")

/**
 * Real local backup (port of iOS `BackupService`): every record to a portable JSON archive and
 * back, fully offline. The file is a faithful snapshot (ids, owner, timestamps, removed rows)
 * and a restore rebuilds the links (check-in symptoms, medicine logs), so a round trip loses
 * nothing.
 */
class BackupService(
    private val main: MainDatabase,
    private val health: HealthDatabase,
    private val clock: KeelClock,
    /** The app's version (iOS writes `CFBundleShortVersionString`). */
    private val appVersion: () -> String,
) {
    /** What a restore brought back (iOS `BackupService.Summary`). */
    data class Summary(
        val checkIns: Int, val symptoms: Int, val cycleEntries: Int, val medications: Int, val medicationLogs: Int, val activityLogs: Int,
    )

    /** Snapshot everything into an archive. */
    suspend fun export(): KeelBackup = withContext(Dispatchers.IO) {
        val r = main.backup().snapshot()
        KeelBackup(
            version = CURRENT_VERSION, exportedAt = clock.now().truncatedTo(ChronoUnit.SECONDS), appVersion = appVersion(),
            profiles = r.profiles, symptoms = r.symptoms, checkIns = r.checkIns, checkInSymptoms = r.checkInSymptoms,
            cycleEntries = r.cycleEntries, medications = r.medications, medicationLogs = r.medicationLogs,
            insights = r.insights, activityLogs = r.activityLogs, healthSamples = health.samples().all(),
        )
    }

    /** The archive as the `.keelbackup` file's bytes. */
    suspend fun exportData(): ByteArray = BackupCodec.encode(export()).toByteArray(Charsets.UTF_8)

    /** Read a file she chose; throws [BackupFormatException] if it isn't a Keel backup. */
    fun decode(data: ByteArray): KeelBackup = BackupCodec.decode(data.toString(Charsets.UTF_8), clock.zone(), clock.now())

    /**
     * Replace everything with the archive's contents. Refuses a newer format. Health Connect
     * access and onboarding belong to this phone, not the archive: a backup made while connected
     * must not switch reading on here, and she is restoring from inside the app, so she stays
     * onboarded.
     */
    suspend fun restore(backup: KeelBackup): Summary = withContext(Dispatchers.IO) {
        if (backup.version > CURRENT_VERSION) throw UnsupportedBackupVersionException(backup.version)
        val here = main.backup().profiles().filter { it.deletedAt == null }
        val connectedHere = here.any { it.healthConnected }
        val onboardedHere = here.mapNotNull { it.onboardingCompletedAt }.minOrNull()

        val checkInIds = backup.checkIns.map { it.id }.toSet()
        val symptomIds = backup.symptoms.map { it.id }.toSet()
        val medicationIds = backup.medications.map { it.id }.toSet()
        main.backup().replaceAll(
            RecordSnapshot(
                profiles = backup.profiles.map { it.copy(healthConnected = connectedHere, onboardingCompletedAt = onboardedHere) },
                symptoms = backup.symptoms,
                checkIns = backup.checkIns,
                // A link whose check-in or symptom isn't in the archive is skipped.
                checkInSymptoms = backup.checkInSymptoms.filter { it.checkInID in checkInIds && it.symptomID in symptomIds },
                cycleEntries = backup.cycleEntries,
                medications = backup.medications,
                // A log whose medicine isn't in the archive keeps its day, unlinked.
                medicationLogs = backup.medicationLogs.map { if (it.medicationID in medicationIds) it else it.copy(medicationID = null) },
                insights = backup.insights,
                activityLogs = backup.activityLogs,
            ),
        )
        health.samples().deleteAll()
        health.samples().insertAll(backup.healthSamples)
        Summary(
            checkIns = backup.checkIns.size, symptoms = backup.symptoms.size, cycleEntries = backup.cycleEntries.size,
            medications = backup.medications.size, medicationLogs = backup.medicationLogs.size, activityLogs = backup.activityLogs.size,
        )
    }

    /** The file name she saves it as: `Keel-backup-2026-10-22-1045.keelbackup` (plan D17). */
    fun fileName(): String =
        "Keel-backup-" + LocalDateTime.ofInstant(clock.now(), clock.zone()).format(STAMP) + ".keelbackup" // not-copy: file name

    companion object {
        /** Bumped whenever the archive's shape changes; a restore refuses newer versions. */
        const val CURRENT_VERSION = 1
        private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
    }
}
