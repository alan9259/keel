import Foundation

/// One turn of conversation handed to the model.
struct ChatTurn: Sendable {
    let role: ChatRole
    let text: String
}

/// Backend-agnostic seam for the AI companion, mirroring the `SyncProvider`
/// pattern. `CompanionChatService` composes the on-device Apple Intelligence and
/// Gemini engines behind this; `LocalCompanionFallback` answers offline (and can
/// still draft a log card). Swapping the implementation is a one-line change in
/// `AppEnvironment`.
protocol ChatService: Sendable {
    /// Streams the assistant's reply as incremental text deltas. Main-actor
    /// isolated: the companion reads repositories and pushes proposals, and its
    /// callers (the chat screen, the harness probes) are already on the main actor.
    @MainActor
    func streamReply(history: [ChatTurn], system: String) -> AsyncThrowingStream<String, Error>
}

enum ChatError: Error {
    case httpStatus(Int)
    case notConfigured
    /// The engine can't serve this turn (e.g. Apple Intelligence isn't available
    /// on this device / OS). Signals the composite to fall back.
    case unavailable
    /// The Gemini free-tier budget is spent; try again after `retryAfter` seconds.
    case rateLimited(retryAfter: TimeInterval)
}
