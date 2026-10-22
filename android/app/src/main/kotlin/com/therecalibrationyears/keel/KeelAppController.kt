package com.therecalibrationyears.keel

import com.therecalibrationyears.keel.data.ActivityRepository
import com.therecalibrationyears.keel.data.CheckInRepository
import com.therecalibrationyears.keel.data.CycleRepository
import com.therecalibrationyears.keel.data.InsightRepository
import com.therecalibrationyears.keel.data.GPSummaryService
import com.therecalibrationyears.keel.domain.GPAboutMe
import com.therecalibrationyears.keel.domain.GPReviewMed
import com.therecalibrationyears.keel.domain.GPSummaryDocument
import com.therecalibrationyears.keel.domain.GPSummaryInputs
import com.therecalibrationyears.keel.gpsummary.GPSummaryActions
import com.therecalibrationyears.keel.domain.MonthSummary
import com.therecalibrationyears.keel.insights.ActivitiesActions
import com.therecalibrationyears.keel.insights.ActivitiesData
import com.therecalibrationyears.keel.insights.DayFeeling
import com.therecalibrationyears.keel.insights.LookingBackActions
import com.therecalibrationyears.keel.insights.StatsActions
import com.therecalibrationyears.keel.insights.StatsData
import com.therecalibrationyears.keel.insights.StatsEntry
import com.therecalibrationyears.keel.insights.LookingBackData
import com.therecalibrationyears.keel.cycle.CycleActions
import com.therecalibrationyears.keel.cycle.CycleData
import com.therecalibrationyears.keel.domain.FlowLevel
import com.therecalibrationyears.keel.domain.PatternEngine
import com.therecalibrationyears.keel.domain.PatternFinding
import com.therecalibrationyears.keel.data.LocalIdentity
import com.therecalibrationyears.keel.data.LoggedDose
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
import com.therecalibrationyears.keel.settings.AppInfo
import com.therecalibrationyears.keel.settings.AppearanceActions
import com.therecalibrationyears.keel.settings.RemindersActions
import com.therecalibrationyears.keel.settings.SettingsActions
import com.therecalibrationyears.keel.designsystem.ColourMode
import com.therecalibrationyears.keel.data.RecordEraser
import com.therecalibrationyears.keel.domain.ReminderConfig
import com.therecalibrationyears.keel.domain.ReminderKinds
import java.time.Instant
import java.time.ZoneId
import com.therecalibrationyears.keel.onboarding.OnboardingActions
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val cycle: CycleRepository,
    private val insights: InsightRepository,
    private val gpSummary: GPSummaryService,
    private val speech: SpeechRecognitionService,
    private val treatments: TreatmentCatalogStore,
    val reminders: KeelReminders,
    val clock: KeelClock,
    private val scope: CoroutineScope,
    private val records: RecordEraser,
    /** The app's version and support context, for More and feedback drafts. */
    val appInfo: AppInfo,
    /** Removes files Keel made for sharing (the GP summary PDF in its cache). */
    private val clearSharedFiles: () -> Unit = {},
) : OnboardingActions {

    /**
     * Launch work (iOS `bootstrap`, in its order; later milestones add their steps): bring
     * the built-in symptom catalog up to date, re-derive Looking back's pattern cards, carry
     * older medicine entries onto schedules, tidy brand examples out of their names, then the
     * everyday reminders, today's auto-logged doses and the medication reminders.
     */
    fun bootstrap() {
        scope.launch {
            symptoms.syncBuiltIns()
            insights.refreshDerived()
            medications.migrateLegacySchedules()
            medications.stripExampleBrandsFromNames()
            reminders.refreshAll(::autoLogTodaysDoses)
        }
    }

    /**
     * Keel came to the foreground: log today's arrived auto-log doses and top up the
     * medication reminders (laid out a few weeks ahead, so opening the app extends them).
     */
    fun onForeground() {
        scope.launch {
            autoLogTodaysDoses()
            reminders.refreshMedicationReminders()
        }
    }

    /** A reboot, a clock or time zone change, or an update: alarms are re-armed from scratch. */
    suspend fun rearmReminders() = reminders.refreshAll(::autoLogTodaysDoses)

    /**
     * Log today's arrived doses for medicines set to auto-log (iOS `autoLogTodaysDoses`), and
     * drop their now-redundant reminders. Runs while the app is open: a day she never opens
     * Keel isn't logged.
     */
    suspend fun autoLogTodaysDoses(): List<LoggedDose> {
        val logged = medications.autoLogTodaysDueDoses()
        val today = clock.now().atZone(clock.zone()).toLocalDate()
        logged.forEach { reminders.cancelMedicationReminders(it.medicationId, today, it.slot) }
        return logged
    }

    /** "Mark taken" on a reminder, or a tap on it (iOS `markMedicationTaken`). */
    suspend fun markMedicationTaken(medicationId: String, slot: String?, day: LocalDate) {
        val med = medications.active(medicationId) ?: return
        medications.setTaken(med, day, slot, taken = true)
        // Logged, so no nudge for this dose again today.
        reminders.cancelMedicationReminders(med.id, day, slot)
    }

    /** "Always mark taken": auto-log this medicine from now on, and log this dose. */
    suspend fun enableMedicationAutoLog(medicationId: String, slot: String?, day: LocalDate) {
        val med = medications.active(medicationId) ?: return
        medications.setAutoLog(med.id, true)
        medications.setTaken(med, day, slot, taken = true)
        // Its reminders now carry the auto-log wording, and today's logged dose is skipped.
        reminders.refreshMedicationReminders()
    }

    /** The reminders explanation is owed (set when onboarding finishes). */
    val notificationExplainerPending: Flow<Boolean> = settings.notificationExplainerPending

    /** "Continue" on the reminders explanation, after Android's prompt (if any) has answered. */
    suspend fun answerNotificationExplainer() {
        reminders.recordNotificationExplainerAnswer(allow = true)
        reminders.refreshLifestyleReminders()
        reminders.refreshMedicationReminders()
    }

    private val exactTimingOffer = MutableStateFlow(false)

    /**
     * "Reminders on time" is owed (Android only, D25): she just saved a medicine whose
     * reminders Android would deliver late. Shown once, never during onboarding.
     */
    val exactTimingOfferPending: StateFlow<Boolean> = exactTimingOffer.asStateFlow()

    private suspend fun offerExactTimingIfLate(medicationId: String) {
        if (reminders.shouldOfferExactTiming(medicationId)) exactTimingOffer.value = true
    }

    /** "Continue" on "Reminders on time" (which then opens Android's page): told once. */
    suspend fun answerExactTimingOffer() {
        reminders.recordExactTimingOffered()
        exactTimingOffer.value = false
    }

    /** Keel asked Android for notification permission (it can't say "never asked" itself). */
    suspend fun markNotificationPermissionAsked() { settings.setNotificationPermissionAsked(true) }
    val notificationPermissionAsked: Flow<Boolean> = settings.notificationPermissionAsked

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
     * She tapped a medicine on Home (iOS `toggleMedicationFromHome`). Ticking records the whole
     * day and cancels that day's remaining reminders; unticking clears every log for it that
     * day and lays them out again, so a still-upcoming dose can nudge her.
     */
    suspend fun toggleMedicationFromHome(medication: MedicationEntity, day: LocalDate, currentlyTaken: Boolean) {
        if (currentlyTaken) {
            medications.clearTaken(medication, day)
            reminders.refreshMedicationReminders()
        } else {
            medications.setTaken(medication, day, slot = null, taken = true)
            reminders.cancelMedicationReminders(medication.id, day, slot = null)
        }
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
            val med = medications.add(draft)
            autoLogTodaysDoses() // added with auto-log on: tick today now
            reminders.refreshMedicationReminders()
            offerExactTimingIfLate(med.id)
        }

        override suspend fun update(id: String, draft: TreatmentDraft) {
            medications.update(id, draft)
            autoLogTodaysDoses() // turning auto-log on ticks today's log straight away
            reminders.refreshMedicationReminders()
            offerExactTimingIfLate(id)
        }

        override suspend fun remove(id: String) {
            medications.archive(id)
            reminders.cancelMedicationReminders(id)
        }
        override suspend fun setTracked(id: String, tracked: Boolean) = medications.setTracked(id, tracked)
    }

    /**
     * The Cycle screen's view of the app: her period days, live, and the cycle-length finding
     * from the pattern engine (iOS reads it from the same records). Imported period days are
     * empty until Health Connect is integrated (M7): nothing is imported yet, so none show.
     */
    val cycleActions: CycleActions = object : CycleActions {
        override val data: Flow<CycleData> = combine(cycle.observeDays(), checkIns.observeLive()) { days, entries ->
            val zone = clock.zone()
            val today = clock.now().atZone(zone).toLocalDate()
            val finding = PatternEngine.build(entries.map { it.date.atZone(zone).toLocalDate() }, days, today)
                .findings().firstOrNull { it.kind == PatternFinding.Kind.CYCLE_VARIABILITY }
            CycleData(days = days, imported = emptyMap(), finding = finding)
        }
        override val zone: ZoneId get() = clock.zone()
        override fun now(): Instant = clock.now()
        override suspend fun setFlow(level: FlowLevel?, day: LocalDate) = cycle.setFlow(level, day)
    }

    /**
     * Looking back's view of the app (iOS `PatternsView`): this month's summary from her live
     * check-ins and their symptoms, whether she has written anything, and the pattern cards.
     */
    val lookingBackActions: LookingBackActions = object : LookingBackActions {
        override val data: Flow<LookingBackData> =
            combine(checkIns.observeLive(), checkIns.observeLoggedSymptoms(), insights.observeAll()) { entries, logged, cards ->
                val names = logged.groupBy({ it.checkInID }, { it.name })
                val summary = MonthSummary.build(
                    entries.map { MonthSummary.Entry(it.id, it.date, names[it.id].orEmpty(), it.notes) }, clock.now(), clock.zone(),
                )
                LookingBackData(summary, hasAnyNotes = entries.any { !it.notes.isNullOrBlank() }, insights = cards)
            }
        override val allNotes: Flow<List<MonthSummary.Note>> = checkIns.observeLive().map { entries ->
            MonthSummary.notes(entries.map { MonthSummary.Entry(it.id, it.date, emptyList(), it.notes) })
        }
        override val zone: ZoneId get() = clock.zone()
        override fun now(): Instant = clock.now()
    }

    /**
     * The Activities screen's view of the app (iOS `ActivitiesView`): her own entries, her
     * check-ins, the Health Connect items she has on, and whether it's connected. Imported
     * amounts and readings are empty until Health Connect is integrated (M7): nothing is
     * imported yet, so those tiles say "No data yet".
     */
    val activitiesActions: ActivitiesActions = object : ActivitiesActions {
        override val data: Flow<ActivitiesData> = combine(
            activities.observeAll(), checkIns.observeLive(), settings.disabledHealthItemIds,
            users.observeCurrentProfile(), moodPack,
        ) { manual, entries, disabled, profile, pack ->
            val zone = clock.zone()
            ActivitiesData(
                manual = manual, imported = emptyList(), readings = emptyList(),
                feelings = entries.map { DayFeeling(it.date.atZone(zone).toLocalDate(), it.mood, it.energyLevel) },
                disabledItems = disabled, healthConnected = profile?.healthConnected == true, moodPack = pack,
            )
        }
        override val zone: ZoneId get() = clock.zone()
        override fun now(): Instant = clock.now()
        override suspend fun setAmount(activityId: String, day: LocalDate, amount: Double) = activities.setAmount(activityId, day, amount)
        override suspend fun setEating(itemId: String, day: LocalDate, answer: Boolean?) = activities.setEating(itemId, day, answer)
    }

    /**
     * The Stats screen's view of the app (iOS `ReportsView`): her check-ins with their
     * symptoms, her medicines and the days one was recorded as taken, and her sleep.
     * Imported nights and readings are empty until Health Connect is integrated (M7).
     */
    val statsActions: StatsActions = object : StatsActions {
        override val data: Flow<StatsData> = combine(
            checkIns.observeLive(), checkIns.observeLoggedSymptoms(), medications.observeActive(), medications.observeLogs(),
            activities.observeAmounts("sleep"),
        ) { entries, logged, meds, logs, sleep ->
            val zone = clock.zone()
            val names = logged.groupBy({ it.checkInID }, { it.name })
            StatsData(
                entries = entries.map { StatsEntry(it.date.atZone(zone).toLocalDate(), it.mood, it.energyLevel, it.energy, names[it.id].orEmpty()) },
                activeMedicineCount = meds.size,
                anyAutoLogged = meds.any { it.autoLogDoses },
                takenDays = logs.filter { it.taken }.map { it.date.atZone(zone).toLocalDate() },
                manualSleep = sleep, importedSleep = emptyList(), readings = emptyList(),
            )
        }
        override val zone: ZoneId get() = clock.zone()
        override fun now(): Instant = clock.now()
    }

    /** The GP Visit Summary flow's view of the app: it reads her records, never writes. */
    val gpSummaryActions: GPSummaryActions = object : GPSummaryActions {
        override val zone: ZoneId get() = clock.zone()
        override fun now(): Instant = clock.now()
        override suspend fun document(inputs: GPSummaryInputs): GPSummaryDocument = gpSummary.makeDocument(inputs)
        override suspend fun reviewMeds(): List<GPReviewMed> = gpSummary.reviewMeds()
        override suspend fun aboutMe(): GPAboutMe = gpSummary.aboutMe()
    }

    /** The Reminders screen's view of the app. */
    val remindersActions: RemindersActions = object : RemindersActions {
        override val enabledIds = settings.enabledReminderIds
        override val config = settings.reminderConfig
        override val notificationPermissionAsked = settings.notificationPermissionAsked
        override suspend fun markNotificationPermissionAsked() { settings.setNotificationPermissionAsked(true) }

        override suspend fun setEnabled(id: String, on: Boolean) {
            settings.setReminderEnabled(id, on)
            when {
                id == ReminderKinds.MEDICATION -> reminders.refreshMedicationReminders()
                on -> reminders.rescheduleLifestyleReminder(id)
                else -> reminders.cancelLifestyleReminder(id)
            }
        }

        override suspend fun setConfig(config: ReminderConfig) {
            settings.setReminderConfig(config)
            // Any edit to the timing reschedules the reminders she has switched on.
            val enabled = settings.enabledReminderIds.first()
            ReminderKinds.LIFESTYLE.filter { it in enabled }.forEach { reminders.rescheduleLifestyleReminder(it) }
        }
    }

    /** The Settings screen's view of the app. */
    val settingsActions: SettingsActions = object : SettingsActions {
        override val pushNotifications = settings.pushNotifications
        override val haptics = settings.haptics
        override val medicineNamesInReminders = settings.medicineNamesInReminders
        override val notesAlcohol = settings.notesAlcohol
        override val notificationPermissionAsked = settings.notificationPermissionAsked
        override suspend fun markNotificationPermissionAsked() { settings.setNotificationPermissionAsked(true) }
        override suspend fun setPushNotificationsEnabled(on: Boolean) = reminders.setPushNotificationsEnabled(on)
        override suspend fun setHaptics(on: Boolean) { settings.setHaptics(on) }
        override suspend fun setMedicineNamesInReminders(on: Boolean) {
            settings.setMedicineNamesInReminders(on)
            reminders.refreshMedicationReminders()
        }
        override suspend fun setNotesAlcohol(on: Boolean) { settings.setNotesAlcohol(on) }
        override fun eraseAllData() { scope.launch { this@KeelAppController.eraseAllData() } }
    }

    /** Themes, Colour mode and Mood icons. */
    val appearanceActions: AppearanceActions = object : AppearanceActions {
        override val themeId = settings.themeId
        override val colourModeRaw = settings.colourModeRaw
        override val moodPackId = settings.moodPackId
        override suspend fun setThemeId(id: String) { settings.setThemeId(id) }
        override suspend fun setColourMode(mode: ColourMode) { settings.setColourModeRaw(mode.raw) }
        override suspend fun setMoodPackId(id: String) { settings.setMoodPackId(id) }
    }

    /**
     * "Delete all my data" (iOS `eraseAllData`, in its order): every record, then the built-in
     * symptoms re-seeded for the next person, every reminder (armed or showing), her
     * preferences, and the local identity. Without her profile she is back at onboarding.
     * Android also clears the GP summary it last made for sharing, held in Keel's cache.
     */
    suspend fun eraseAllData() {
        records.deleteEverything()
        symptoms.syncBuiltIns()
        reminders.removeEverything() // no reminder naming a deleted medicine fires
        settings.resetToDefaults()
        identity.signOut()
        clearSharedFiles()
        exactTimingOffer.value = false
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
