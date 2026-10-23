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
import com.therecalibrationyears.keel.domain.SyncStatus
import java.time.Instant

/**
 * A daily value imported from a health app (port of iOS `HealthSample`): vitals and workload,
 * one row per metric and day. Lives in its own database, apart from her records, so nothing
 * imported can reach a sync seam (plan §1.4). Health Connect fills it in M7; until then it
 * holds only what a restored backup carried.
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

@Dao
interface HealthSampleDao {
    /** Every row, removed ones included (a backup is a faithful snapshot). */
    @Query("SELECT * FROM health_sample ORDER BY day, typeID")
    suspend fun all(): List<HealthSampleEntity>

    @Insert suspend fun insertAll(rows: List<HealthSampleEntity>)

    @Query("DELETE FROM health_sample")
    suspend fun deleteAll()
}

/** Imported health data (port of the iOS "health" SwiftData store). */
@Database(entities = [HealthSampleEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun samples(): HealthSampleDao

    companion object {
        private const val FILE_NAME = "keel-health.db"

        fun open(context: Context): HealthDatabase =
            Room.databaseBuilder(context, HealthDatabase::class.java, FILE_NAME).build()

        fun inMemory(context: Context): HealthDatabase =
            Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java).build()
    }
}
