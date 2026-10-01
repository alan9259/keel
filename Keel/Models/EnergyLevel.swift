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

    /// Warm ramp from the active theme: accent (drained) through copper and sand to sage
    /// (charged). No red (the brand rule is that red competes with rosewood).
    func color(in theme: KeelTheme) -> Color {
        switch self {
        case .drained: theme.accent
        case .low: theme.copper
        case .okay: theme.sand
        case .good: theme.sageSoft
        case .charged: theme.sage
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
