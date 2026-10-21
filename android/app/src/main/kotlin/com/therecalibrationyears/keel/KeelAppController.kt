package com.therecalibrationyears.keel

import com.therecalibrationyears.keel.data.ActivityRepository
import com.therecalibrationyears.keel.data.CheckInRepository
import com.therecalibrationyears.keel.data.LocalIdentity
import com.therecalibrationyears.keel.data.MedicationRepository
import com.therecalibrationyears.keel.data.SettingsStore
import com.therecalibrationyears.keel.data.SymptomPick
import com.therecalibrationyears.keel.data.SymptomRepository
import com.therecalibrationyears.keel.data.TreatmentCatalogStore
import com.therecalibrationyears.keel.data.UserRepository
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.data.db.SymptomEntity
import com.therecalibrationyears.keel.checkin.CheckInActions
import com.therecalibrationyears.keel.checkin.CheckInDraft
import com.therecalibrationyears.keel.checkin.CheckInRequest
import com.therecalibrationyears.keel.checkin.SymptomPickerActions
import com.therecalibrationyears.keel.domain.CheckInRules
import com.therecalibrationyears.keel.domain.EnergyLevel
import com.therecalibrationyears.keel.domain.MoodPack
import com.therecalibrationyears.keel.domain.MoodPacks
import com.therecalibrationyears.keel.platform.SpeechRecognitionService
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.domain.Pathway
import com.therecalibrationyears.keel.domain.TreatmentCatalog
import com.therecalibrationyears.keel.domain.TreatmentDraft
import com.therecalibrationyears.keel.home.HomeData
import com.therecalibrationyears.keel.medications.MedicationsActions
import java.time.Instant
import java.time.ZoneId
import com.therecalibrationyears.keel.onboarding.OnboardingActions
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private val symptoms: SymptomRepository,
    private val medications: MedicationRepository,
    private val activities: ActivityRepository,
    private val speech: SpeechRecognitionService,
    private val treatments: TreatmentCatalogStore,
    val clock: KeelClock,
    private val scope: CoroutineScope,
) : OnboardingActions {

    /**
     * Launch work (iOS `bootstrap`, in its order; later milestones add their steps): bring
     * the built-in symptom catalog up to date, carry older medicine entries onto schedules,
     * tidy brand examples out of their names, then log today's auto-logged doses.
     */
    fun bootstrap() {
        scope.launch {
            symptoms.syncBuiltIns()
            medications.migrateLegacySchedules()
            medications.stripExampleBrandsFromNames()
            autoLogTodaysDoses()
        }
    }

    /**
     * Log today's arrived doses for medicines set to auto-log (iOS `autoLogTodaysDoses`).
     * Runs while the app is open: a day she never opens Keel isn't logged.
     */
    suspend fun autoLogTodaysDoses() {
        medications.autoLogTodaysDueDoses()
    }

    /** Her chosen mood pack (the default until she picks one). */
    val moodPack: StateFlow<MoodPack> = settings.moodPackId.map { MoodPacks.pack(it) }
        .stateIn(scope, SharingStarted.Eagerly, MoodPacks.pack(null))

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

    /** Opening an existing entry to edit: its mood and day (iOS `onEditEntry`). */
    suspend fun editRequest(checkInId: String): CheckInRequest? = checkIns.entry(checkInId)?.let {
        CheckInRequest(it.mood, it.date.atZone(clock.zone()).toLocalDate(), it.id)
    }

    /**
     * Save the check-in (port of iOS `CheckInModal.save`): create or update the entry with
     * the symptoms still in the catalog, then alcohol (only while the setting is on, so
     * turning it off never erases a number she recorded) and her own sleep for that night.
     */
    suspend fun saveCheckIn(request: CheckInRequest, draft: CheckInDraft) {
        val active = symptoms.allActive().map { it.id }.toSet()
        val picks = draft.selected.filter { (id, level) -> level > 0 && id in active }.map { (id, level) -> SymptomPick(id, level) }
        val energy = draft.energy?.percent ?: EnergyLevel.NOT_RECORDED
        val existing = request.editingId?.let { checkIns.entry(it) }
        val id = if (existing != null) {
            checkIns.update(existing.id, request.mood, energy, draft.notes, picks)
            existing.id
        } else {
            val stamp = CheckInRules.entryTimestamp(request.day, clock.now(), clock.zone())
            checkIns.create(request.mood, energy, draft.notes, picks, stamp).id
        }
        if (settings.notesAlcohol.first()) checkIns.setAlcohol(id, draft.alcohol)
        if (!draft.sleepFromHealth) activities.saveManualSleep(request.day, draft.sleepHours)
    }

    /** The check-in screen's view of the app. */
    val checkInActions: CheckInActions = object : CheckInActions {
        override val activeSymptoms: Flow<List<SymptomEntity>> = symptoms.observeActive()
        override val notesAlcohol: Flow<Boolean> = settings.notesAlcohol
        override suspend fun quickChipIds(): List<String> = symptoms.defaultChips().map { it.id }

        override suspend fun loadDraft(request: CheckInRequest): CheckInDraft {
            val sleep = activities.manualSleep(request.day)
            val entry = request.editingId?.let { checkIns.entry(it) } ?: return CheckInDraft(sleepHours = sleep)
            return CheckInDraft(
                energy = entry.energyLevel, notes = entry.notes.orEmpty(), sleepHours = sleep, alcohol = entry.alcoholCount,
                selected = checkIns.picks(entry.id).associate { it.symptomId to it.severity },
            )
        }

        override suspend fun save(request: CheckInRequest, draft: CheckInDraft) = saveCheckIn(request, draft)
        override suspend fun delete(checkInId: String) = checkIns.delete(checkInId)

        override val voice: SpeechRecognitionService = speech

        override val picker: SymptomPickerActions = object : SymptomPickerActions {
            override val showsSensitive: Flow<Boolean> = settings.showsSensitiveSymptoms
            override suspend fun showSensitive() { settings.setShowsSensitiveSymptoms(true) }
            override suspend fun addCustom(name: String, categoryRaw: String) { symptoms.findOrCreateCustom(name, categoryRaw) }
            override suspend fun archive(symptom: SymptomEntity) = symptoms.archive(symptom)
            override suspend fun rename(symptom: SymptomEntity, name: String) = symptoms.rename(symptom, name)
            override suspend fun move(symptom: SymptomEntity, categoryRaw: String) = symptoms.move(symptom, categoryRaw)
        }
    }

    /** The Medications screen's view of the app. */
    val medicationsActions: MedicationsActions = object : MedicationsActions {
        override val active = medications.observeActive()
        override val stopped = medications.observeStopped()
        override val catalog: TreatmentCatalog get() = treatments.catalog
        override val zone: ZoneId get() = clock.zone()
        override fun now(): Instant = clock.now()

        override suspend fun add(draft: TreatmentDraft) {
            medications.add(draft)
            autoLogTodaysDoses() // added with auto-log on: tick today now
        }

        override suspend fun update(id: String, draft: TreatmentDraft) {
            medications.update(id, draft)
            autoLogTodaysDoses() // turning auto-log on ticks today's log straight away
        }

        override suspend fun remove(id: String) = medications.archive(id)
        override suspend fun setTracked(id: String, tracked: Boolean) = medications.setTracked(id, tracked)
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
