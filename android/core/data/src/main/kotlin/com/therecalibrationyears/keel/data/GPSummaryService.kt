package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.CheckInEntity
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.data.db.SymptomLog
import com.therecalibrationyears.keel.data.db.UserProfileEntity
import com.therecalibrationyears.keel.domain.CycleEntryType
import com.therecalibrationyears.keel.domain.GPAboutMe
import com.therecalibrationyears.keel.domain.CycleStats
import com.therecalibrationyears.keel.domain.FlowLevel
import com.therecalibrationyears.keel.domain.GPCycleBlock
import com.therecalibrationyears.keel.domain.GPDateWindow
import com.therecalibrationyears.keel.domain.GPMedCategory
import com.therecalibrationyears.keel.domain.GPMedInput
import com.therecalibrationyears.keel.domain.GPReviewMed
import com.therecalibrationyears.keel.domain.GPSummaryBuilder
import com.therecalibrationyears.keel.domain.GPSummaryCopy
import com.therecalibrationyears.keel.domain.GPSummaryDocument
import com.therecalibrationyears.keel.domain.GPSummaryInputs
import com.therecalibrationyears.keel.domain.GPSummarySection
import com.therecalibrationyears.keel.domain.GPSymptomStat
import com.therecalibrationyears.keel.domain.GPTreatmentChange
import com.therecalibrationyears.keel.domain.Hysterectomy
import com.therecalibrationyears.keel.domain.KeelClock
import com.therecalibrationyears.keel.domain.Mood
import com.therecalibrationyears.keel.domain.ScheduleWords
import com.therecalibrationyears.keel.domain.TreatmentKind
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reads her records for the chosen period and folds them, through the pure
 * `GPSummaryBuilder`, into a `GPSummaryDocument` (port of iOS `GPSummaryService`). This layer
 * only retrieves and counts; every rule that could be interpreted lives in the tested
 * builder. It never writes, and never invents a value: a date she didn't enter stays null, a
 * missing section reads as its honest empty state.
 *
 * Stopped treatments she dated are kept: the MHT table shows them with "Stopped [date]" and
 * the changes list restates the stop. Dates are only ever the ones she entered.
 */
