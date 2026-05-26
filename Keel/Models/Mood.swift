import Foundation

/// Visual mood scale using nature imagery (on-brand: "find your even keel"),
/// ordered worst → best, left to right, so it runs the same direction as the
/// energy scale (drained → charged). Not a numbered rating.
enum Mood: String, CaseIterable, Codable, Identifiable {
    case difficult
    case low
    case okay
    case good
    case great

    var id: String { rawValue }

    var label: String {
        switch self {
        case .great: "Great"
        case .good: "Good"
        case .okay: "Okay"
        case .low: "Low"
        case .difficult: "Difficult"
        }
    }

    var subtitle: String {
        switch self {
        case .great: "Feeling really good"
        case .good: "Doing well today"
        case .okay: "Getting through it"
        case .low: "Struggling a bit"
        case .difficult: "Really hard today"
        }
    }

    /// Position on the worst→best scale, 0 (difficult) … 4 (great). Used for
    /// pattern detection (e.g. a premenstrual mood dip), not shown as a number.
    var score: Double {
        switch self {
        case .difficult: 0
        case .low: 1
        case .okay: 2
        case .good: 3
        case .great: 4
        }
    }

    /// Nature emoji: storm, spiral, sunrise, shell, wave.
    var emoji: String {
        switch self {
        case .difficult: "⛈️"
        case .low: "🌀"
        case .okay: "🌅"
        case .good: "🐚"
        case .great: "🌊"
        }
    }
}

extension String {
    /// Drops emoji variation selectors (VS16/VS15). The bundled Twemoji font
    /// renders a stray selector as an extra advancing box that shifts a centred
    /// single-emoji off-centre; the base code point still maps to the colour glyph.
    var glyphOnly: String {
        String(unicodeScalars.filter { $0 != "\u{FE0F}" && $0 != "\u{FE0E}" })
    }
}
