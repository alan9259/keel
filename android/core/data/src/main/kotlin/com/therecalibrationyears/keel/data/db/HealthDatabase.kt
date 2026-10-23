package com.therecalibrationyears.keel.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.therecalibrationyears.keel.domain.FlowLevel
import com.therecalibrationyears.keel.domain.SyncStatus
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * A daily value imported from Health Connect (port of iOS `HealthSample`): vitals and workload,
 * one row per metric and day. Lives in its own database, apart from her records, so nothing
 * imported can reach a sync seam (plan §1.4).
 */
@Entity(tableName = "health_sample", indices = [Index("typeID", "day")])
data class HealthSampleEntity(
    @PrimaryKey val id: String,
    /** Stable metric key, e.g. "heartRate", "restingHeartRate", "bodyMass". */
    val typeID: String,
    /** Start of the local day the value is for. */
    val day: Instant,
    val value: Double,
    val unit: String,
    val sourceRaw: String = "healthKit",
    val ownerID: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val syncStatusRaw: String = SyncStatus.SYNCED.raw,
)

/** Imported activity (port of iOS `HealthActivitySample`): sleep, steps, exercise. Her own entries win per day. */
@Entity(tableName = "health_activity_sample", indices = [Index("activityID", "date")])
data class HealthActivitySampleEntity(
    @PrimaryKey val id: String,
    /** Start of the local day. */
    val date: Instant,
    val activityID: String,
    val amount: Double,
    val ownerID: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val syncStatusRaw: String = SyncStatus.SYNCED.raw,
)

/**
 * An imported period day (port of iOS `HealthFlowSample`): shown on the Cycle screen, labelled,
 * never used for an estimate or a pattern, and never written into her own cycle entries.
 */
@Entity(tableName = "health_flow_sample", indices = [Index("date")])
data class HealthFlowSampleEntity(
    @PrimaryKey val id: String,
    /** Start of the local day. */
    val date: Instant,
    val flowRaw: String = FlowLevel.UNSPECIFIED.raw,
    val ownerID: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val syncStatusRaw: String = SyncStatus.SYNCED.raw,
) {
    val flowLevel: FlowLevel get() = FlowLevel.fromRaw(flowRaw)
}

@Dao
interface HealthSampleDao {
    /** Every row, removed ones included (a backup is a faithful snapshot). */
    @Query("SELECT * FROM health_sample ORDER BY day, typeID")
    suspend fun all(): List<HealthSampleEntity>

    @Query("SELECT * FROM health_sample WHERE deletedAt IS NULL AND typeID = :typeID")
    suspend fun liveOfType(typeID: String): List<HealthSampleEntity>

    @Query("SELECT * FROM health_sample WHERE deletedAt IS NULL ORDER BY day")
    fun observeLive(): Flow<List<HealthSampleEntity>>

    @Query("SELECT COUNT(*) FROM health_sample WHERE deletedAt IS NULL")
    fun observeLiveCount(): Flow<Int>

    @Insert suspend fun insertAll(rows: List<HealthSampleEntity>)
    @Upsert suspend fun upsertAll(rows: List<HealthSampleEntity>)

    /** Readings Keel no longer imports (iOS `discontinuedVitalTypeIDs`) and old `symptom.*` rows. */
    @Query("DELETE FROM health_sample WHERE typeID IN (:typeIDs) OR typeID LIKE 'symptom.%'")
    suspend fun deleteDiscontinued(typeIDs: List<String>): Int

    @Query("DELETE FROM health_sample")
    suspend fun deleteAll(): Int
}

@Dao
interface HealthActivityDao {
    @Query("SELECT * FROM health_activity_sample WHERE deletedAt IS NULL AND activityID = :activityID")
    suspend fun liveOf(activityID: String): List<HealthActivitySampleEntity>

    @Query("SELECT * FROM health_activity_sample WHERE deletedAt IS NULL ORDER BY date")
    fun observeLive(): Flow<List<HealthActivitySampleEntity>>

    @Query("SELECT * FROM health_activity_sample WHERE deletedAt IS NULL AND activityID = :activityID ORDER BY date")
    fun observeLiveOf(activityID: String): Flow<List<HealthActivitySampleEntity>>

    @Query("SELECT COUNT(*) FROM health_activity_sample WHERE deletedAt IS NULL")
    fun observeLiveCount(): Flow<Int>

    @Upsert suspend fun upsertAll(rows: List<HealthActivitySampleEntity>)

    /** Mindful minutes are no longer imported (iOS): only the imported copies go. */
    @Query("DELETE FROM health_activity_sample WHERE activityID = 'meditation'")
    suspend fun deleteDiscontinued(): Int

    @Query("DELETE FROM health_activity_sample")
    suspend fun deleteAll(): Int
}

@Dao
interface HealthFlowDao {
    @Query("SELECT * FROM health_flow_sample WHERE deletedAt IS NULL ORDER BY date")
    suspend fun live(): List<HealthFlowSampleEntity>

    @Query("SELECT * FROM health_flow_sample WHERE deletedAt IS NULL ORDER BY date")
    fun observeLive(): Flow<List<HealthFlowSampleEntity>>

    @Query("SELECT COUNT(*) FROM health_flow_sample WHERE deletedAt IS NULL")
    fun observeLiveCount(): Flow<Int>

    @Upsert suspend fun upsertAll(rows: List<HealthFlowSampleEntity>)

    @Query("DELETE FROM health_flow_sample WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>): Int

    @Query("DELETE FROM health_flow_sample")
    suspend fun deleteAll(): Int
}

/** Imported health data (port of the iOS "health" SwiftData store). */
@Database(
    entities = [HealthSampleEntity::class, HealthActivitySampleEntity::class, HealthFlowSampleEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun samples(): HealthSampleDao
    abstract fun activity(): HealthActivityDao
    abstract fun flow(): HealthFlowDao

    companion object {
        private const val FILE_NAME = "keel-health.db"

        fun open(context: Context): HealthDatabase =
            Room.databaseBuilder(context, HealthDatabase::class.java, FILE_NAME).build()

        fun inMemory(context: Context): HealthDatabase =
            Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).build()
    }
}