class GPSummaryService(
    private val checkIns: CheckInRepository,
    private val medications: MedicationRepository,
    private val cycle: CycleRepository,
    private val users: UserRepository,
    private val clock: KeelClock,
    /** Her locale's words for a medicine's schedule ("Every day at 8:00 am"). */
    private val words: () -> ScheduleWords,
) {
    private fun dayOf(instant: Instant): LocalDate = instant.atZone(clock.zone()).toLocalDate()

    suspend fun makeDocument(inputs: GPSummaryInputs, now: Instant = clock.now()): GPSummaryDocument {
        val today = dayOf(now)
        val window = inputs.period.window(today)
        val all = checkIns.all()
        val thisCheckIns = all.filter { window.contains(dayOf(it.date)) }
        val prevCheckIns = all.filter { window.previousContains(dayOf(it.date)) }
        val checkInDaysThis = distinctDays(thisCheckIns).size
        val checkInDaysPrev = distinctDays(prevCheckIns).size

        // Symptoms.
        val logs = checkIns.symptomLogs().groupBy { it.checkInID }
        val stats = symptomStats(thisCheckIns, prevCheckIns, logs, inputs.removedSymptomNames)

        // Sleep: nights she logged a sleep-disruption symptom, of nights with a check-in.
        val disruptedNights = thisCheckIns
            .filter { ci -> logs[ci.id].orEmpty().any { GPSummaryBuilder.isSleepDisruptionSymptom(it.name) } }
            .map { dayOf(it.date) }.toSet().size
        val sleepLine = GPSummaryBuilder.sleepLine(disruptedNights, checkInDaysThis)

        // Energy: her level out of 10 (a faithful rescale of 20..100), only where she picked one.
        val energies = thisCheckIns.filter { it.energyLevel != null }.map { it.energy / 10.0 }
        val energyLine = GPSummaryBuilder.energyLine(if (energies.isEmpty()) 0.0 else energies.average(), energies.size)

        // Mood: the three most-logged labels, count then better mood as the tie-break.
        val moodCounts = thisCheckIns.groupBy { it.mood }.toList()
            .sortedWith(compareByDescending<Pair<Mood, List<CheckInEntity>>> { it.second.size }.thenByDescending { it.first.score })
            .map { (mood, entries) -> mood.label to entries.size }
        val moodLine = GPSummaryBuilder.moodLine(moodCounts)

        // Alcohol: only days with a saved number count (zero included); blank is never 0.
        val alcoholDays = distinctDays(thisCheckIns.filter { it.alcoholCount != null }).size
        val alcoholLine = GPSummaryBuilder.alcoholLine(alcoholDays, checkInDaysThis)

        val cycleBlock = makeCycleBlock(window)

        // Treatment. Active medicines fill the three tables; treatments she stopped during
        // the window are kept too, so the MHT table can show them with "Stopped [date]" and
        // the changes list can restate the stop.
        val active = medications.active().filter { it.id !in inputs.removedMedIDs }
        val stoppedInWindow = medications.stoppedTreatments().filter { med ->
            val stopped = med.stoppedAt ?: return@filter false
            window.contains(dayOf(stopped)) && med.id !in inputs.removedMedIDs
        }
        // Only MHT includes stopped items as rows (spec); a stopped other or supplement still
        // contributes a dated change but not a table row.
        val medInputs = (active + stoppedInWindow.filter { category(it) == GPMedCategory.MHT }).map(::medInput)
        val mht = GPSummaryBuilder.medTable(medInputs, GPMedCategory.MHT, dateStyle = ::dateStyle)
        val other = GPSummaryBuilder.medTable(medInputs, GPMedCategory.OTHER_PRESCRIBED, dateStyle = ::dateStyle)
        val supplements = GPSummaryBuilder.medTable(medInputs, GPMedCategory.SUPPLEMENT, dateStyle = ::dateStyle)

        // Changes: dose changes and stops she dated within the window, most recent first.
        val changeEvents = mutableListOf<GPTreatmentChange>()
        for (med in active + stoppedInWindow) {
            val changed = med.doseChangedAt ?: continue
            if (window.contains(dayOf(changed))) changeEvents += GPTreatmentChange(changed, med.name, GPTreatmentChange.Kind.DOSE_CHANGED)
        }
        for (med in stoppedInWindow) {
            med.stoppedAt?.let { changeEvents += GPTreatmentChange(it, med.name, GPTreatmentChange.Kind.STOPPED) }
        }
        val treatmentChanges = GPSummaryBuilder.treatmentChanges(changeEvents, dateStyle = ::dateStyle)

        // Step 3 inputs (her words). Fixed labels lower-cased; her "other" text verbatim.
        val renderedAreas = inputs.impactAreas.map { it.lowercase() }.toMutableList()
        val otherText = inputs.impactOther.trim()
        if (otherText.isNotEmpty()) renderedAreas += otherText
        val impactLine = GPSummaryBuilder.impactLine(renderedAreas, inputs.impactOverall)
        val priorities = cleanLines(inputs.priorities, GPSummaryCopy.maxPriorities)
        val questions = cleanLines(inputs.questions, GPSummaryCopy.maxQuestions)

        // About me (all off by default).
        val profile = users.currentProfile()
        val name = if (inputs.includeName) profile?.recordedName else null
        val age = if (inputs.includeAge) profile?.age(today) else null
        val hysterectomyLine = if (inputs.includeHysterectomy) Hysterectomy.summaryLine(profile?.hysterectomy, profile?.hysterectomyYear) else null

        return GPSummaryDocument(
            name = name,
            age = age,
            hysterectomyLine = hysterectomyLine,
            periodLabel = periodLabel(window),
            checkInsLabel = "$checkInDaysThis of ${window.dayCount} ${if (window.dayCount == 1) "day" else "days"}",
            priorities = priorities,
            symptomStats = stats,
            checkInDaysThisPeriod = checkInDaysThis,
            checkInDaysPreviousPeriod = checkInDaysPrev,
            previousWindowDayCount = window.dayCount,
            defaultSymptomMaxRows = if (priorities.size >= GPSummaryCopy.maxPriorities) 5 else 6,
            impactLine = impactLine,
            cycle = cycleBlock,
            mht = mht,
            otherMeds = other,
            supplements = supplements,
            treatmentChanges = treatmentChanges,
            sleepLine = sleepLine,
            energyLine = energyLine,
            moodLine = moodLine,
            alcoholLine = alcoholLine,
            questions = questions,
            generatedOn = today,
            includeCycle = GPSummarySection.CYCLE !in inputs.removedSections,
            includeSleep = GPSummarySection.SLEEP !in inputs.removedSections,
            includeEnergy = GPSummarySection.ENERGY !in inputs.removedSections,
            includeMood = GPSummarySection.MOOD !in inputs.removedSections,
            includeAlcohol = GPSummarySection.ALCOHOL !in inputs.removedSections,
        )
    }

    // Symptoms

    private fun distinctDays(entries: List<CheckInEntity>): Set<LocalDate> = entries.map { dayOf(it.date) }.toSet()

    private class SymptomAcc(val isCustom: Boolean, var last: Instant) {
        val days = mutableSetOf<LocalDate>()
        var severitySum = 0
        var severityCount = 0
        val previousDays = mutableSetOf<LocalDate>()
    }

    private fun symptomStats(
        thisPeriod: List<CheckInEntity>,
        previous: List<CheckInEntity>,
        logs: Map<String, List<SymptomLog>>,
        removed: Set<String>,
    ): List<GPSymptomStat> {
        val acc = linkedMapOf<String, SymptomAcc>()
        for (ci in thisPeriod) {
            val day = dayOf(ci.date)
            for (log in logs[ci.id].orEmpty()) {
                val entry = acc.getOrPut(log.name) { SymptomAcc(log.isCustom, ci.date) }
                entry.days += day
                entry.severitySum += log.severity
                entry.severityCount += 1
                entry.last = maxOf(entry.last, ci.date)
            }
        }
        for (ci in previous) {
            val day = dayOf(ci.date)
            for (log in logs[ci.id].orEmpty()) {
                acc.getOrPut(log.name) { SymptomAcc(log.isCustom, ci.date) }.previousDays += day
            }
        }
        return acc.filterKeys { it !in removed }.map { (name, e) ->
            GPSymptomStat(
                name = name,
                isCustom = e.isCustom,
                daysThisPeriod = e.days.size,
                daysPreviousPeriod = e.previousDays.size,
                meanSeverity = if (e.severityCount > 0) e.severitySum.toDouble() / e.severityCount else 0.0,
                lastLogged = e.last,
            )
        }
    }

    // Cycle

    private suspend fun makeCycleBlock(window: GPDateWindow): GPCycleBlock {
        val entries = cycle.entries(window.start, window.end).filter { it.type != CycleEntryType.PERIOD_END }
        val menstruationDays = entries.filter { it.flowLevel != FlowLevel.SPOTTING }.map { dayOf(it.date) }
        val spottingDays = entries.filter { it.flowLevel == FlowLevel.SPOTTING }.map { dayOf(it.date) }
        val startsInWindow = CycleStats.periodStarts(menstruationDays)

        // Most-recorded flow descriptor, in her words (the level she chose); ties go to the
        // label first in alphabetical order, as on iOS.
        val mostFrequentFlow = entries.groupingBy { it.flowLevel.label }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key

        return GPSummaryBuilder.cycleBlock(
            periodStartsInPeriod = startsInWindow,
            lastRecordedStart = cycle.stats(today = window.end).lastStart,
            mostFrequentFlow = mostFrequentFlow,
            intermenstrualBleeding = GPSummaryBuilder.interPeriodBleeding(menstruationDays, spottingDays),
            notApplicableReason = users.currentProfile()?.periodsNotApplicableReason,
        )
    }

    // Review

    /** The active treatments as removable rows for the review step. */
    suspend fun reviewMeds(): List<GPReviewMed> = medications.active().map { GPReviewMed(it.id, it.name, category(it)) }

    /** Which "About me" opt-ins to offer: only what she has recorded (never "rather not say"). */
    suspend fun aboutMe(): GPAboutMe {
        val profile = users.currentProfile()
        return GPAboutMe(
            hasName = profile?.recordedName != null,
            hasAge = profile?.birthYear != null,
            hasHysterectomyAnswer = Hysterectomy.summaryLine(profile?.hysterectomy, profile?.hysterectomyYear) != null,
        )
    }

    /**
     * The first name she gave, if any. "there" is the stand-in for a name she left blank
     * (as iOS's Profile screen treats it), so it never prints as her name (plan D26; iOS
     * offers and prints it).
     */
    private val UserProfileEntity.recordedName: String?
        get() = firstName.ifEmpty { null }?.takeIf { it != UserRepository.DEFAULT_FIRST_NAME }

    // Medicines

    private fun category(med: MedicationEntity): GPMedCategory = when {
        med.kind == TreatmentKind.SUPPLEMENT -> GPMedCategory.SUPPLEMENT
        med.catalogGroupID in GPSummaryBuilder.mhtGroupIDs -> GPMedCategory.MHT
        else -> GPMedCategory.OTHER_PRESCRIBED
    }

    private fun medInput(med: MedicationEntity) = GPMedInput(
        name = med.name,
        dose = med.dosage.ifEmpty { null },
        frequency = (if (med.hasSchedule) med.schedule(clock.zone()).summary(words()) else med.timing).ifEmpty { null },
        started = med.date,
        doseChangedAt = med.doseChangedAt,
        stoppedAt = med.stoppedAt,
        category = category(med),
    )

    // Formatting: a fixed English (AU) locale so month names are always spelled out and the
    // PDF reads the same on any phone; her zone keeps a day on its day.

    private fun periodLabel(window: GPDateWindow): String {
        val sameYear = window.start.year == window.end.year
        val start = (if (sameYear) DAY_MONTH else DAY_MONTH_YEAR).format(window.start)
        return "$start to ${DAY_MONTH_YEAR.format(window.end)}"
    }

    private fun dateStyle(instant: Instant): String = DAY_MONTH_YEAR.format(dayOf(instant))

    /** Trim, drop blanks, cap the count, keep her order. Never reorders or ranks. */
    private fun cleanLines(lines: List<String>, max: Int): List<String> = lines.map { it.trim() }.filter { it.isNotEmpty() }.take(max)

    companion object {
        private val EN_AU: Locale = Locale.forLanguageTag("en-AU")
        val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", EN_AU) // not-copy: date pattern
        val DAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", EN_AU) // not-copy: date pattern
    }
}
