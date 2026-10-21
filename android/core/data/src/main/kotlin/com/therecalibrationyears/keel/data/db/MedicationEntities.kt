package com.therecalibrationyears.keel.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.therecalibrationyears.keel.domain.SyncStatus
import java.time.Instant

/**
 * Port of iOS `Medication` (Keel/Models/Medication.swift): something she takes, a
 * prescribed treatment or a supplement. Every column is carried so backups map field for
 * field; the schedule rules (`DoseSchedule`) arrive with the Medications screen (M3).
 */
@Entity(tableName = "medication")
data class MedicationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dosage: String = "",
    val doseAmount: Double? = null,
    val doseUnitRaw: String? = null,
    val timing: String = "",
    val methodRaw: String? = null,
    val isActive: Boolean = true,
    /** The tick on the Medications screen: show it in Home's log. Not a dose taken. */
    val isTracked: Boolean = true,
    /** Opt-in: mark due doses taken without a tap. Off by default. */
    val autoLogDoses: Boolean = false,
    val scheduleKindRaw: String? = null,
    /** Bit 0 is Sunday through bit 6 Saturday (iOS Calendar weekdays). */
    val weekdaysMask: Int = 0,
    val cycleLength: Int = 28,
    val cyclePauseDays: Int = 7,
    val cycleAnchor: Instant? = null,
    /** `[{"id":"UPPER","weekdays":[1..7],"hour":8,"minute":0}]`, as iOS writes it. */
    val doseTimesJSON: String? = null,
    val doseTime: Instant? = null,
    val kindRaw: String = "supplement",
    val catalogGroupID: String? = null,
    val frequencyRaw: String? = null,
    val timeOfDayRaw: String? = null,
    val date: Instant? = null,
    val doseChangedAt: Instant? = null,
    /** The date she entered when she stopped taking it; null while she still is. */
    val stoppedAt: Instant? = null,
    val note: String? = null,
    val isOffLabel: Boolean = false,
    val isCompounded: Boolean = false,
    val ownerID: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val syncStatusRaw: String = SyncStatus.PENDING_UPLOAD.raw,
) {
    /** In Home's Medications log: the ones she tracks, plus any set to auto-log (iOS). */
    val appearsInHomeLog: Boolean get() = isTracked || autoLogDoses
}

/** Port of iOS `MedicationLog`: a per-day (or per-dose) "taken" record. */
@Entity(
    tableName = "medication_log",
    foreignKeys = [ForeignKey(MedicationEntity::class, ["id"], ["medicationID"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("medicationID"), Index("date")],
)
data class MedicationLogEntity(
    @PrimaryKey val id: String,
    /** Start of the local day the log applies to. */
    val date: Instant,
    /** Which dose of the day (the dose slot id); null is the whole day. */
    val slot: String? = null,
    val taken: Boolean,
    val medicationID: String?,
    val ownerID: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val syncStatusRaw: String = SyncStatus.PENDING_UPLOAD.raw,
)

/** Port of iOS `ActivityLog`: her own per-day amount for one activity (sleep hours...). */
@Entity(tableName = "activity_log", indices = [Index("activityID", "date")])
data class ActivityLogEntity(
    @PrimaryKey val id: String,
    /** Start of the local day. */
    val date: Instant,
    val activityID: String,
    val amount: Double = 0.0,
    val sourceRaw: String = "manual",
    val ownerID: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val syncStatusRaw: String = SyncStatus.PENDING_UPLOAD.raw,
)
