package com.therecalibrationyears.keel.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.therecalibrationyears.keel.domain.EnergyLevel
import com.therecalibrationyears.keel.domain.Mood
import com.therecalibrationyears.keel.domain.SyncStatus
import java.time.Instant

/** Port of iOS `CheckIn` (Keel/Models/CheckIn.swift). */
@Entity(tableName = "check_in", indices = [Index("date")])
data class CheckInEntity(
    @PrimaryKey val id: String,
    /** When she made the entry (the exact time; Home groups by her local day). */
    val date: Instant,
    val moodRaw: String,
    /** Energy she picked as a percentage (20..100), or 0 when she didn't pick one. */
    val energy: Int = EnergyLevel.NOT_RECORDED,
    val notes: String? = null,
    /** Alcohol as a count she entered; null means not recorded (zero is a real answer). */
    val alcoholCount: Int? = null,
    val ownerID: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val syncStatusRaw: String = SyncStatus.PENDING_UPLOAD.raw,
) {
    val mood: Mood get() = Mood.fromRaw(moodRaw)
    val energyLevel: EnergyLevel? get() = EnergyLevel.picked(energy)
}

/**
 * Port of iOS `Symptom`: the shared catalog. Built-ins are global (empty `ownerID`);
 * custom ones are hers. Archived ones are hidden from pickers, never deleted.
 */
@Entity(tableName = "symptom", indices = [Index("name")])
data class SymptomEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Free string: a built-in category raw value or her own group (iOS `categoryRaw`). */
    val categoryRaw: String,
    val isCustom: Boolean = false,
    val isArchived: Boolean = false,
    val isDefaultChip: Boolean = false,
    val ownerID: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val syncStatusRaw: String = SyncStatus.PENDING_UPLOAD.raw,
)

/**
 * Port of iOS `CheckInSymptom`, the many-to-many link. Deleting a check-in removes its
 * links (cascade); removing a catalog symptom only clears the link's pointer (set null),
 * as the iOS delete rules do.
 */
@Entity(
    tableName = "check_in_symptom",
    foreignKeys = [
        ForeignKey(CheckInEntity::class, ["id"], ["checkInID"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SymptomEntity::class, ["id"], ["symptomID"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("checkInID"), Index("symptomID")],
)
data class CheckInSymptomEntity(
    @PrimaryKey val id: String,
    val checkInID: String,
    val symptomID: String?,
    /** 1 mild, 2 moderate, 3 severe. */
    val severity: Int = 1,
    /** "manual" or "healthKit" (raw values kept identical to iOS). */
    val sourceRaw: String = "manual",
    val ownerID: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val syncStatusRaw: String = SyncStatus.PENDING_UPLOAD.raw,
)

/** A symptom she logged on a check-in, with its catalog name, for display. */
data class LoggedSymptom(val linkId: String, val checkInID: String, val name: String, val severity: Int)
