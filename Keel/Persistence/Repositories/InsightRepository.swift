import Foundation
import SwiftData

@MainActor
protocol InsightRepositoring {
    func all() -> [Insight]
    /// Re-derive insights from her own data. Replaces the old mock seeding.
    func refreshDerived()
}

@MainActor
struct InsightRepository: InsightRepositoring {
    let context: ModelContext
    let ownerID: OwnerIDProvider

    /// Days of check-ins before Keel attempts a real pattern rather than a gentle
    /// "still learning" note.
    private static let minDays = 5

    func all() -> [Insight] {
        let descriptor = FetchDescriptor<Insight>(
            predicate: #Predicate { $0.deletedAt == nil },
            sortBy: [SortDescriptor(\.generatedAt, order: .reverse)]
        )
        return (try? context.fetch(descriptor)) ?? []
    }

    /// Derive insights from her check-ins, sleep, and symptoms and rewrite the
    /// stored set. Honest by design: only surfaces a pattern when there is genuine
    /// signal, frames it as a possibility, never invents a statistic, and says so
    /// plainly when there isn't enough data. Regenerated locally each launch, so
    /// it stays private to the device and never uploads.
    func refreshDerived() {
        (try? context.fetch(FetchDescriptor<Insight>()))?.forEach { context.delete($0) }
        let owner = ownerID()
        for draft in derive() {
            context.insert(Insight(title: draft.title, detail: draft.detail, timeframe: draft.timeframe,
                                   iconKey: draft.icon, accent: draft.accent,
                                   ownerID: owner, syncStatus: .synced))
        }
        try? context.save()
    }

    // MARK: Derivation

    private struct Draft {
        let title: String
        let detail: String
        let timeframe: String
        let icon: String
        let accent: InsightAccent
    }

    /// Cards come straight from the shared `PatternEngine`, so the Patterns page and
    /// the daily summary always describe the same patterns.
    ///
    /// Only real findings become cards. The low-data and no-pattern states are told
    /// once, accurately and live, by the Patterns screen's "Today's reflection"
    /// placeholder (`DailySummaryService.placeholderReflection`). A "still learning"
    /// card here just repeated that message, and its "N days logged so far" count
    /// went stale between launches (it's only regenerated on launch), so it's gone.
    private func derive() -> [Draft] {
        let engine = PatternEngine.build(context: context)
        guard engine.loggedDayCount >= Self.minDays else { return [] }
        return engine.findings().map {
            Draft(title: $0.title, detail: $0.detail, timeframe: $0.timeframe, icon: $0.icon, accent: $0.accent)
        }
    }
}
