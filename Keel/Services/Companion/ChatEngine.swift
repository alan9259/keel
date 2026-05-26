import Foundation

/// One place a companion reply can come from: the on-device Apple Intelligence
/// model, or Gemini. Each engine owns its own tool loop and builds its own system
/// prompt (full or compact), and streams plain text out. The composite
/// `CompanionChatService` decides which to use and how to fall back.
///
/// Engines are `@MainActor` because they read the toolbox, which reads SwiftData.
@MainActor
protocol ChatEngine {
    /// For logs and fallback reasoning.
    var name: String { get }

    /// Cheap check for whether this engine can serve a turn right now. The
    /// composite skips an engine that says no rather than paying for an attempt.
    func isAvailable() -> Bool

    /// Stream the assistant's reply as incremental text deltas. Tool calls are
    /// handled internally and are invisible here.
    func streamReply(history: [ChatTurn]) -> AsyncThrowingStream<String, Error>
}

extension ChatEngine {
    /// The recent transcript, capped so the on-device model's small context isn't
    /// blown, rendered for engines that take a single prompt string.
    func recentPrompt(from history: [ChatTurn], maxTurns: Int = 8) -> String {
        let recent = history.suffix(maxTurns)
        guard let latest = recent.last, latest.role == .user else {
            // Nothing to answer (shouldn't happen): fall back to a gentle opener.
            return "Say a short, warm hello and ask what is on her mind."
        }
        let earlier = recent.dropLast()
        var lines: [String] = []
        if !earlier.isEmpty {
            lines.append("Earlier in this conversation:")
            for turn in earlier {
                lines.append("\(turn.role == .user ? "She" : "You"): \(turn.text)")
            }
            lines.append("")
        }
        lines.append("She just said: \(latest.text)")
        lines.append("")
        lines.append("Reply as Keel.")
        return lines.joined(separator: "\n")
    }
}
