package com.therecalibrationyears.keel.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.therecalibrationyears.keel.domain.CycleDay
import com.therecalibrationyears.keel.domain.CycleEntryType
import com.therecalibrationyears.keel.domain.FlowLevel
import com.therecalibrationyears.keel.domain.SyncStatus
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Port of iOS `CycleEntry` (Keel/Models/CycleEntry.swift): a period day she logged. Only her
 * own entries live here; imported period days go in the separate health store (M7), so no
 * estimate ever reads them.
 */
@Entity(tableName = "cycle_entry", indices = [Index("date")])
data class CycleEntryEntity(
    @PrimaryKey val id: String,
    /** Start of the local day. */
    val date: Instant,
    val typeRaw: String,
    /** How heavy; null (a legacy period day) reads as a plain period (`unspecified`). */
    val flowLevelRaw: String? = null,
    /** "manual" or "healthKit" (raw values kept identical to iOS). */
    val sourceRaw: String = "manual",
    val ownerID: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val syncStatusRaw: String = SyncStatus.PENDING_UPLOAD.raw,
) {
    val type: CycleEntryType get() = CycleEntryType.fromRaw(typeRaw)
    val flowLevel: FlowLevel get() = FlowLevel.fromRaw(flowLevelRaw)
    val day: CycleDay get() = CycleDay(type, flowLevel)
}

@Dao
interface CycleDao {
    @Query("SELECT * FROM cycle_entry WHERE deletedAt IS NULL ORDER BY date")
    fun observeLive(): Flow<List<CycleEntryEntity>>

    /** Live entries with `start <= date < end`, oldest first. */
    @Query("SELECT * FROM cycle_entry WHERE deletedAt IS NULL AND date >= :start AND date < :end ORDER BY date")
    suspend fun liveBetween(start: Instant, end: Instant): List<CycleEntryEntity>

    @Upsert suspend fun upsert(entry: CycleEntryEntity)
}
