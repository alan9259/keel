import Foundation

/// Merges symptom "days reported" from her check-ins AND Apple Health's own symptom
/// logs (which Keel archives as `symptom.*` `HealthSample` rows on days she didn't
/// check in), so a symptom she recorded only in Health isn't invisible in her
/// reports or patterns. Pure and day-set based: the same symptom on the same day
/// from both sources counts once (set union), never twice.
struct SymptomTally {
    /// Distinct days a symptom was reported, keyed by canonical (sentence-case) name.
    private(set) var daysByName: [String: Set<Date>] = [:]

    /// The vasomotor symptoms clinicians weigh most (frequency drives treatment
    /// decisions). Names match the Keel catalog / Apple Health mapping.
    static let vasomotorNames: Set<String> = ["Hot flushes", "Night sweats"]

    /// Record one reported day for a symptom. `day` should already be start-of-day.
    mutating func add(name: String, day: Date) {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        daysByName[trimmed, default: []].insert(day)
    }

    /// Symptoms by distinct days reported, most days first (ties broken by name so the
    /// order is stable across launches).
    func ranked() -> [(name: String, days: Int)] {
        daysByName
            .map { (name: $0.key, days: $0.value.count) }
            .sorted { $0.days != $1.days ? $0.days > $1.days : $0.name < $1.name }
    }

    /// Distinct days on which ANY of the named symptoms were reported — e.g. hot
    /// flushes OR night sweats on a day counts once, for a vasomotor frequency.
    func days(forAnyOf names: Set<String>) -> Int {
        var union: Set<Date> = []
        for (name, days) in daysByName where names.contains(name) { union.formUnion(days) }
        return union.count
    }

    /// Distinct days hot flushes or night sweats were reported.
    var vasomotorDays: Int { days(forAnyOf: Self.vasomotorNames) }

    // MARK: Apple Health `symptom.*` bridge

    /// The `HealthSample` typeID Keel archives a symptom under — the same forward
    /// transform `HealthIngestor` uses (lowercased, spaces to underscores).
    static func healthTypeID(forName name: String) -> String {
        "symptom." + name.lowercased().replacingOccurrences(of: " ", with: "_")
    }

    /// The display name for an archived `symptom.*` typeID (reverse transform):
    /// underscores back to spaces, first letter capitalised — which reproduces the
    /// sentence-case catalog names. Nil for a non-symptom typeID.
    static func name(fromHealthTypeID typeID: String) -> String? {
        let prefix = "symptom."
        guard typeID.hasPrefix(prefix) else { return nil }
        let raw = String(typeID.dropFirst(prefix.count)).replacingOccurrences(of: "_", with: " ")
        guard let first = raw.first else { return nil }
        return first.uppercased() + raw.dropFirst()
    }
}
