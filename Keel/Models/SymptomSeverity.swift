import SwiftUI

/// How strongly a symptom is felt on a given check-in. Chosen by tapping the
/// chip: one tap mild, two moderate, three severe, a fourth clears it. Stored as
/// an Int on `CheckInSymptom.severity`.
enum SymptomSeverity: Int, CaseIterable, Identifiable {
    case mild = 1
    case moderate = 2
    case severe = 3

    var id: Int { rawValue }

    var label: String {
        switch self {
        case .mild: "Mild"
        case .moderate: "Moderate"
        case .severe: "Severe"
        }
    }

    /// A warm ramp from the active theme: sand → copper → accent (rosewood by default).
    /// No red (the brand rule is that red competes with rosewood).
    func color(in theme: KeelTheme) -> Color {
        switch self {
        case .mild: theme.sand
        case .moderate: theme.copper
        case .severe: theme.accent
        }
    }

    /// The level after another tap (0 = unselected). Cycles 0→1→2→3→0.
    static func nextLevel(after level: Int) -> Int {
        level >= 3 ? 0 : level + 1
    }
}
