import Foundation

/// Her answer to "Have you had a hysterectomy?" (submission pack 3C). Recorded for her
/// own background and, only if she turns it on, her GP summary. Keel records the answer
/// and changes nothing because of it: no feature, insight or screen depends on it.
enum Hysterectomy: String, CaseIterable, Identifiable {
    case no
    case yesOvariesKept
    case yesOneOvaryRemoved
    case yesBothOvariesRemoved
    case yesNotSureAboutOvaries
    case ratherNotSay

    var id: String { rawValue }

    static let question = "Have you had a hysterectomy?"

    /// The option as she sees it.
    var label: String {
        switch self {
        case .no: "No"
        case .yesOvariesKept: "Yes, both ovaries were kept"
        case .yesOneOvaryRemoved: "Yes, one ovary was removed"
        case .yesBothOvariesRemoved: "Yes, both ovaries were removed"
        case .yesNotSureAboutOvaries: "Yes, I'm not sure about my ovaries"
        case .ratherNotSay: "I'd rather not say"
        }
    }

    /// A year only makes sense for a "yes".
    var allowsYear: Bool {
        switch self {
        case .yesOvariesKept, .yesOneOvaryRemoved, .yesBothOvariesRemoved, .yesNotSureAboutOvaries: true
        case .no, .ratherNotSay: false
        }
    }

    /// The GP summary wording, matching her answer. Nil for "I'd rather not say", which
    /// never appears on the summary.
    var summaryText: String? {
        switch self {
        case .no: "no"
        case .yesOvariesKept: "yes, both ovaries kept"
        case .yesOneOvaryRemoved: "yes, one ovary removed"
        case .yesBothOvariesRemoved: "yes, both ovaries removed"
        case .yesNotSureAboutOvaries: "yes, not sure about ovaries"
        case .ratherNotSay: nil
        }
    }

    /// "yes, one ovary removed, 2019 (as recorded by her)". Pure so it's testable.
    static func summaryLine(_ answer: Hysterectomy?, year: Int?) -> String? {
        guard let answer, let text = answer.summaryText else { return nil }
        let withYear = (answer.allowsYear ? year : nil).map { "\(text), \($0)" } ?? text
        return "\(withYear) (as recorded by her)"
    }

    /// A plausible year she might enter, or nil (blank, partial or nonsense).
    static func validYear(_ text: String, now: Date = .now, calendar: Calendar = .current) -> Int? {
        guard let year = Int(text.trimmingCharacters(in: .whitespaces)) else { return nil }
        let current = calendar.component(.year, from: now)
        return (1900...current).contains(year) ? year : nil
    }
}
