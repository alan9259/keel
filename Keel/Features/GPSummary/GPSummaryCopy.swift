import Foundation

/// User-facing strings and the safety guardrails for the GP Visit Summary. Every
/// provisional label lives here (one place to change in the September copy pass), and
/// the banned-verb list is enforced over Keel-generated text before release.
///
/// The summary records what she entered, organised for a clinician. It never analyses,
/// scores or interprets, so Keel-generated text uses only neutral verbs.
enum GPSummaryCopy {
    // Provisional product wording (may change 26 Aug / September copy pass).
    static let featureName = "GP Visit Summary"
    static let prepareEntry = "Prepare for a GP visit"

    // Step prompts (her words, never pre-filled or suggested).
    static let prioritiesPrompt = "What are the one to three things you most want help with today?"
    static let questionsPrompt = "Is there anything you want to make sure you ask before you leave?"
    static let impactOwnAssessment = "My own assessment of impact."

    // "How this is affecting me" (step 4). Multi-select areas, then one overall level.
    // The last option is her own 60-character free text and prints verbatim.
    static let impactAreaOptions = [
        "Sleep", "Work or concentration", "Exercise or physical activity",
        "Relationships or intimacy", "Day-to-day activities", "Emotional wellbeing",
    ]
    static let impactOtherOption = "Other"
    static let impactLevelOptions = ["Mild", "Moderate", "Significant"]

    // Priority/question limits (spec: 3 lines each; 100 / 120 characters).
    static let maxPriorities = 3
    static let priorityCharLimit = 100
    static let maxQuestions = 3
    static let questionCharLimit = 120
    static let impactOtherCharLimit = 60

    // Section headings.
    static let aboutHeading = "About me"
    static let prioritiesHeading = "What I most want help with today"
    static let symptomsHeading = "Symptoms I logged most often"
    static let impactHeading = "How this is affecting me"
    static let cycleHeading = "Periods and cycle"
    static let mhtHeading = "Hormonal treatment (MHT)"
    static let otherMedsHeading = "Other prescribed medications"
    static let supplementsHeading = "Supplements and non-prescription products"
    static let treatmentChangesHeading = "Changes to my treatment during this period"
    static let sleepEnergyMoodHeading = "Sleep, energy and mood"
    static let questionsHeading = "Questions I want to discuss"

    // Empty states and unavailable notes (neutral, factual).
    static let noSymptoms = "No symptoms were recorded during this period."
    static let noEarlierComparison = "Not enough earlier records to compare."
    static let notEnoughPeriodsForRange = "not enough recorded periods to show a range"
    static let notRecorded = "not recorded"
    static let noneRecorded = "None recorded."

    // Row labels used by the renderer (kept here so the release lint covers them).
    static let aboutName = "Name"
    static let aboutAge = "Age"
    static let aboutPeriod = "Summary period"
    static let aboutCheckIns = "Check-ins in this period"
    static let cycleLastStart = "Last recorded period start"
    static let cyclePeriodsRecorded = "Periods recorded in this period"
    static let cycleLengths = "Cycle lengths recorded"
    static let cycleFlow = "Flow"
    static let cycleBleeding = "Bleeding or spotting between periods"
    static let cycleNotApplicable = "Periods not applicable"
    static let sleepRowLabel = "Sleep"
    static let sleepRowSuffix = "nights with disrupted sleep logged"
    static let energyRowLabel = "Energy"
    static let moodRowLabel = "Mood"
    static let symptomColumn = "Symptom"
    static let thisPeriodColumn = "This period"
    static let previousPeriodColumn = "Previous period"
    static let treatmentColumn = "Treatment"
    static let mhtDoseColumn = "Dose or use as entered"
    static let mhtChangedColumn = "Started or last changed"
    static let nameColumn = "Name"
    static let doseColumn = "Dose"
    static let doseIfKnownColumn = "Dose if known"
    static let frequencyColumn = "Frequency"
    static let generatedPrefix = "Generated"

    /// Fixed, non-removable footer on every page.
    static let footer = "This summary contains information recorded by the user in Keel. It is intended to support a healthcare conversation and is not a diagnosis or clinical assessment."

    static let shareWarning = "Once you share this, it is outside Keel. Keep it somewhere you are comfortable with."

    // MARK: Safety guardrails

    /// Verbs Keel-generated text must never use (they imply change, cause or judgement).
    /// This does NOT apply to anything she typed, which prints verbatim.
    static let bannedVerbs: [String] = [
        "improved", "worsened", "increased", "decreased", "responded",
        "caused", "triggered", "indicates", "suggests", "appears",
    ]

    /// The neutral verbs Keel-generated text may use.
    static let allowedVerbs: [String] = ["recorded", "logged", "entered", "noted"]

    /// Whether a Keel-generated string uses a banned verb (whole-word, case-insensitive).
    /// Used by the release lint over the summary's generated strings.
    static func containsBannedVerb(_ text: String) -> Bool {
        let lowered = text.lowercased()
        return bannedVerbs.contains { verb in
            // Whole-word match so "recorded" isn't caught by "corded" etc.
            lowered.range(of: "\\b\(verb)\\b", options: .regularExpression) != nil
        }
    }

    /// Every fixed Keel-generated string in this feature, for the release lint.
    static var lintableStrings: [String] {
        [featureName, prepareEntry, prioritiesPrompt, questionsPrompt, impactOwnAssessment,
         aboutHeading, prioritiesHeading, symptomsHeading, impactHeading, cycleHeading,
         mhtHeading, otherMedsHeading, supplementsHeading, treatmentChangesHeading,
         sleepEnergyMoodHeading, questionsHeading, noSymptoms, noEarlierComparison,
         notEnoughPeriodsForRange, notRecorded, footer, shareWarning,
         impactOtherOption, noneRecorded, aboutName, aboutAge, aboutPeriod, aboutCheckIns,
         cycleLastStart, cyclePeriodsRecorded, cycleLengths, cycleFlow, cycleBleeding,
         cycleNotApplicable, sleepRowLabel, sleepRowSuffix, energyRowLabel, moodRowLabel,
         symptomColumn, thisPeriodColumn, previousPeriodColumn, treatmentColumn,
         mhtDoseColumn, mhtChangedColumn, nameColumn, doseColumn, doseIfKnownColumn,
         frequencyColumn, generatedPrefix] + impactAreaOptions + impactLevelOptions
    }
}
