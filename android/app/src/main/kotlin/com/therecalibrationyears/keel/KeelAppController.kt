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
import com.therecalibrationyears.keel.insights.HealthReading
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
import com.therecalibrationyears.keel.settings.ProfileActions
import com.therecalibrationyears.keel.domain.ProfileForm
import com.therecalibrationyears.keel.settings.RemindersActions
import com.therecalibrationyears.keel.settings.SettingsActions
import com.therecalibrationyears.keel.designsystem.ColourMode
import com.therecalibrationyears.keel.data.RecordEraser
import com.therecalibrationyears.keel.data.AppLockService
import com.therecalibrationyears.keel.domain.LockState
import com.therecalibrationyears.keel.settings.AppLockActions
import com.therecalibrationyears.keel.settings.HealthConnectActions
import com.therecalibrationyears.keel.domain.HealthAvailability
import com.therecalibrationyears.keel.domain.HealthOffer
import kotlinx.coroutines.flow.filterNotNull
import com.therecalibrationyears.keel.data.backup.BackupService
import com.therecalibrationyears.keel.data.backup.KeelBackup
import com.therecalibrationyears.keel.data.backup.UnsupportedBackupVersionException
import com.therecalibrationyears.keel.settings.BackupActions
import com.therecalibrationyears.keel.settings.BackupPreview
import com.therecalibrationyears.keel.settings.RestoreOutcome
import com.therecalibrationyears.keel.settings.RestoreSummary
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
import kotlinx.coroutines.Job
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
    private val backup: BackupService,
    /** The app lock (iOS `env.lock`). */
    val lock: AppLockService,
    /** The app's version and support context, for More and feedback drafts. */
    val appInfo: AppInfo,
    /** Removes files Keel made for sharing (the GP summary PDF in its cache). */
    private val clearSharedFiles: () -> Unit = {},
    /** Health Connect: connecting, the launch offer and the sync (iOS `AppEnvironment`'s health part). */
    val health: KeelHealth,
    /** Opens Health Connect's page for what Keel may read. */
    private val manageHealthAccess: () -> Unit = {},
) : OnboardingActions {

    private var launchWork: Job? = null

    /**
     * Launch work (iOS `bootstrap`, in its order): bring the built-in symptom catalog up to
     * date, clear out imports Keel no longer keeps, re-derive Looking back's pattern cards, carry
     * older medicine entries onto schedules, tidy brand examples out of their names, then lay
     * the reminders out. Android runs this whenever it starts Keel's process, which can be in
     * the background (to show a reminder, after a reboot, for the daily top-up), so logging
     * auto-logged doses and the Health Connect sync wait for [onForeground]: iOS does both at
     * launch, which there is always her opening the app (D22).
     */
    fun bootstrap() {
        // The lock's saved state first: the launch screen waits for it, so nothing shows unlocked.
        scope.launch { lock.load() }
        launchWork = scope.launch {
            symptoms.syncBuiltIns()
            health.purgeDiscontinued()
            insights.refreshDerived()
            medications.migrateLegacySchedules()
            medications.stripExampleBrandsFromNames()
            reminders.refreshReminders()
        }
    }

    /** Waits for [bootstrap]'s work (tests). */
    internal suspend fun awaitLaunchWork() = launchWork?.join()

    /**
     * Keel came to the foreground, the first time too: after the launch work, log today's
     * arrived auto-log doses, top up the medication reminders (laid out a few weeks ahead, so
     * opening the app extends them), and bring in new Health Connect data (only once she has
     * connected, at most every 30 minutes, so it never asks).
     */
    fun onForeground() {
        scope.launch {
            launchWork?.join()
            autoLogTodaysDoses()
            reminders.refreshMedicationReminders()
            syncHealthData()
        }
    }

    /** Bring Health Connect data in (iOS `syncHealthData`): only once connected; `force` skips the 30-minute wait. */
    fun syncHealthData(force: Boolean = false) = health.sync(force)

    /** The launch offer (iOS `checkHealthAccessOnLaunch`): "Connect to Health Connect?" or "Update ...", once each. */
    suspend fun checkHealthAccessOnLaunch(): HealthOffer =
        health.checkAccessOnLaunch(hasOnboarded = users.currentProfile()?.onboardingCompletedAt != null)

    private var healthOfferChecked = false

    /** The launch offer is looked at once per launch (iOS: once per MainView), not again when the screen is recreated. */
    fun claimHealthOfferCheck(): Boolean = !healthOfferChecked.also { healthOfferChecked = true }

    /** She answered the offer (Continue): not made again on this install. */
    suspend fun answerHealthOffer(offer: HealthOffer) = health.answerOffer(offer)

    /** Readings imported from Health Connect (vitals, energy, distance, flights), as Activities and Stats show them. */
    private val importedReadings: Flow<List<HealthReading>> =
        health.imported.observeReadings().map { rows -> rows.map { (row, day) -> HealthReading(row.typeID, day, row.value) } }

    /** The Health Connect screen's view of the app. */
    val healthConnectActions: HealthConnectActions = object : HealthConnectActions {
        override val connected: Flow<Boolean> = users.observeCurrentProfile().map { it?.healthConnected == true }
        override val hasImportedData: Flow<Boolean> = health.hasImportedData
        override val disabledItems: Flow<Set<String>> = settings.disabledHealthItemIds
        override suspend fun availability(): HealthAvailability = health.availability()
        override suspend fun connect(): Boolean = health.connect()
        override fun syncNow() = health.sync(force = true)
        override suspend fun disconnect() = health.disconnect()
        override suspend fun setSyncing(itemId: String, on: Boolean) = health.setSyncing(itemId, on)
        override suspend fun removeImported(): Int = health.removeImported()
        override fun manageAccess() = manageHealthAccess()
    }

    /**
     * A reboot, a clock or time zone change, an update, or the daily top-up: alarms are re-armed
     * from scratch, with Keel likely not open, so nothing is logged (D22).
     */
    suspend fun rearmReminders() = reminders.refreshReminders()

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
     * Everything Home reads, live from the database: her records, and nights imported from
     * Health Connect (her own entry wins for a night she logged).
     */
    val homeData: Flow<HomeData> = combine(
        checkIns.observeLive(), checkIns.observeLoggedSymptoms(), medications.observeActive(), medications.observeLogs(),
        combine(activities.observeAmounts("sleep"), health.imported.observeActivity("sleep"), settings.moodPackId) { sleep, imported, pack ->
            Triple(sleep, imported, pack)
        },
    ) { checkInList, symptoms, meds, logs, (sleep, imported, pack) ->
        HomeData(checkInList, symptoms, meds, logs, manualSleep = sleep, importedSleep = imported, moodPackId = pack)
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
            // Her own entry for the night (editable) comes first; otherwise Health Connect's, read-only (iOS `loadSleep`).
            val manual = activities.manualSleep(request.day)
            val imported = if (manual == null) health.imported.amount("sleep", request.day) else null
            val sleep = CheckInDraft(sleepHours = manual ?: imported, sleepFromHealth = imported != null)
            val entry = request.editingId?.let { checkIns.entry(it) } ?: return sleep
            return sleep.copy(
                energy = entry.energyLevel, notes = entry.notes.orEmpty(), alcohol = entry.alcoholCount,
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
     * The Cycle screen's view of the app: her period days, live, the days imported from Health
     * Connect (shown outlined, never used for an estimate), and the cycle-length finding from
     * the pattern engine, which reads only her own days (iOS reads it from the same records).
     */
    val cycleActions: CycleActions = object : CycleActions {
        override val data: Flow<CycleData> = combine(cycle.observeDays(), checkIns.observeLive(), health.imported.observeFlow()) { days, entries, imported ->
            val zone = clock.zone()
            val today = clock.now().atZone(zone).toLocalDate()
            val finding = PatternEngine.build(entries.map { it.date.atZone(zone).toLocalDate() }, days, today)
                .findings().firstOrNull { it.kind == PatternFinding.Kind.CYCLE_VARIABILITY }
            CycleData(days = days, imported = imported, finding = finding)
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
     * The Activities screen's view of the app (iOS `ActivitiesView`): her own entries, what
     * Health Connect brought in (amounts and readings), her check-ins, the Health Connect items
     * she has on, and whether it's connected.
     */
    val activitiesActions: ActivitiesActions = object : ActivitiesActions {
        override val data: Flow<ActivitiesData> = combine(
            combine(activities.observeAll(), health.imported.observeActivity(), importedReadings) { manual, imported, readings ->
                Triple(manual, imported, readings)
            },
            checkIns.observeLive(), settings.disabledHealthItemIds,
            users.observeCurrentProfile(), moodPack,
        ) { (manual, imported, readings), entries, disabled, profile, pack ->
            val zone = clock.zone()
            ActivitiesData(
                manual = manual, imported = imported, readings = readings,
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
     * symptoms, her medicines and the days one was recorded as taken, her sleep (with nights
     * from Health Connect where she logged none) and the readings Health Connect brought in.
     */
    val statsActions: StatsActions = object : StatsActions {
        override val data: Flow<StatsData> = combine(
            checkIns.observeLive(), checkIns.observeLoggedSymptoms(), medications.observeActive(), medications.observeLogs(),
            combine(activities.observeAmounts("sleep"), health.imported.observeActivity("sleep"), importedReadings) { sleep, imported, readings ->
                Triple(sleep, imported, readings)
            },
        ) { entries, logged, meds, logs, (sleep, importedSleep, readings) ->
            val zone = clock.zone()
            val names = logged.groupBy({ it.checkInID }, { it.name })
            StatsData(
                entries = entries.map { StatsEntry(it.date.atZone(zone).toLocalDate(), it.mood, it.energyLevel, it.energy, names[it.id].orEmpty()) },
                activeMedicineCount = meds.size,
                anyAutoLogged = meds.any { it.autoLogDoses },
                takenDays = logs.filter { it.taken }.map { it.date.atZone(zone).toLocalDate() },
                manualSleep = sleep, importedSleep = importedSleep, readings = readings,
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

    /** The Profile screen's view of the app: her details and background, read and saved together. */
    val profileActions: ProfileActions = object : ProfileActions {
        private fun today() = clock.now().atZone(clock.zone()).toLocalDate()

        override suspend fun load(): ProfileForm {
            val p = users.currentProfile() ?: return ProfileForm()
            return ProfileForm.from(
                p.firstName, p.lastName, p.birthYear, p.mobile, p.email, p.hysterectomy, p.hysterectomyYear,
                p.periodsNotApplicableReason, today(),
            )
        }

        override suspend fun save(form: ProfileForm) {
            val saved = form.saved(today())
            users.updateBasicInfo(saved.firstName, saved.lastName, saved.birthYear, saved.mobile, saved.email)
            users.setHysterectomy(saved.hysterectomy, saved.hysterectomyYear)
            users.setPeriodsNotApplicableReason(saved.periodsNote)
        }
    }

    /** The app lock screens' view of the app. */
    val lockActions: AppLockActions = object : AppLockActions {
        override val state: Flow<LockState> = lock.state.filterNotNull()
        override val biometricsAvailable: Boolean get() = lock.biometricsAvailable
        override fun useBiometrics(state: LockState) = lock.useBiometrics(state)
        override val canRecoverWithScreenLock: Boolean get() = lock.canRecoverWithScreenLock
        override fun now(): Instant = clock.now()
        override suspend fun setPIN(pin: String) = lock.setPIN(pin)
        override suspend fun disable() = lock.disable()
        override suspend fun setBiometricUnlock(on: Boolean) = lock.setBiometricUnlock(on)
        override suspend fun verifyPIN(pin: String) = lock.verifyPIN(pin)
        override fun isLockedOut() = lock.isLockedOut()
        override suspend fun unlockWithBiometrics() = lock.unlockWithBiometrics("Unlock Keel")
        override suspend fun authenticateOwner() = lock.authenticateOwner("Confirm it's you with your screen lock to reset your PIN")
    }

    /** A backup file she chose, kept until she confirms or cancels the restore. */
    private var pendingBackup: KeelBackup? = null

    /** Backup & Restore's view of the app (iOS `BackupRestoreView` actions). */
    val backupActions: BackupActions = object : BackupActions {
        override val zone: ZoneId get() = clock.zone()
        override fun fileName(): String = backup.fileName()
        override suspend fun exportData(): ByteArray = backup.exportData()

        override suspend fun read(bytes: ByteArray): BackupPreview? {
            val b = runCatching { backup.decode(bytes) }.getOrNull() ?: return null
            pendingBackup = b
            return BackupPreview(b.checkIns.size, b.symptoms.size, b.medications.size, b.exportedAt)
        }

        override suspend fun restorePending(): RestoreOutcome {
            val b = pendingBackup ?: return RestoreOutcome.Refused(null)
            return try {
                val s = restoreBackup(b)
                pendingBackup = null
                RestoreOutcome.Done(RestoreSummary(s.checkIns, s.medications, s.cycleEntries))
            } catch (e: UnsupportedBackupVersionException) {
                RestoreOutcome.Refused(e.message)
            }
        }

        override fun discardPending() { pendingBackup = null }
    }

    /**
     * Replace her data with a backup (iOS `performRestore`). She stays onboarded here, even if the
     * archive held no profile. Android also brings older iOS records up to date straight away
     * (iOS does it at the next launch) and lays reminders out for the restored medicines, so
     * none for a replaced one can fire.
     */
    suspend fun restoreBackup(archive: KeelBackup): BackupService.Summary {
        val summary = backup.restore(archive)
        markOnboarded()
        medications.migrateLegacySchedules()
        medications.stripExampleBrandsFromNames()
        reminders.refreshAll(::autoLogTodaysDoses)
        return summary
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
     * symptoms re-seeded for the next person, every reminder (armed or showing), Keel's Health
     * Connect access and sync state, her preferences, and the local identity. Without her profile she is back at onboarding.
     * Android also clears the GP summary it last made for sharing, held in Keel's cache.
     */
    suspend fun eraseAllData() {
        records.deleteEverything()
        symptoms.syncBuiltIns()
        reminders.removeEverything() // no reminder naming a deleted medicine fires
        lock.disable() // the PIN removed, the lock off
        health.revokeAll() // Keel's Health Connect access too (plan D4)
        health.resetSyncState()
        settings.resetToDefaults()
        identity.signOut()
        clearSharedFiles()
        exactTimingOffer.value = false
    }

    /** Everything the first frame depends on has loaded (the launch screen waits for this). */
    val isReady: Boolean get() = hasCompletedOnboarding.value != null && appearance.value != null && lock.state.value != null

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

    /** The onboarding step's Continue (iOS `connectAppleHealth`): Health Connect asks, then the first import. */
    override suspend fun connectHealth(): Boolean = health.connect()
}
