import SwiftUI

/// How strongly a symptom is felt on a given check-in. Chosen by tapping the
/// chip: one tap mild (yellow), two moderate (red), three severe (purple), a
/// fourth clears it. Stored as an Int on `CheckInSymptom.severity`.
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

    var color: Color {
        switch self {
        case .mild: Color(hex: 0xE0A81C)     // yellow
        case .moderate: Color(hex: 0xDC2626) // red
        case .severe: Color(hex: 0x7C3AED)   // purple
        }
    }

    /// The level after another tap (0 = unselected). Cycles 0→1→2→3→0.
    static func nextLevel(after level: Int) -> Int {
        level >= 3 ? 0 : level + 1
    }
}
