import SwiftUI

/// Discrete 5-point energy scale (the check-in uses tappable levels, not a
/// slider). Persisted on `CheckIn.energy` as a percentage (`level × 20`), so the
/// existing 0–100 model, averages, and sparkline all keep working.
enum EnergyLevel: Int, CaseIterable, Identifiable {
    case drained = 1
    case low
    case okay
    case good
    case charged

    var id: Int { rawValue }

    var label: String {
        switch self {
        case .drained: "Drained"
        case .low: "Low"
        case .okay: "Okay"
        case .good: "Good"
        case .charged: "Charged"
        }
    }

    var color: Color {
        switch self {
        case .drained: Color(hex: 0xEF4444)
        case .low: Color(hex: 0xF97316)
        case .okay: Color(hex: 0xCA8A04)
        case .good: Color(hex: 0xC8866B)
        case .charged: Color(hex: 0x16A34A)
        }
    }

    /// Stored energy percentage (20…100).
    var percent: Int { rawValue * 20 }

    /// Nearest level for a stored percentage.
    static func from(percent: Int) -> EnergyLevel {
        let level = Int((Double(percent) / 20.0).rounded())
        return EnergyLevel(rawValue: min(max(level, 1), 5)) ?? .okay
    }
}
