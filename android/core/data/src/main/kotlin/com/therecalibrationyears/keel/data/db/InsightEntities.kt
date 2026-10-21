package com.therecalibrationyears.keel.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.therecalibrationyears.keel.domain.Insight
import com.therecalibrationyears.keel.domain.InsightAccent
import com.therecalibrationyears.keel.domain.SyncStatus
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Port of iOS `Insight`: a card on Looking back, derived from her own data by the pattern
 * engine at launch (`InsightRepository.refreshDerived`). Same columns, so backups map field
 * for field.
 */
@Entity(tableName = "insight")
data class InsightEntity(
    @PrimaryKey val id: String,
    val title: String,
    val detail: String,
    val timeframe: String,
    /** The iOS SF Symbol name for the leading icon. */
    val iconKey: String,
    /** "terracotta", "sage" or "warmGrey". */
    val accentRaw: String,
    val generatedAt: Instant,
    val ownerID: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val syncStatusRaw: String = SyncStatus.PENDING_UPLOAD.raw,
) {
    val insight: Insight get() = Insight(title, detail, timeframe, iconKey, InsightAccent.fromRaw(accentRaw))
}

@Dao
interface InsightDao {
    @Query("SELECT * FROM insight WHERE deletedAt IS NULL ORDER BY generatedAt DESC")
    fun observeLive(): Flow<List<InsightEntity>>

    @Query("SELECT * FROM insight")
    suspend fun all(): List<InsightEntity>

    @Query("DELETE FROM insight")
    suspend fun deleteAll()

    @Insert suspend fun insertAll(insights: List<InsightEntity>)

    /** The stored set is rewritten whole, in one transaction (iOS deletes, then inserts). */
    @Transaction
    suspend fun replaceAll(insights: List<InsightEntity>) {
        deleteAll()
        insertAll(insights)
    }
}
