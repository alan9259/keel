import Foundation
import SwiftData

@MainActor
protocol SymptomRepositoring {
    func syncBuiltIns()
    func allActive() -> [Symptom]
    func defaultChips() -> [Symptom]
    func grouped() -> [(category: SymptomCategory, symptoms: [Symptom])]
    func search(_ query: String) -> [Symptom]
    @discardableResult
    func findOrCreateCustom(name: String, category: SymptomCategory) -> Symptom
    @discardableResult
    func findOrCreateCustom(name: String, categoryRaw: String) -> Symptom
}

@MainActor
struct SymptomRepository: SymptomRepositoring {
    let context: ModelContext
    let ownerID: OwnerIDProvider

    /// Days of check-ins before the default chips start following her own use.
    private static let adaptAfterDays = 14
    private static let versionKey = "keel.symptomCatalogVersion"

    /// Bring the built-in catalog in line with `SymptomCatalog`, once per version
    /// (and again if the store is empty, e.g. after a restore). Built-ins are
    /// global (`ownerID == ""`, `isCustom == false`).
    ///
    /// Deliberately version-gated rather than run every launch: once she has
    /// moved or removed a chip herself, that stays her call.
    func syncBuiltIns() {
        let defaults = UserDefaults.standard
        let stored = defaults.integer(forKey: Self.versionKey)
        let existing = (try? context.fetchCount(FetchDescriptor<Symptom>())) ?? 0
        guard stored < SymptomCatalog.version || existing == 0 else { return }
        reconcileBuiltIns()
        defaults.set(SymptomCatalog.version, forKey: Self.versionKey)
    }

    private func reconcileBuiltIns() {
        let all = (try? context.fetch(FetchDescriptor<Symptom>())) ?? []
        var byName: [String: Symptom] = [:]
        for symptom in all where !symptom.isCustom {
            byName[symptom.name.lowercased()] = symptom
        }

        // Carry history across renamed wording rather than stranding it on a chip
        // she can no longer see.
        for (old, new) in SymptomCatalog.renamed {
            guard let symptom = byName[old.lowercased()], byName[new.lowercased()] == nil else { continue }
            symptom.name = new
            markReferenceData(symptom)
            byName[new.lowercased()] = symptom
            byName[old.lowercased()] = nil
        }

        var catalogNames: Set<String> = []
        for entry in SymptomCatalog.builtIn {
            let key = entry.name.lowercased()
            catalogNames.insert(key)
            if let symptom = byName[key] {
                symptom.categoryRaw = entry.category.rawValue
                symptom.isDefaultChip = entry.isDefault
                markReferenceData(symptom)
            } else {
                context.insert(Symptom(
                    name: entry.name,
                    category: entry.category,
                    isCustom: false,
                    isDefaultChip: entry.isDefault,
                    ownerID: "",
                    syncStatus: .synced // built-ins aren't user data to upload
                ))
            }
        }

        // Built-ins the catalog dropped: archive, never delete, so past check-ins
        // keep their labels.
        for symptom in all where !symptom.isCustom && !symptom.isArchived
            && !catalogNames.contains(symptom.name.lowercased()) {
            symptom.isArchived = true
            markReferenceData(symptom)
        }

        try? context.save()
    }

    /// Built-ins are global reference data (`ownerID == ""`), seeded identically
    /// on every device. Reconciling them must not queue them for upload: there's
    /// nothing to share, and a row with no owner can't satisfy a row-level
    /// ownership rule (`owner_id = auth.uid()`) on the backend.
    private func markReferenceData(_ symptom: Symptom) {
        symptom.updatedAt = .now
        symptom.syncStatus = .synced
    }

    func allActive() -> [Symptom] {
        let descriptor = FetchDescriptor<Symptom>(
            predicate: #Predicate { $0.deletedAt == nil && $0.isArchived == false },
            sortBy: [SortDescriptor(\.name)]
        )
        return (try? context.fetch(descriptor)) ?? []
    }

    /// The small, fast set shown before "more". Catalog order to start with; once
    /// she has roughly a fortnight of check-ins behind her, her most-logged rise
    /// to the front so the list adapts to her rather than the other way around.
    func defaultChips() -> [Symptom] {
        let chips = allActive().filter(\.isDefaultChip)
        let ranked = chips.sorted {
            let a = SymptomCatalog.defaultRank(of: $0.name), b = SymptomCatalog.defaultRank(of: $1.name)
            return a == b ? $0.name < $1.name : a < b
        }
        guard trackingDayCount() >= Self.adaptAfterDays else { return ranked }
        let counts = usageCounts()
        return ranked.enumerated().sorted { lhs, rhs in
            let a = counts[lhs.element.id] ?? 0, b = counts[rhs.element.id] ?? 0
            return a == b ? lhs.offset < rhs.offset : a > b
        }.map(\.element)
    }

    /// How many check-ins each symptom has been logged on.
    func usageCounts() -> [UUID: Int] {
        let descriptor = FetchDescriptor<CheckInSymptom>(predicate: #Predicate { $0.deletedAt == nil })
        let links = (try? context.fetch(descriptor)) ?? []
        return links.reduce(into: [UUID: Int]()) { counts, link in
            guard let id = link.symptomID else { return }
            counts[id, default: 0] += 1
        }
    }

    /// Distinct days with a check-in, used to decide when the chips may adapt.
    private func trackingDayCount() -> Int {
        let descriptor = FetchDescriptor<CheckIn>(predicate: #Predicate { $0.deletedAt == nil })
        let checkIns = (try? context.fetch(descriptor)) ?? []
        return Set(checkIns.map { $0.date.startOfDay }).count
    }

    func grouped() -> [(category: SymptomCategory, symptoms: [Symptom])] {
        let all = allActive()
        return SymptomCategory.allCases
            .sorted { $0.sortOrder < $1.sortOrder }
            .compactMap { category in
                let matches = all.filter { $0.category == category }
                return matches.isEmpty ? nil : (category, matches)
            }
    }

    func search(_ query: String) -> [Symptom] {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return allActive() }
        return allActive().filter { $0.name.localizedCaseInsensitiveContains(trimmed) }
    }

    @discardableResult
    func findOrCreateCustom(name: String, category: SymptomCategory) -> Symptom {
        findOrCreateCustom(name: name, categoryRaw: category.rawValue)
    }

    /// Same, but the category is a raw string so user-created categories work.
    @discardableResult
    func findOrCreateCustom(name: String, categoryRaw: String) -> Symptom {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if let match = allActive().first(where: { $0.name.localizedCaseInsensitiveCompare(trimmed) == .orderedSame }) {
            return match
        }
        let symptom = Symptom(
            name: trimmed,
            category: .body,
            isCustom: true,
            ownerID: ownerID()
        )
        symptom.categoryRaw = categoryRaw
        context.insert(symptom)
        try? context.save()
        return symptom
    }
}
