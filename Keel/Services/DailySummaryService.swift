import Foundation
import SwiftData

/// Builds and stores a once-a-day reflection ("today's summary") from her own
/// data, keeping a dated record so past patterns can be looked back on rather
/// than only regenerated.
///
/// Grounded facts come from the shared `PatternEngine`; Apple Intelligence
/// narrates them into a warm paragraph on eligible devices, and a plain
/// deterministic reflection is written otherwise (and stored as the fallback the
/// AI is asked to improve on, never replace). Generation runs on the first app
/// open of each calendar day: `refreshIfNeeded()` writes today's summary only if
/// one isn't already stored.
@MainActor
@Observable
final class DailySummaryService {
    private let context: ModelContext
    private let ownerID: OwnerIDProvider

    init(context: ModelContext, ownerID: @escaping OwnerIDProvider) {
        self.context = context
        self.ownerID = ownerID
    }

    // MARK: Reads

    /// Today's stored summary, if it has already been generated.
    func today() -> DailySummary? {
        let day = Date().startOfDay
        let descriptor = FetchDescriptor<DailySummary>(
            predicate: #Predicate { $0.deletedAt == nil && $0.day == day }
        )
        return (try? context.fetch(descriptor))?.first
    }

    /// Past reflections, most recent first (includes today's if present).
    func history(limit: Int = 60) -> [DailySummary] {
        var descriptor = FetchDescriptor<DailySummary>(
            predicate: #Predicate { $0.deletedAt == nil },
            sortBy: [SortDescriptor(\.day, order: .reverse)]
        )
        descriptor.fetchLimit = limit
        return (try? context.fetch(descriptor)) ?? []
    }

    // MARK: Generation

    /// Generate today's summary if the day hasn't been written yet. Called on
    /// launch, so it runs once on the first open of each calendar day.
    func refreshIfNeeded() async {
        guard today() == nil else { return }
        await generate()
    }

    /// Rebuild today's summary from scratch (e.g. a manual "refresh" tap).
    func regenerate() async {
        await generate()
    }

    private func generate() async {
        let engine = PatternEngine.build(context: context)
        let findings = engine.findings()
        let facts = findings.map(\.fact)
        let deterministic = deterministicText(findings: findings, dayCount: engine.loggedDayCount)

        var text = deterministic
        var source = DailySummarySource.deterministic
        // Only ask the model to narrate when there are real facts to weave; a
        // gentle "still learning" line doesn't need embellishing.
        if !facts.isEmpty, let narrated = await narrate(facts: facts), !narrated.isEmpty {
            text = narrated
            source = .ai
        }
        persist(text: text, source: source, facts: facts)
    }

    /// The plain, always-available reflection. Second person, warm, and honest:
    /// leads with the strongest pattern's own wording, or a gentle note when
    /// there isn't enough to say. Never invents a statistic.
    private func deterministicText(findings: [PatternFinding], dayCount: Int) -> String {
        if dayCount < 5 {
            return "You've logged a handful of days so far. Keep going at your own pace and Keel will start to notice what tends to move together for you. There's no rush, and no wrong way to do this."
        }
        guard let top = findings.first else {
            return "Nothing stands out strongly in your recent check-ins, which is completely normal. Keep going and Keel will keep looking with you."
        }
        return top.detail
    }

    private func persist(text: String, source: DailySummarySource, facts: [String]) {
        let signalsJSON = (try? JSONEncoder().encode(facts)).flatMap { String(data: $0, encoding: .utf8) }
        if let existing = today() {
            existing.text = text
            existing.source = source
            existing.signalsJSON = signalsJSON
            existing.generatedAt = .now
            existing.updatedAt = .now
        } else {
            context.insert(DailySummary(
                day: Date().startOfDay, text: text, source: source,
                signalsJSON: signalsJSON, generatedAt: .now,
                ownerID: ownerID(), syncStatus: .synced))
        }
        try? context.save()
    }

    /// Narrate grounded facts with Apple Intelligence. Returns nil (falling back
    /// to the deterministic text) when the on-device model is unavailable or errors.
    private func narrate(facts: [String]) async -> String? {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            return await AppleSummaryNarrator.narrate(facts: facts)
        }
        #endif
        return nil
    }
}

#if canImport(FoundationModels)
import FoundationModels

/// One-shot Apple Intelligence narration of the day's grounded facts. Kept
/// separate from the chat engine because this is a single request, not a
/// tool-using conversation. The instructions hold the same non-negotiables as
/// the companion: AU/NZ voice, no invented facts, and the medical boundary.
@available(iOS 26.0, macOS 26.0, *)
enum AppleSummaryNarrator {
    @MainActor
    static func narrate(facts: [String]) async -> String? {
        guard case .available = SystemLanguageModel.default.availability else { return nil }
        let session = LanguageModelSession(instructions: instructions)
        let prompt = "Observations about her recent tracking:\n"
            + facts.map { "- \($0)" }.joined(separator: "\n")
        do {
            let response = try await session.respond(to: prompt)
            let text = response.content.trimmingCharacters(in: .whitespacesAndNewlines)
            return text.isEmpty ? nil : text
        } catch {
            return nil
        }
    }

    static let instructions = """
    You write Keel's short daily reflection for a woman navigating perimenopause.

    Rewrite the observations you are given into one warm, steady reflection of two \
    to four sentences, in second person (you, your).

    Rules you must not break:
    - Use only the facts given. Add no new facts or advice.
    - Do not state any specific numbers, counts, days, or percentages. Describe \
    the patterns in words. The exact figures are shown to her elsewhere.
    - Australian and New Zealand spelling. Say "hot flushes", never "hot flashes".
    - Never diagnose, never prescribe, never predict. These are things to notice.
    - No dashes of any kind. Use full stops and commas.
    - Gentle and grounded, never alarming. It is fine to gently suggest she might \
    mention something to her GP.

    Return only the reflection text, nothing else.
    """
}
#endif
