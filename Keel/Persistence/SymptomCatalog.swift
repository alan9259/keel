import Foundation

/// Built-in symptom catalog from `docs/keel-symptom-list-build-spec.md`, kept in
/// sync on launch by `SymptomRepository.syncBuiltIns()`.
///
/// Recognition before education: a small set of common, validating, low
/// sensitivity chips (`isDefault`) shows straight away in the check-in, and
/// everything else sits behind "more", grouped gently. She can add her own to any
/// group.
enum SymptomCatalog {
    struct Entry {
        let category: SymptomCategory
        let name: String
        /// Shown immediately in the check-in, before "more".
        let isDefault: Bool

        init(_ category: SymptomCategory, _ name: String, isDefault: Bool = false) {
            self.category = category
            self.name = name
            self.isDefault = isDefault
        }
    }

    /// Bumped whenever the entries below change, so an existing store picks up
    /// the new set once (see `SymptomRepository.syncBuiltIns()`).
    static let version = 2

    static let builtIn: [Entry] = [
        // Sleep & rest
        .init(.sleep, "Trouble sleeping", isDefault: true),
        .init(.sleep, "Restless sleep"),
        .init(.sleep, "Night sweats", isDefault: true),
        .init(.sleep, "Vivid dreams"),
        .init(.sleep, "Restless legs"),

        // Energy. Note: energy is also captured on the check-in slider. Treat the
        // slider and this chip as related but separate signals, and don't
        // double-count them in correlations.
        .init(.energy, "Fatigue or low energy", isDefault: true),
        .init(.energy, "Afternoon crashes"),

        // Body & temperature
        .init(.body, "Hot flushes", isDefault: true),
        .init(.body, "Palpitations"),
        .init(.body, "Dizziness"),
        .init(.body, "Headache", isDefault: true),
        .init(.body, "Migraine"),
        .init(.body, "Tinnitus (ringing in the ears)"),
        .init(.body, "Pins and needles"),
        .init(.body, "Skin-crawling sensation"),

        // Aches & joints
        .init(.aches, "Joint pain", isDefault: true),
        .init(.aches, "Muscle aches"),
        .init(.aches, "Stiffness"),
        .init(.aches, "Frozen shoulder"),
        .init(.aches, "Foot pain"),

        // Head & thinking
        .init(.cognition, "Brain fog", isDefault: true),
        .init(.cognition, "Memory slips"),
        .init(.cognition, "Word-finding difficulty"),
        .init(.cognition, "Trouble focusing"),

        // Mood & self
        .init(.mood, "Anxious", isDefault: true),
        .init(.mood, "Irritable", isDefault: true),
        .init(.mood, "Low mood", isDefault: true),
        .init(.mood, "Mood swings", isDefault: true),
        .init(.mood, "Not feeling like myself", isDefault: true),
        .init(.mood, "Overwhelmed"),
        .init(.mood, "Flat or numb"),
        .init(.mood, "Tearful"),
        .init(.mood, "Low motivation"),
        .init(.mood, "Loss of confidence"),

        // Skin, hair & eyes
        .init(.skin, "Dry skin"),
        .init(.skin, "Itchy skin"),
        .init(.skin, "Hair thinning"),
        .init(.skin, "Brittle nails"),
        .init(.skin, "Dry eyes"),
        .init(.skin, "Dry mouth"),

        // Tummy & appetite
        .init(.digestion, "Bloating"),
        .init(.digestion, "Nausea"),
        .init(.digestion, "Weight changes"),
        .init(.digestion, "Constipation"),
        .init(.digestion, "Heartburn"),
        .init(.digestion, "Appetite changes"),
        .init(.digestion, "Reduced alcohol tolerance"),

        // Intimacy & bladder (opt-in, sensitive)
        .init(.intimacy, "Vaginal dryness"),
        .init(.intimacy, "Low libido"),
        .init(.intimacy, "Painful sex"),
        .init(.intimacy, "Urinary urgency or frequency"),
        .init(.intimacy, "Recurring UTIs"),
        .init(.intimacy, "Bladder leaks"),
        .init(.intimacy, "Waking to wee"),
    ]

    /// The order the default chips appear in before she has enough history for
    /// the list to adapt to her. Common and validating first.
    static let defaultOrder: [String] = [
        "Hot flushes",
        "Night sweats",
        "Trouble sleeping",
        "Fatigue or low energy",
        "Brain fog",
        "Headache",
        "Joint pain",
        "Anxious",
        "Irritable",
        "Low mood",
        "Mood swings",
        "Not feeling like myself",
    ]

    /// Earlier built-in names mapped to their current wording, so anything she
    /// already logged keeps its history instead of becoming a second chip.
    static let renamed: [String: String] = [
        "Insomnia": "Trouble sleeping",
        "Restless": "Restless sleep",
        "Memory issues": "Memory slips",
        "Difficulty focusing": "Trouble focusing",
        "Sad": "Low mood",
        "Changes in appetite": "Appetite changes",
    ]

    /// Position in `defaultOrder`, for sorting the default chips.
    static func defaultRank(of name: String) -> Int {
        defaultOrder.firstIndex(of: name) ?? defaultOrder.count
    }

    /// Position within a group, so each group reads in the order the spec lays
    /// out (most common first) rather than alphabetically. Her own additions rank
    /// last, then sort by name.
    static func rank(of name: String) -> Int {
        rankByName[name.lowercased()] ?? builtIn.count
    }

    private static let rankByName: [String: Int] = builtIn.enumerated()
        .reduce(into: [String: Int]()) { $0[$1.element.name.lowercased()] = $1.offset }
}
