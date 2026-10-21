package com.therecalibrationyears.keel.gpsummary

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import com.therecalibrationyears.keel.domain.GPAboutMe
import com.therecalibrationyears.keel.domain.GPPeriod
import com.therecalibrationyears.keel.domain.GPReviewMed
import com.therecalibrationyears.keel.domain.GPSummaryBuilder
import com.therecalibrationyears.keel.domain.GPSummaryCopy
import com.therecalibrationyears.keel.domain.GPSummaryDocument
import com.therecalibrationyears.keel.domain.GPSummaryInputs
import com.therecalibrationyears.keel.domain.GPSummarySection
import com.therecalibrationyears.keel.domain.GPSymptomStat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The four steps, in order (iOS `GPSummaryFlowModel.Step`). */
enum class GPSummaryStep { PERIOD, REVIEW, DETAILS, PREVIEW }

/** What the GP Visit Summary flow reads from the app; it never touches storage itself. */
interface GPSummaryActions {
    val zone: ZoneId
    fun now(): Instant

    /** The summary resolved from her records for these inputs. */
    suspend fun document(inputs: GPSummaryInputs): GPSummaryDocument

    /** Her active treatments, as removable rows. */
    suspend fun reviewMeds(): List<GPReviewMed>

    /** Which "About me" opt-ins she has something recorded for. */
    suspend fun aboutMe(): GPAboutMe
}

/**
 * Everything she has chosen and typed in the flow (the state of iOS `GPSummaryFlowModel`),
 * as one value so it survives the screen being recreated. The rules are pure functions
 * returning a changed copy; the service and builder do the rest.
 */
