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
        cleanUpLowContentSummaries()
        guard today() == nil else { return }
        await generate()
    }

    /// Rebuild today's summary from scratch (e.g. a manual "refresh" tap).
    func regenerate() async {
        cleanUpLowContentSummaries()
        await generate()
    }

    private func generate() async {
        let engine = PatternEngine.build(context: context)
        let findings = engine.findings()
        // Nothing worth reflecting on yet. Don't store a placeholder reflection: a
        // fixed "still learning" line, stored every day, turned "Looking back" into
        // a wall of identical entries (reported bug). The Patterns screen shows a
        // live, day-count-accurate note instead (`placeholderReflection`), which is
        // never persisted and so can't repeat in the history.
        guard !findings.isEmpty else { return }

        let facts = findings.map(\.fact)
        var text = findings[0].detail
        var source = DailySummarySource.deterministic
        // Ask the model to narrate the real facts into a warm paragraph.
        if let narrated = await narrate(facts: facts), !narrated.isEmpty {
            text = narrated
            source = .ai
        }
        persist(text: text, source: source, facts: facts)
    }

    /// The live "not enough to reflect on yet" note shown on the Patterns screen
    /// when there's no stored reflection. Accurate to the days logged (never "a
    /// handful" when it is one), and never persisted, so it can't repeat.
    nonisolated static func placeholderReflection(loggedDays: Int) -> String {
        switch loggedDays {
        case ..<1:
            return "Check in for a little while and Keel will start writing you a short daily reflection here."
        case 1:
            return "You've logged your first day. Keep going at your own pace and Keel will start to notice what tends to move together for you."
        case 2...4:
            return "You've logged a few days so far. A little more and Keel can start to notice what tends to move together for you."
        default:
            return "Nothing stands out strongly in your recent check-ins yet, which is completely normal. Keep going and Keel will keep looking with you."
        }
    }

    /// Legacy builds persisted the fixed low-content reflections daily, so existing
    /// users have a run of identical entries in "Looking back". Soft-delete those
    /// known placeholder texts (they carry no real pattern) so history shows only
    /// real reflections; new ones are no longer written (see `generate`).
    private static let legacyPlaceholderTexts: Set<String> = [
        "You've logged a handful of days so far. Keep going at your own pace and Keel will start to notice what tends to move together for you. There's no rush, and no wrong way to do this.",
        "Nothing stands out strongly in your recent check-ins, which is completely normal. Keep going and Keel will keep looking with you.",
    ]

    private func cleanUpLowContentSummaries() {
        let stored = (try? context.fetch(FetchDescriptor<DailySummary>(
            predicate: #Predicate { $0.deletedAt == nil }))) ?? []
        var changed = false
        for summary in stored where Self.legacyPlaceholderTexts.contains(summary.text) {
            summary.softDelete()
            changed = true
        }
        if changed { try? context.save() }
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
