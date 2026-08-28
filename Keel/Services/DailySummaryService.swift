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

    /// Refresh on launch. A reflection is keyed to a CHANGE in her patterns, not to
    /// the calendar: it only writes a new entry when today's patterns differ from
    /// the most recent reflection, so "Looking back" doesn't fill with identical
    /// entries when nothing has changed.
    func refreshIfNeeded() async {
        cleanUpLowContentSummaries()
        purgeUnsafeReflections()
        dedupUnchangedSummaries()
        await generate(force: false)
    }

    /// Manual "refresh" tap: re-evaluate now, and re-word the current reflection
    /// even if the underlying pattern is unchanged (so the button does something),
    /// without adding a duplicate to the history.
    func regenerate() async {
        cleanUpLowContentSummaries()
        purgeUnsafeReflections()
        dedupUnchangedSummaries()
        await generate(force: true)
    }

    private func generate(force: Bool) async {
        let findings = PatternEngine.build(context: context).findings()
        // Nothing worth reflecting on yet: the Patterns screen shows a live,
        // day-count-accurate note (`placeholderReflection`) instead of a stored one.
        guard !findings.isEmpty else { return }

        let facts = findings.map(\.fact)
        let signature = Self.signature(of: facts)
        let latest = mostRecent()
        let changed = latest?.signalsJSON != signature

        // Unchanged and not a manual refresh → the existing reflection still stands,
        // so add nothing (this is what stops the per-day duplicates).
        guard changed || force else { return }

        var text = findings[0].detail
        var source = DailySummarySource.deterministic
        if let narrated = await narrate(facts: facts), !narrated.isEmpty {
            text = narrated
            source = .ai
        }

        if changed {
            // A different pattern → a new dated reflection (or replace today's, so a
            // pattern that shifts within a day doesn't leave two entries for it).
            if let todays = today() {
                apply(text: text, source: source, signature: signature, to: todays)
            } else {
                context.insert(DailySummary(
                    day: Date().startOfDay, text: text, source: source,
                    signalsJSON: signature, generatedAt: .now,
                    ownerID: ownerID(), syncStatus: .synced))
            }
        } else if let latest {
            // Same pattern, manual refresh → freshen the wording in place, no new row.
            apply(text: text, source: source, signature: signature, to: latest)
        }
        try? context.save()
    }

    private func apply(text: String, source: DailySummarySource, signature: String, to summary: DailySummary) {
        summary.text = text
        summary.source = source
        summary.signalsJSON = signature
        summary.generatedAt = .now
        summary.updatedAt = .now
    }

    /// The most recent stored reflection, of any day.
    private func mostRecent() -> DailySummary? {
        var d = FetchDescriptor<DailySummary>(
            predicate: #Predicate { $0.deletedAt == nil },
            sortBy: [SortDescriptor(\.day, order: .reverse), SortDescriptor(\.generatedAt, order: .reverse)])
        d.fetchLimit = 1
        return (try? context.fetch(d))?.first
    }

    /// Stable fingerprint of the day's grounded facts, matched against a stored
    /// `signalsJSON` to tell whether the pattern has actually changed.
    nonisolated static func signature(of facts: [String]) -> String {
        (try? JSONEncoder().encode(facts)).flatMap { String(data: $0, encoding: .utf8) } ?? facts.joined(separator: "|")
    }

    /// Collapse runs of consecutive reflections that describe the SAME pattern down
    /// to the first (when that pattern began). Cleans up the per-day duplicates
    /// earlier builds left, and is a harmless no-op once generation is change-keyed.
    private func dedupUnchangedSummaries() {
        let ascending = (try? context.fetch(FetchDescriptor<DailySummary>(
            predicate: #Predicate { $0.deletedAt == nil },
            sortBy: [SortDescriptor(\.day), SortDescriptor(\.generatedAt)]))) ?? []
        var previousSignature: String?
        var changed = false
        for summary in ascending {
            let signature = summary.signalsJSON ?? ""
            if signature == previousSignature {
                summary.softDelete() // same pattern as the one before it → redundant
                changed = true
            } else {
                previousSignature = signature
            }
        }
        if changed { try? context.save() }
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

    /// Retire any already-stored reflection that reassures or normalises a symptom
    /// (written before the safety filter existed), so "Looking back" can't keep
    /// showing it. New reflections are guarded at generation time (see `narrate`).
    private func purgeUnsafeReflections() {
        let stored = (try? context.fetch(FetchDescriptor<DailySummary>(
            predicate: #Predicate { $0.deletedAt == nil }))) ?? []
        var changed = false
        for summary in stored where Self.offersReassurance(summary.text) {
            summary.softDelete()
            changed = true
        }
        if changed { try? context.save() }
    }

    /// Narrate grounded facts with Apple Intelligence. Returns nil (falling back to the
    /// deterministic text) when the on-device model is unavailable, errors, OR slips
    /// into reassurance — the model must never tell her a symptom is normal or nothing
    /// to worry about, and instructions alone don't guarantee that, so we also filter.
    private func narrate(facts: [String]) async -> String? {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            guard let text = await AppleSummaryNarrator.narrate(facts: facts) else { return nil }
            return Self.offersReassurance(text) ? nil : text
        }
        #endif
        return nil
    }

    /// A reflection must reflect a pattern to notice, never reassure her about what it
    /// means (Keel can't judge whether a symptom is serious) and never alarm her. This
    /// catches an AI narration that normalises or dismisses a symptom, or tells her to
    /// worry or not to, so it can be discarded in favour of the deterministic text.
    /// A tester flagged "there's no reason to worry... perfectly normal" for headaches.
    nonisolated static func offersReassurance(_ text: String) -> Bool {
        let lower = text.lowercased()
        let banned = [
            "worry",              // a reflection should neither reassure nor alarm
            "normal",             // never call a symptom normal
            "harmless", "benign",
            "nothing serious", "not serious", "nothing to be concerned",
            "no cause for concern", "no need to be concerned", "no need for concern",
            "perfectly fine", "you're fine", "you are fine", "everything is fine",
            "don't be alarmed", "do not be alarmed", "no need to panic", "no need to worry",
            "reassur",            // reassure / reassuring / rest assured
        ]
        return banned.contains { lower.contains($0) }
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
    - Never reassure her about her health. You cannot judge whether a symptom is \
    serious, so do not say a symptom is normal, common, harmless, expected, nothing \
    to worry about, or that there is no reason to worry. Do not tell her to worry or \
    not to worry. Reflect the pattern plainly, and where it fits, gently suggest she \
    might mention it to her GP.
    - No dashes of any kind. Use full stops and commas.

    Keep a calm, steady tone. Do not be alarming, but do not offer comfort about what \
    her symptoms mean either.

    Return only the reflection text, nothing else.
    """
}
#endif
