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
         notEnoughPeriodsForRange, notRecorded, footer, shareWarning]
    }
}
