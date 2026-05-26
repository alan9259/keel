import Foundation

/// The fallback engine: Google Gemini (`gemini-2.5-flash`), reached over its
/// streaming REST API. Runs the same toolbox as the on-device engine, driving the
/// manual functionCall → functionResponse loop itself.
///
/// ⚠️ Security: a Gemini API key must never ship inside a client app. In
/// production, point `baseURL` at your own proxy (or a Supabase Edge Function)
/// that holds the key and forwards to Google; leave `apiKey` nil. `apiKey` is for
/// local development against generativelanguage.googleapis.com only.
@MainActor
final class GeminiChatEngine: ChatEngine {
    let name = "gemini"

    /// `https://generativelanguage.googleapis.com` (dev) or your proxy base URL.
    private let baseURL: URL
    private let apiKey: String?
    private let model: String
    private let toolbox: CompanionToolbox
    private let limiter: GeminiRateLimiter
    private let maxToolRounds = 5

    init(baseURL: URL, apiKey: String?, model: String,
         toolbox: CompanionToolbox, limiter: GeminiRateLimiter) {
        self.baseURL = baseURL
        self.apiKey = apiKey
        self.model = model
        self.toolbox = toolbox
        self.limiter = limiter
    }

    func isAvailable() -> Bool { true }

    func streamReply(history: [ChatTurn]) -> AsyncThrowingStream<String, Error> {
        AsyncThrowingStream { continuation in
            let task = Task { @MainActor in
                do {
                    // Seed the conversation from the transcript.
                    var contents: [[String: Any]] = history.map { turn in
                        ["role": turn.role == .user ? "user" : "model",
                         "parts": [["text": turn.text]]]
                    }
                    let tools: [[String: Any]] = [[
                        "function_declarations": toolbox.specs.map { spec in
                            ["name": spec.name, "description": spec.description, "parameters": spec.parameters]
                        },
                    ]]

                    // Round-trip until the model answers with text instead of a tool call.
                    for _ in 0..<maxToolRounds {
                        try await limiter.reserve()
                        let round = try await runRound(contents: contents, tools: tools) { delta in
                            continuation.yield(delta)
                        }
                        guard !round.functionCalls.isEmpty else {
                            continuation.finish()
                            return
                        }
                        // Record what the model asked for, then answer each tool.
                        contents.append(["role": "model", "parts": round.modelParts])
                        var responseParts: [[String: Any]] = []
                        for call in round.functionCalls {
                            let result = await toolbox.run(name: call.name, arguments: call.args)
                            responseParts.append([
                                "functionResponse": [
                                    "name": call.name,
                                    "response": ["result": result],
                                ],
                            ])
                        }
                        contents.append(["role": "user", "parts": responseParts])
                    }
                    // Ran out of tool rounds without a final answer.
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    // MARK: One streamed round

    private struct FunctionCall { let name: String; let args: [String: Any] }
    private struct Round {
        var functionCalls: [FunctionCall] = []
        /// The model's own parts this round, replayed back so it has the context.
        var modelParts: [[String: Any]] = []
    }

    /// Streams one `streamGenerateContent` call, forwarding text deltas and
    /// collecting any function calls the model made.
    private func runRound(contents: [[String: Any]], tools: [[String: Any]],
                          onText: (String) -> Void) async throws -> Round {
        var body: [String: Any] = [
            "system_instruction": ["parts": [["text": KeelChatPrompt.full()]]],
            "contents": contents,
            "tools": tools,
            "generationConfig": ["maxOutputTokens": 1024],
        ]
        // Keep JSON stable for tests/readability.
        body["safetySettings"] = []

        let path = "v1beta/models/\(model):streamGenerateContent"
        var components = URLComponents(url: baseURL.appending(path: path), resolvingAgainstBaseURL: false)
        components?.queryItems = [URLQueryItem(name: "alt", value: "sse")]
        guard let url = components?.url else { throw ChatError.notConfigured }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "content-type")
        if let apiKey, !apiKey.isEmpty {
            request.setValue(apiKey, forHTTPHeaderField: "x-goog-api-key")
        }
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (bytes, response) = try await URLSession.shared.bytes(for: request)
        guard let http = response as? HTTPURLResponse else { throw ChatError.httpStatus(-1) }
        guard (200..<300).contains(http.statusCode) else { throw ChatError.httpStatus(http.statusCode) }

        var round = Round()
        var textParts: [String] = []
        for try await line in bytes.lines {
            guard line.hasPrefix("data:") else { continue }
            let payload = line.dropFirst(5).trimmingCharacters(in: .whitespaces)
            guard let data = payload.data(using: .utf8),
                  let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let candidates = obj["candidates"] as? [[String: Any]],
                  let content = candidates.first?["content"] as? [String: Any],
                  let parts = content["parts"] as? [[String: Any]]
            else { continue }

            for part in parts {
                if let text = part["text"] as? String, !text.isEmpty {
                    textParts.append(text)
                    onText(text)
                } else if let fn = part["functionCall"] as? [String: Any],
                          let name = fn["name"] as? String {
                    let args = fn["args"] as? [String: Any] ?? [:]
                    round.functionCalls.append(FunctionCall(name: name, args: args))
                    round.modelParts.append(["functionCall": fn])
                }
            }
        }
        // Replay any streamed text too, so the model keeps its own thread.
        if !textParts.isEmpty {
            round.modelParts.append(["text": textParts.joined()])
        }
        return round
    }
}
