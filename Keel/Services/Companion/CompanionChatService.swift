import Foundation

/// The companion, as the app sees it. Implements the existing `ChatService` seam,
/// so `ChatView` is unchanged: it still calls `streamReply(history:system:)` and
/// receives text deltas.
///
/// Underneath, it tries each engine in priority order (Apple Intelligence first,
/// then Gemini), and if an engine can't serve the turn, or errors before
/// producing a single token, it falls to the next. `LocalCompanionFallback` is
/// the final resort so the chat always answers, even offline with no engine, and
/// it can still draft a log card from a clear request.
///
/// The `system` argument is superseded: each engine builds its own prompt (full
/// for Gemini, compact for the on-device model), so the boundary and voice stay
/// consistent regardless of who answers.
@MainActor
final class CompanionChatService: ChatService {
    private let engines: [ChatEngine]
    private let fallback: ChatService

    init(engines: [ChatEngine], fallback: ChatService) {
        self.engines = engines
        self.fallback = fallback
    }

    func streamReply(history: [ChatTurn], system: String) -> AsyncThrowingStream<String, Error> {
        AsyncThrowingStream { continuation in
            let task = Task { @MainActor in
                for engine in engines where engine.isAvailable() {
                    var produced = false
                    do {
                        for try await delta in engine.streamReply(history: history) {
                            produced = true
                            continuation.yield(delta)
                        }
                    } catch {
                        // Mid-reply failure can't be undone; surface it. A failure
                        // before any token just moves on to the next engine.
                        if produced { continuation.finish(throwing: error); return }
                        continue
                    }
                    if produced { continuation.finish(); return }
                    // Finished cleanly but empty: try the next engine instead.
                }

                // No engine answered: the offline fallback still gets her a reply.
                do {
                    for try await delta in fallback.streamReply(history: history, system: system) {
                        continuation.yield(delta)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}
