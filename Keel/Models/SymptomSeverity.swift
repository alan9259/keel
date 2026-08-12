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

    /// A warm brand ramp: sand → copper → rosewood. No red (the brand rule is that
    /// red competes with rosewood).
    var color: Color {
        switch self {
        case .mild: Color(hex: 0xC4A882)     // warm sand
        case .moderate: Color(hex: 0xB87333) // copper
        case .severe: Color(hex: 0x8C4A45)   // rosewood
        }
    }

    /// The level after another tap (0 = unselected). Cycles 0→1→2→3→0.
    static func nextLevel(after level: Int) -> Int {
        level >= 3 ? 0 : level + 1
    }
}
