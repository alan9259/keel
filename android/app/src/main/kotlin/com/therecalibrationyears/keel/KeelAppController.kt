package com.therecalibrationyears.keel

import com.therecalibrationyears.keel.data.ActivityRepository
import com.therecalibrationyears.keel.data.CheckInRepository
import com.therecalibrationyears.keel.data.LocalIdentity
import com.therecalibrationyears.keel.data.MedicationRepository
import com.therecalibrationyears.keel.data.SettingsStore
import com.therecalibrationyears.keel.data.UserRepository
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.domain.Pathway
import com.therecalibrationyears.keel.home.HomeData
import com.therecalibrationyears.keel.onboarding.OnboardingActions
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The saved appearance settings, read once before the first frame. */
data class Appearance(val themeId: String?, val colourModeRaw: String?, val haptics: Boolean)

/**
 * App-level state and actions (port of the relevant parts of iOS `AppEnvironment`, with the
 * same method names). Grows with each milestone.
 */
class KeelAppController(
    private val users: UserRepository,
    private val identity: LocalIdentity,
    private val settings: SettingsStore,
    private val checkIns: CheckInRepository,
    private val medications: MedicationRepository,
    private val activities: ActivityRepository,
    val clock: KeelClock,
    scope: CoroutineScope,
) : OnboardingActions {

    /**
     * She has finished onboarding on this phone, read live from her profile in the database
     * (`onboardingCompletedAt`), so "Delete all my data" sends her back. Null until read.
     */
    val hasCompletedOnboarding: StateFlow<Boolean?> = users.observeCurrentProfile()
        .map { it?.onboardingCompletedAt != null }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val appearance: StateFlow<Appearance?> =
        combine(settings.themeId, settings.colourModeRaw, settings.haptics, ::Appearance)
            .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Everything Home reads, live from the database. Imported sleep is empty until Health
     * Connect is integrated (M7): nothing is imported yet, so nothing is shown for it.
     */
    val homeData: Flow<HomeData> = combine(
        checkIns.observeLive(), checkIns.observeLoggedSymptoms(), medications.observeActive(), medications.observeLogs(),
        combine(activities.observeAmounts("sleep"), settings.moodPackId) { sleep, pack -> sleep to pack },
    ) { checkInList, symptoms, meds, logs, (sleep, pack) ->
        HomeData(checkInList, symptoms, meds, logs, manualSleep = sleep, importedSleep = emptyList(), moodPackId = pack)
    }

    /**
     * She tapped a medicine on Home: unticking clears every log for it that day; ticking
     * records the whole day as taken (iOS `toggleMedicationFromHome`; reminders follow in M3).
     */
    suspend fun toggleMedicationFromHome(medication: MedicationEntity, day: LocalDate, currentlyTaken: Boolean) {
        if (currentlyTaken) medications.clearTaken(medication, day)
        else medications.setTaken(medication, day, slot = null, taken = true)
    }

    /** Everything the first frame depends on has loaded (the launch screen waits for this). */
    val isReady: Boolean get() = hasCompletedOnboarding.value != null && appearance.value != null

    /** She finished onboarding: record it on her profile (a reinstall onboards again). */
    suspend fun markOnboarded() = users.markOnboardingCompleted()

    /**
     * She just finished onboarding. Home first explains reminders in one line, and only
     * "Continue" there leads to the system prompt (iOS `completeOnboarding`).
     */
    suspend fun completeOnboarding() = settings.setNotificationExplainerPending(true)

    /** Establish a stable local identity and keep her first name, if she gave one. */
    override suspend fun saveFirstName(name: String) {
        identity.continueLocally()
        users.upsertProfile(
            firstName = name.trim().ifEmpty { UserRepository.DEFAULT_FIRST_NAME },
            email = null, appleUserID = null,
        )
    }

    override suspend fun setPathway(pathway: Pathway) = users.setPathway(pathway)

    // FAKE: stands in for the Health Connect permission request (port plan §3.6) — remove in M7 (not integrated yet: Continue just moves on, nothing is read)
    override suspend fun connectHealth(): Boolean = false
}
