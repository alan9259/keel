package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.UserProfileDao
import com.therecalibrationyears.keel.data.db.UserProfileEntity
import com.therecalibrationyears.keel.domain.Hysterectomy
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.domain.Pathway
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Port of iOS `UserRepository` (Keel/Persistence/Repositories/UserRepository.swift).
 * Writes are serialised so two quick taps can never create two profiles.
 */
class UserRepository(
    private val dao: UserProfileDao,
    private val ownerId: suspend () -> String,
    private val clock: KeelClock,
    private val device: DeviceContext.Source,
) {
    private val writes = Mutex()

    suspend fun currentProfile(): UserProfileEntity? = dao.current()

    fun observeCurrentProfile(): Flow<UserProfileEntity?> = dao.observeCurrent()

    suspend fun upsertProfile(firstName: String, email: String?, appleUserID: String?): UserProfileEntity =
        writes.withLock {
            val now = clock.now()
            val existing = dao.current()
            val profile = existing?.copy(
                firstName = firstName,
                email = email ?: existing.email,
                appleUserID = appleUserID ?: existing.appleUserID,
            )?.touched(now)
                ?: newProfile(firstName, now).copy(email = email, appleUserID = appleUserID)
            stamped(profile).also { dao.upsert(it) }
        }

    /**
     * Save the basic details she edits on the Profile screen. Optional fields are written
     * exactly as given (null clears them), so the form is the source of truth; a blank first
     * name is ignored so she's never left unnamed.
     */
    suspend fun updateBasicInfo(
        firstName: String, lastName: String?, birthYear: Int?, mobile: String?, email: String?,
    ): UserProfileEntity = writes.withLock {
        val now = clock.now()
        val name = firstName.trim().ifEmpty { null }
        val base = dao.current() ?: newProfile(name ?: DEFAULT_FIRST_NAME, now)
        val profile = base.copy(
            firstName = name ?: base.firstName,
            lastName = lastName, birthYear = birthYear, mobile = mobile, email = email,
        ).touched(now)
        stamped(profile).also { dao.upsert(it) }
    }

    suspend fun setPathway(pathway: Pathway) = writes.withLock {
        val profile = dao.current() ?: return@withLock
        dao.upsert(profile.copy(pathwayRaw = pathway.raw).touched(clock.now()))
    }

    /** Her own note for when periods no longer apply (trimmed; blank clears it). */
    suspend fun setPeriodsNotApplicableReason(reason: String?) = writes.withLock {
        val profile = dao.current() ?: return@withLock
        dao.upsert(profile.copy(periodsNotApplicableReason = reason?.trim()?.ifEmpty { null }).touched(clock.now()))
    }

    /** Her hysterectomy answer (null clears it). The year is kept only for a "yes" answer. */
    suspend fun setHysterectomy(answer: Hysterectomy?, year: Int?) = writes.withLock {
        val profile = dao.current() ?: return@withLock
        dao.upsert(
            profile.copy(hysterectomyRaw = answer?.raw, hysterectomyYear = if (answer?.allowsYear == true) year else null)
                .touched(clock.now()),
        )
    }

    /**
     * Creates a profile if somehow none exists yet, so the connected flag is never silently
     * dropped. Every onboarding path makes one, so this is defensive.
     */
    suspend fun setHealthConnected(connected: Boolean) = writes.withLock {
        val now = clock.now()
        val profile = dao.current() ?: newProfile(DEFAULT_FIRST_NAME, now)
        dao.upsert(profile.copy(healthConnected = connected).touched(now))
    }

    /** Record that she finished onboarding; keeps the first date, and creates a profile if needed. */
    suspend fun markOnboardingCompleted(at: Instant = clock.now()) = writes.withLock {
        val profile = dao.current() ?: newProfile(DEFAULT_FIRST_NAME, clock.now())
        if (profile.onboardingCompletedAt != null) return@withLock
        dao.upsert(profile.copy(onboardingCompletedAt = at).touched(clock.now()))
    }

    private suspend fun newProfile(firstName: String, now: Instant) = UserProfileEntity(
        id = UUID.randomUUID().toString().uppercase(),
        firstName = firstName,
        trackingStartDate = now,
        ownerID = ownerId(),
        createdAt = now,
        updatedAt = now,
    )

    /** Record the current non-identifying environment context (refreshed on every upsert). */
    private fun stamped(profile: UserProfileEntity): UserProfileEntity {
        val d = device.current()
        return profile.copy(
            region = d.region, localeID = d.localeID, timeZoneID = d.timeZoneID,
            appVersion = d.appVersion, deviceModel = d.deviceModel, osVersion = d.osVersion,
        )
    }

    companion object {
        /** What Keel calls her when she leaves the first name blank (iOS uses the same word). */
        const val DEFAULT_FIRST_NAME = "there"
    }
}
