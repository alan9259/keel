package com.therecalibrationyears.keel.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.therecalibrationyears.keel.domain.Hysterectomy
import com.therecalibrationyears.keel.domain.Pathway
import com.therecalibrationyears.keel.domain.SyncStatus
import java.time.Instant
import java.time.LocalDate

/**
 * Her profile (port of iOS `UserProfile`, Keel/Models/UserProfile.swift). Same fields and
 * meanings; `healthConnected` is the Android name for `healthKitAuthorized`. The sync
 * envelope (`ownerID`, timestamps, tombstone, status) matches iOS `Syncable`.
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    /** Upper-case UUID, as iOS writes it. */
    @PrimaryKey val id: String,
    val firstName: String,
    val lastName: String? = null,
    /** Year of birth, not age, so the value never drifts. */
    val birthYear: Int? = null,
    val mobile: String? = null,
    val email: String? = null,
    /** Her own note when periods no longer apply, in her words (GP summary cycle block). */
    val periodsNotApplicableReason: String? = null,
    val hysterectomyRaw: String? = null,
    val hysterectomyYear: Int? = null,
    /** Only ever carried through from an iOS backup (plan D21); Android has no Apple sign-in. */
    val appleUserID: String? = null,
    val pathwayRaw: String? = null,
    val healthConnected: Boolean = false,
    /**
     * When she finished onboarding on this phone. Device state: not in backups. Uninstalling
     * deletes the database, so a reinstall onboards afresh.
     */
    val onboardingCompletedAt: Instant? = null,
    val trackingStartDate: Instant,
    // Non-identifying environment context (DeviceContext), refreshed on every upsert.
    val region: String? = null,
    val localeID: String? = null,
    val timeZoneID: String? = null,
    val appVersion: String? = null,
    val deviceModel: String? = null,
    val osVersion: String? = null,
    // Sync envelope
    val ownerID: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val syncStatusRaw: String = SyncStatus.PENDING_UPLOAD.raw,
) {
    val pathway: Pathway? get() = Pathway.fromRaw(pathwayRaw)
    val hysterectomy: Hysterectomy? get() = Hysterectomy.fromRaw(hysterectomyRaw)

    /** Her age this year, from the year of birth she gave (iOS `age`). */
    fun age(today: LocalDate): Int? = birthYear?.let { today.year - it }

    /** Marks the record changed so the next sync pass uploads it (iOS `touch`). */
    fun touched(now: Instant) = copy(updatedAt = now, syncStatusRaw = SyncStatus.PENDING_UPLOAD.raw)
}
