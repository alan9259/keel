package com.therecalibrationyears.keel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** Every row of her records, removed ones included: what a backup carries. */
data class RecordSnapshot(
    val profiles: List<UserProfileEntity>,
    val symptoms: List<SymptomEntity>,
    val checkIns: List<CheckInEntity>,
    val checkInSymptoms: List<CheckInSymptomEntity>,
    val cycleEntries: List<CycleEntryEntity>,
    val medications: List<MedicationEntity>,
    val medicationLogs: List<MedicationLogEntity>,
    val insights: List<InsightEntity>,
    val activityLogs: List<ActivityLogEntity>,
)

/** Whole-store reads and the restore's replace (port of the store access in iOS `BackupService`). */
@Dao
abstract class BackupDao {
    @Query("SELECT * FROM user_profile") abstract suspend fun profiles(): List<UserProfileEntity>
    @Query("SELECT * FROM symptom") abstract suspend fun symptoms(): List<SymptomEntity>
    @Query("SELECT * FROM check_in ORDER BY date") abstract suspend fun checkIns(): List<CheckInEntity>
    @Query("SELECT * FROM check_in_symptom") abstract suspend fun checkInSymptoms(): List<CheckInSymptomEntity>
    @Query("SELECT * FROM cycle_entry ORDER BY date") abstract suspend fun cycleEntries(): List<CycleEntryEntity>
    @Query("SELECT * FROM medication") abstract suspend fun medications(): List<MedicationEntity>
    @Query("SELECT * FROM medication_log ORDER BY date") abstract suspend fun medicationLogs(): List<MedicationLogEntity>
    @Query("SELECT * FROM insight") abstract suspend fun insights(): List<InsightEntity>
    @Query("SELECT * FROM activity_log ORDER BY date") abstract suspend fun activityLogs(): List<ActivityLogEntity>

    @Transaction
    open suspend fun snapshot() = RecordSnapshot(
        profiles(), symptoms(), checkIns(), checkInSymptoms(), cycleEntries(), medications(), medicationLogs(),
        insights(), activityLogs(),
    )

    @Query("DELETE FROM check_in_symptom") protected abstract suspend fun deleteCheckInSymptoms()
    @Query("DELETE FROM medication_log") protected abstract suspend fun deleteMedicationLogs()
    @Query("DELETE FROM check_in") protected abstract suspend fun deleteCheckIns()
    @Query("DELETE FROM medication") protected abstract suspend fun deleteMedications()
    @Query("DELETE FROM symptom") protected abstract suspend fun deleteSymptoms()
    @Query("DELETE FROM cycle_entry") protected abstract suspend fun deleteCycleEntries()
    @Query("DELETE FROM insight") protected abstract suspend fun deleteInsights()
    @Query("DELETE FROM activity_log") protected abstract suspend fun deleteActivityLogs()
    @Query("DELETE FROM user_profile") protected abstract suspend fun deleteProfiles()

    @Insert protected abstract suspend fun insertProfiles(rows: List<UserProfileEntity>)
    @Insert protected abstract suspend fun insertSymptoms(rows: List<SymptomEntity>)
    @Insert protected abstract suspend fun insertCheckIns(rows: List<CheckInEntity>)
    @Insert protected abstract suspend fun insertCheckInSymptoms(rows: List<CheckInSymptomEntity>)
    @Insert protected abstract suspend fun insertCycleEntries(rows: List<CycleEntryEntity>)
    @Insert protected abstract suspend fun insertMedications(rows: List<MedicationEntity>)
    @Insert protected abstract suspend fun insertMedicationLogs(rows: List<MedicationLogEntity>)
    @Insert protected abstract suspend fun insertInsights(rows: List<InsightEntity>)
    @Insert protected abstract suspend fun insertActivityLogs(rows: List<ActivityLogEntity>)

    /**
     * Replace everything with [records] in one transaction: children wiped before parents,
     * parents inserted before the rows that point at them. Nothing is half-restored.
     */
    @Transaction
    open suspend fun replaceAll(records: RecordSnapshot) {
        deleteCheckInSymptoms(); deleteMedicationLogs(); deleteCheckIns(); deleteMedications(); deleteSymptoms()
        deleteCycleEntries(); deleteInsights(); deleteActivityLogs(); deleteProfiles()
        insertSymptoms(records.symptoms)
        insertProfiles(records.profiles)
        insertCycleEntries(records.cycleEntries)
        insertInsights(records.insights)
        insertActivityLogs(records.activityLogs)
        insertCheckIns(records.checkIns)
        insertMedications(records.medications)
        insertCheckInSymptoms(records.checkInSymptoms)
        insertMedicationLogs(records.medicationLogs)
    }
}
