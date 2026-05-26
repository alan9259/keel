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

    /// Cards come straight from the shared `PatternEngine`, so the Patterns page
    /// and the daily summary always describe the same patterns. This layer only
    /// adds the gentle "still learning" / "nothing stands out yet" bookends.
    private func derive() -> [Draft] {
        let engine = PatternEngine.build(context: context)
        let dayCount = engine.loggedDayCount

        guard dayCount >= Self.minDays else {
            return [Draft(
                title: "Still learning your rhythm",
                detail: "A few more check-ins and Keel can start to notice what tends to move together for you. There's no rush, and no wrong way to do this.",
                timeframe: "\(dayCount) day\(dayCount == 1 ? "" : "s") logged so far",
                icon: "sparkles", accent: .sage)]
        }

        let drafts = engine.findings().map {
            Draft(title: $0.title, detail: $0.detail, timeframe: $0.timeframe, icon: $0.icon, accent: $0.accent)
        }
        if drafts.isEmpty {
            return [Draft(
                title: "Nothing stands out yet",
                detail: "Your check-ins don't show a strong pattern just now, which is completely normal. Keep going and Keel will keep looking with you.",
                timeframe: "Across \(dayCount) days",
                icon: "leaf", accent: .sage)]
        }
        return drafts
    }
}
