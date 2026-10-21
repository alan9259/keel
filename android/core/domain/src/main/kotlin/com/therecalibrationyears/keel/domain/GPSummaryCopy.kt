package com.therecalibrationyears.keel.domain

/**
 * User-facing strings and the safety guardrails for the GP Visit Summary (port of iOS
 * `GPSummaryCopy`, Keel/Features/GPSummary/GPSummaryCopy.swift). Every provisional label
 * lives here, and the banned-verb list is enforced over Keel-generated text.
 *
 * The summary records what she entered, organised for a clinician. It never analyses,
 * scores or interprets, so Keel-generated text uses only neutral verbs.
 */
object GPSummaryCopy {
    const val featureName = "GP Visit Summary"
    const val titleSubtitle = "Prepared from my Keel records"
    const val prepareEntry = "Prepare for a GP visit"

    // Step prompts (her words, never pre-filled or suggested).
    const val prioritiesPrompt = "What are the one to three things you most want help with today?"
    const val questionsPrompt = "Is there anything you want to make sure you ask before you leave?"
    const val impactOwnAssessment = "My own assessment of impact."

    // "How this is affecting me" (step 3). Multi-select areas, then one overall level.
    // The last option is her own 60-character free text and prints verbatim.
    val impactAreaOptions = listOf(
        "Sleep", "Work or concentration", "Exercise or physical activity",
        "Relationships or intimacy", "Day-to-day activities", "Emotional wellbeing",
    )
    const val impactOtherOption = "Other"
    val impactLevelOptions = listOf("Mild", "Moderate", "Significant")

    // Priority/question limits (spec: 3 lines each; 100 / 120 characters).
    const val maxPriorities = 3
    const val priorityCharLimit = 100
    const val maxQuestions = 3
    const val questionCharLimit = 120
    const val impactOtherCharLimit = 60

    // Section headings.
    const val aboutHeading = "About me"
    const val prioritiesHeading = "What I most want help with today"
    const val symptomsHeading = "Symptoms I logged most often"
    const val impactHeading = "How this is affecting me"
    const val cycleHeading = "Periods and cycle"
    const val mhtHeading = "Hormonal treatment (MHT)"
    const val otherMedsHeading = "Other prescribed medications"
    const val supplementsHeading = "Supplements and non-prescription products"
    const val treatmentChangesHeading = "Changes to my treatment during this period"
    const val sleepEnergyMoodHeading = "Sleep, energy and mood"
    const val questionsHeading = "Questions I want to discuss"

    // Empty states and unavailable notes (neutral, factual).
    const val noSymptoms = "No symptoms were recorded during this period."
    const val noEarlierComparison = "Not enough earlier records to compare."
    const val notEnoughPeriodsForRange = "not enough recorded periods to show a range"
    const val notRecorded = "not recorded"
    const val noneRecorded = "None recorded."

    // Row labels used by the renderer (kept here so the lint covers them).
    const val aboutName = "Name"
    const val aboutAge = "Age"
    const val aboutHysterectomy = "Hysterectomy"
    const val aboutPeriod = "Summary period"
    const val aboutCheckIns = "Check-ins in this period"
    const val cycleLastStart = "Last recorded period start"
    const val cyclePeriodsRecorded = "Periods recorded in this period"
    const val cycleLengths = "Cycle lengths recorded"
    const val cycleFlow = "Flow"
    const val cycleBleeding = "Bleeding or spotting between periods"
    const val cycleNotApplicable = "Periods not applicable"
    const val sleepRowLabel = "Sleep"
    const val sleepRowSuffix = "nights with disrupted sleep logged"
    const val energyRowLabel = "Energy"
    const val moodRowLabel = "Mood"
    const val alcoholRowLabel = "Alcohol"
    const val symptomColumn = "Symptom"
    const val thisPeriodColumn = "This period"
    const val previousPeriodColumn = "Previous period"
    const val treatmentColumn = "Treatment"
    const val mhtDoseColumn = "Dose or use as entered"
    const val mhtChangedColumn = "Started or last changed"
    const val nameColumn = "Name"
    const val doseColumn = "Dose"
    const val doseIfKnownColumn = "Dose if known"
    const val frequencyColumn = "Frequency"
    const val generatedPrefix = "Generated"

    /** Fixed, non-removable footer on every page. */
    const val footer = "This summary contains information recorded by the user in Keel. It is intended to support a healthcare conversation and is not a diagnosis or clinical assessment."

    const val shareWarning = "Once you share this, it is outside Keel. Keep it somewhere you are comfortable with."

    // Safety guardrails

    /**
     * Verbs Keel-generated text must never use (they imply change, cause or judgement). This
     * does not apply to anything she typed, which prints verbatim.
     */
    val bannedVerbs = listOf(
        "improved", "worsened", "increased", "decreased", "responded",
        "caused", "triggered", "indicates", "suggests", "appears",
    )

    /** The neutral verbs Keel-generated text may use. */
    val allowedVerbs = listOf("recorded", "logged", "entered", "noted")

    /** Whether a Keel-generated string uses a banned verb (whole word, case-insensitive). */
    fun containsBannedVerb(text: String): Boolean {
        val lowered = text.lowercase()
        return bannedVerbs.any { verb -> Regex("\\b$verb\\b").containsMatchIn(lowered) }
    }

    /** Every fixed Keel-generated string in this feature, for the lint. */
    val lintableStrings: List<String>
        get() = listOf(
            featureName, titleSubtitle, prepareEntry, prioritiesPrompt, questionsPrompt, impactOwnAssessment,
            aboutHeading, prioritiesHeading, symptomsHeading, impactHeading, cycleHeading,
            mhtHeading, otherMedsHeading, supplementsHeading, treatmentChangesHeading,
            sleepEnergyMoodHeading, questionsHeading, noSymptoms, noEarlierComparison,
            notEnoughPeriodsForRange, notRecorded, footer, shareWarning,
            impactOtherOption, noneRecorded, aboutName, aboutAge, aboutHysterectomy, aboutPeriod, aboutCheckIns,
            cycleLastStart, cyclePeriodsRecorded, cycleLengths, cycleFlow, cycleBleeding,
            cycleNotApplicable, sleepRowLabel, sleepRowSuffix, energyRowLabel, moodRowLabel, alcoholRowLabel,
            symptomColumn, thisPeriodColumn, previousPeriodColumn, treatmentColumn,
            mhtDoseColumn, mhtChangedColumn, nameColumn, doseColumn, doseIfKnownColumn,
            frequencyColumn, generatedPrefix,
        ) + impactAreaOptions + impactLevelOptions
}
