import Foundation

/// Compares how often a symptom turns up on the days she logged a trigger ("yes")
/// against the days she logged she didn't ("no"). Days she never logged are absent
/// from both sets, so they never fake a clean baseline. Pure and day-set based, so
/// it is easy to test and shared by the pattern engine and the GP report.
///
/// It only ever reports co-occurrence, never cause: the copy says things "turned up
/// more on" trigger days, never that the trigger caused them.
enum DietTriggerCorrelation {
    /// One trigger's logged days.
    struct Input: Equatable {
        let label: String       // "Alcohol"
        let yes: Set<Date>      // days she logged having it
        let no: Set<Date>       // days she logged not having it
    }

    struct Result: Equatable {
        let label: String
        let yesHit: Int         // yes-days that also had the symptom
        let yesTotal: Int
        let noHit: Int          // no-days that also had the symptom
        let noTotal: Int
        var yesRate: Double { yesTotal == 0 ? 0 : Double(yesHit) / Double(yesTotal) }
        var noRate: Double { noTotal == 0 ? 0 : Double(noHit) / Double(noTotal) }
        /// How much more often the symptom appears on yes-days (0...1).
        var lift: Double { yesRate - noRate }
    }

    /// The single strongest trigger whose symptom rate is materially higher on
    /// yes-days. Gated so a thin or noisy signal stays silent:
    ///  - at least `minEach` yes-days AND no-days (a real comparison),
    ///  - at least `minYesHits` co-occurrences (not one stray day),
    ///  - a lift of at least `minLift` (a clear difference, not a coin toss).
    static func strongest(
        _ triggers: [Input],
        symptomDays: Set<Date>,
        minEach: Int = 3,
        minYesHits: Int = 2,
        minLift: Double = 0.2
    ) -> Result? {
        var best: Result?
        for trigger in triggers {
            guard trigger.yes.count >= minEach, trigger.no.count >= minEach else { continue }
            let result = Result(
                label: trigger.label,
                yesHit: trigger.yes.filter(symptomDays.contains).count,
                yesTotal: trigger.yes.count,
                noHit: trigger.no.filter(symptomDays.contains).count,
                noTotal: trigger.no.count)
            guard result.yesHit >= minYesHits, result.lift >= minLift else { continue }
            if best == nil || result.lift > best!.lift { best = result }
        }
        return best
    }
}
