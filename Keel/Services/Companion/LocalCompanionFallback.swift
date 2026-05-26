import Foundation

/// The offline companion: used when neither Apple Intelligence nor Gemini can
/// serve a turn (an ineligible device, the model not ready or guardrail-blocked,
/// no proxy configured).
///
/// It can't reason like a model, so it doesn't pretend to. It does two honest
/// things: when her message clearly asks to add a check-in or a symptom, it
/// drafts the same confirm-before-save card the real engines do (via the shared
/// toolbox), so logging keeps working; otherwise it says plainly that it can't
/// reach the companion right now and points her to real support and to logging.
@MainActor
final class LocalCompanionFallback: ChatService {
    private let toolbox: CompanionToolbox

    init(toolbox: CompanionToolbox) {
        self.toolbox = toolbox
    }

    private static let offlineReply = """
    I'm having trouble reaching the companion right now, so I can't talk this \
    through properly. I don't want to leave you with nothing: if you need \
    support, tap Get support above for lines you can reach any time. I can also \
    add a check-in or log a symptom for you here, just tell me what to note.
    """

    func streamReply(history: [ChatTurn], system: String) -> AsyncThrowingStream<String, Error> {
        let lastUser = history.last(where: { $0.role == .user })?.text ?? ""
        return AsyncThrowingStream { continuation in
            let task = Task { @MainActor in
                let reply = await self.draftIfLogIntent(lastUser) ?? Self.offlineReply
                for word in reply.split(separator: " ", omittingEmptySubsequences: false) {
                    if Task.isCancelled { break }
                    try? await Task.sleep(for: .milliseconds(35))
                    continuation.yield(word + " ")
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    /// If the message clearly asks to log something, draft the proposal and return
    /// a short reply pointing to the confirm card. Otherwise nil.
    private func draftIfLogIntent(_ message: String) async -> String? {
        let lower = message.lowercased()
        let verbs = ["add", "log", "save", "record", "track", "note", "jot", "put in"]
        guard verbs.contains(where: { lower.contains($0) }) else { return nil }

        let symptomHits = SymptomCatalog.builtIn.map(\.name).filter { lower.contains($0.lowercased()) }
        let checkInWords = ["check-in", "checkin", "check in", "entry", "mood", "energy",
                            "feeling", "today", "how i'm", "how i am", "how i feel"]
        let mentionsCheckIn = checkInWords.contains { lower.contains($0) }

        // A named symptom with no check-in framing → draft a single symptom log.
        if !symptomHits.isEmpty, !mentionsCheckIn {
            _ = await toolbox.run(name: "propose_log_symptom",
                                  arguments: ["name": symptomHits[0], "severity": "mild"])
            return "I've drafted that symptom for you to confirm just below. Nothing is saved until you tap it, and you can change the details first."
        }

        guard mentionsCheckIn || !symptomHits.isEmpty else { return nil }

        var args: [String: Any] = [:]
        if let mood = Mood.allCases.first(where: { lower.contains($0.label.lowercased()) }) {
            args["mood"] = mood.rawValue
        }
        if let energy = Self.extractEnergy(from: lower) { args["energy"] = energy }
        if !symptomHits.isEmpty { args["symptoms"] = symptomHits }

        _ = await toolbox.run(name: "propose_log_checkin", arguments: args)
        return "I've put a check-in together for you, just below. Have a look and tap save if it's right, or tell me what to adjust before you do."
    }

    /// Pull an energy value (0 to 100) out of the text, only when she mentions the
    /// word "energy", preferring the number that follows it.
    private static func extractEnergy(from lower: String) -> Int? {
        guard let range = lower.range(of: "energy") else { return nil }
        var number = ""
        for ch in lower[range.upperBound...] {
            if ch.isNumber {
                number.append(ch)
            } else if !number.isEmpty {
                break
            }
        }
        guard let value = Int(number), (0...100).contains(value) else { return nil }
        return value
    }
}
