package com.therecalibrationyears.keel.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_in WHERE deletedAt IS NULL ORDER BY date DESC")
    fun observeLive(): Flow<List<CheckInEntity>>

    /** Live links whose catalog symptom still exists (iOS `loggedSymptoms`). */
    @Query(
        "SELECT l.id AS linkId, l.checkInID AS checkInID, s.name AS name, l.severity AS severity " +
            "FROM check_in_symptom l JOIN symptom s ON s.id = l.symptomID " +
            "WHERE l.deletedAt IS NULL ORDER BY l.createdAt",
    )
    fun observeLoggedSymptoms(): Flow<List<LoggedSymptom>>

    @Upsert suspend fun upsert(checkIn: CheckInEntity)
    @Upsert suspend fun upsertLink(link: CheckInSymptomEntity)
}

@Dao
interface SymptomDao {
    @Query("SELECT * FROM symptom WHERE deletedAt IS NULL ORDER BY createdAt")
    suspend fun allLive(): List<SymptomEntity>

    @Upsert suspend fun upsert(symptom: SymptomEntity)
}

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medication WHERE deletedAt IS NULL AND isActive = 1 ORDER BY createdAt")
    fun observeActive(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medication_log WHERE deletedAt IS NULL")
    fun observeLiveLogs(): Flow<List<MedicationLogEntity>>

    @Query(
        "SELECT * FROM medication_log WHERE deletedAt IS NULL AND medicationID = :medicationId " +
            "AND date >= :start AND date < :end",
    )
    suspend fun liveLogsBetween(medicationId: String, start: Instant, end: Instant): List<MedicationLogEntity>

    @Upsert suspend fun upsert(medication: MedicationEntity)
    @Upsert suspend fun upsertLog(log: MedicationLogEntity)
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_log WHERE deletedAt IS NULL AND activityID = :activityId")
    fun observeLive(activityId: String): Flow<List<ActivityLogEntity>>

    @Upsert suspend fun upsert(log: ActivityLogEntity)
}