@Immutable
data class GPSummaryDraft(
    val step: GPSummaryStep = GPSummaryStep.PERIOD,
    val inputs: GPSummaryInputs = GPSummaryInputs(),
    /** Custom-range dates, only used when the period is custom. */
    val customStart: LocalDate,
    val customEnd: LocalDate,
    /** Three fixed slots each; blanks are dropped and the list capped when built. */
    val priorityDrafts: List<String> = List(GPSummaryCopy.maxPriorities) { "" },
    val questionDrafts: List<String> = List(GPSummaryCopy.maxQuestions) { "" },
) {
    // Period

    val isCustom: Boolean get() = inputs.period is GPPeriod.Custom
    fun selectPreset(period: GPPeriod) = copy(inputs = inputs.copy(period = period))

    /** Use her two dates (iOS `setCustomRange`); the date pickers keep start ≤ end ≤ today. */
    fun setCustomRange(start: LocalDate = customStart, end: LocalDate = customEnd) =
        copy(customStart = start, customEnd = end, inputs = inputs.copy(period = GPPeriod.Custom(start, end)))

    // Review toggles

    fun isSymptomIncluded(name: String) = name !in inputs.removedSymptomNames
    fun toggleSymptom(name: String) = copy(inputs = inputs.copy(removedSymptomNames = inputs.removedSymptomNames.toggled(name)))

    fun isMedIncluded(id: String) = id !in inputs.removedMedIDs
    fun toggleMed(id: String) = copy(inputs = inputs.copy(removedMedIDs = inputs.removedMedIDs.toggled(id)))

    fun isSectionIncluded(section: GPSummarySection) = section !in inputs.removedSections
    fun toggleSection(section: GPSummarySection) = copy(inputs = inputs.copy(removedSections = inputs.removedSections.toggled(section)))

    // Details: her words, held to the spec's lengths

    fun isAreaSelected(area: String) = area in inputs.impactAreas
    fun toggleArea(area: String) = copy(
        inputs = inputs.copy(impactAreas = if (area in inputs.impactAreas) inputs.impactAreas - area else inputs.impactAreas + area),
    )

    /** One overall level, or none: tapping the chosen one again clears it. */
    fun toggleOverall(level: String) = copy(inputs = inputs.copy(impactOverall = if (inputs.impactOverall == level) null else level))

    fun setImpactOther(text: String) = copy(inputs = inputs.copy(impactOther = text.take(GPSummaryCopy.impactOtherCharLimit)))
    fun setPriority(index: Int, text: String) =
        copy(priorityDrafts = priorityDrafts.toMutableList().also { it[index] = text.take(GPSummaryCopy.priorityCharLimit) })
    fun setQuestion(index: Int, text: String) =
        copy(questionDrafts = questionDrafts.toMutableList().also { it[index] = text.take(GPSummaryCopy.questionCharLimit) })

    fun setIncludeName(on: Boolean) = copy(inputs = inputs.copy(includeName = on))
    fun setIncludeAge(on: Boolean) = copy(inputs = inputs.copy(includeAge = on))
    fun setIncludeHysterectomy(on: Boolean) = copy(inputs = inputs.copy(includeHysterectomy = on))

    /** The inputs with her typed lines committed (iOS `document()`). */
    val committedInputs: GPSummaryInputs get() = inputs.copy(priorities = priorityDrafts, questions = questionDrafts)

    // Navigation

    val isFirst: Boolean get() = step == GPSummaryStep.PERIOD
    val isLast: Boolean get() = step == GPSummaryStep.PREVIEW

    /** Home leaves the whole flow; past the first step she has made choices worth keeping, so ask first. */
    val homeNeedsConfirmation: Boolean get() = homeNeedsConfirmation(step)

    fun next() = copy(step = GPSummaryStep.entries.getOrElse(step.ordinal + 1) { step })
    fun back() = copy(step = GPSummaryStep.entries.getOrElse(step.ordinal - 1) { step })

    companion object {
        fun homeNeedsConfirmation(step: GPSummaryStep) = step != GPSummaryStep.PERIOD

        /** A fresh flow: twelve weeks, and a custom range that starts on the same twelve weeks. */
        fun start(today: LocalDate, step: GPSummaryStep = GPSummaryStep.PERIOD) =
            GPSummaryDraft(step = step, customStart = today.minusDays(83), customEnd = today)

        private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

        private const val PRESET_PREFIX = "preset:" // not-copy: saved-state key
        private const val CUSTOM = "custom" // not-copy: saved-state key

        private fun encode(period: GPPeriod): String = when (period) {
            is GPPeriod.Custom -> CUSTOM
            else -> PRESET_PREFIX + GPPeriod.presets.indexOf(period)
        }

        /** Saved as plain values, so it survives the activity being recreated or the process restarting. */
        val Saver: Saver<GPSummaryDraft, Any> = mapSaver(
            save = { d ->
                mapOf(
                    "step" to d.step.name,
                    "period" to encode(d.inputs.period),
                    "customStart" to d.customStart.toEpochDay(),
                    "customEnd" to d.customEnd.toEpochDay(),
                    "includeName" to d.inputs.includeName,
                    "includeAge" to d.inputs.includeAge,
                    "includeHysterectomy" to d.inputs.includeHysterectomy,
                    "impactAreas" to ArrayList(d.inputs.impactAreas),
                    "impactOther" to d.inputs.impactOther,
                    "impactOverall" to d.inputs.impactOverall,
                    "removedSymptoms" to ArrayList(d.inputs.removedSymptomNames),
                    "removedMeds" to ArrayList(d.inputs.removedMedIDs),
                    "removedSections" to ArrayList(d.inputs.removedSections.map { it.name }),
                    "priorities" to ArrayList(d.priorityDrafts),
                    "questions" to ArrayList(d.questionDrafts),
                )
            },
            restore = { m ->
                @Suppress("UNCHECKED_CAST")
                fun strings(key: String) = (m[key] as? List<String>).orEmpty()
                val customStart = LocalDate.ofEpochDay(m["customStart"] as Long)
                val customEnd = LocalDate.ofEpochDay(m["customEnd"] as Long)
                val periodKey = m["period"] as String
                val period = if (periodKey == CUSTOM) GPPeriod.Custom(customStart, customEnd)
                else GPPeriod.presets.getOrElse(periodKey.removePrefix(PRESET_PREFIX).toIntOrNull() ?: -1) { GPPeriod.default }
                GPSummaryDraft(
                    step = GPSummaryStep.valueOf(m["step"] as String),
                    inputs = GPSummaryInputs(
                        period = period,
                        includeName = m["includeName"] as Boolean,
                        includeAge = m["includeAge"] as Boolean,
                        includeHysterectomy = m["includeHysterectomy"] as Boolean,
                        impactAreas = strings("impactAreas"),
                        impactOther = m["impactOther"] as String,
                        impactOverall = m["impactOverall"] as String?,
                        removedSymptomNames = strings("removedSymptoms").toSet(),
                        removedMedIDs = strings("removedMeds").toSet(),
                        removedSections = strings("removedSections").map(GPSummarySection::valueOf).toSet(),
                    ),
                    customStart = customStart,
                    customEnd = customEnd,
                    priorityDrafts = strings("priorities"),
                    questionDrafts = strings("questions"),
                )
            },
        )
    }
}

/**
 * What the chosen period found, before any removal, for the review step (iOS
 * `candidateSymptoms`, `candidateCheckInDays`, `reviewMeds`, `hasAlcoholRecords`).
 */
@Immutable
data class GPSummaryCandidates(
    val symptoms: List<GPSymptomStat> = emptyList(),
    /** Check-in days in the chosen window, for the same "X of Y check-in days" the PDF prints. */
    val checkInDays: Int = 0,
    val meds: List<GPReviewMed> = emptyList(),
    /** Whether she recorded any alcohol in the period (offers the Alcohol row). */
    val hasAlcoholRecords: Boolean = false,
) {
    companion object {
        /**
         * From the period's summary before removals. Only symptoms recorded in the period are
         * listed: iOS also lists ones logged only in the window before, at "0 of N check-in
         * days", which never print (plan D26).
         */
        fun from(document: GPSummaryDocument, meds: List<GPReviewMed>) = GPSummaryCandidates(
            symptoms = GPSummaryBuilder.ranked(document.symptomStats).filter { it.daysThisPeriod > 0 },
            checkInDays = document.checkInDaysThisPeriod,
            meds = meds,
            hasAlcoholRecords = document.alcoholLine != null,
        )

        /** The inputs the candidates are read with: the chosen period, nothing removed yet. */
        fun baseInputs(inputs: GPSummaryInputs) = inputs.copy(removedSymptomNames = emptySet(), removedMedIDs = emptySet())
    }
}
