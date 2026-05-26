import Foundation

#if canImport(FoundationModels)
import FoundationModels

/// The primary engine: Apple Intelligence's on-device model (Foundation Models).
/// Free, private, and offline. Available only on eligible devices running the
/// right OS, so it is fully behind `canImport` + an availability check; when it
/// can't serve a turn, the composite falls back to Gemini.
///
/// Tool calls are executed by the framework itself: we attach `Tool`s and stream
/// the reply, and the model runs the tools transparently between tokens.
@available(iOS 26.0, macOS 26.0, *)
@MainActor
final class AppleIntelligenceEngine: ChatEngine {
    let name = "apple-intelligence"
    private let toolbox: CompanionToolbox

    init(toolbox: CompanionToolbox) {
        self.toolbox = toolbox
    }

    func isAvailable() -> Bool {
        if case .available = SystemLanguageModel.default.availability { return true }
        return false
    }

    func streamReply(history: [ChatTurn]) -> AsyncThrowingStream<String, Error> {
        AsyncThrowingStream { continuation in
            let task = Task { @MainActor in
                guard case .available = SystemLanguageModel.default.availability else {
                    continuation.finish(throwing: ChatError.unavailable)
                    return
                }
                do {
                    let session = LanguageModelSession(
                        tools: CompanionAppleTools.all(toolbox: toolbox),
                        instructions: KeelChatPrompt.compact()
                    )
                    let prompt = recentPrompt(from: history)
                    var previous = ""
                    // `streamResponse` yields the cumulative reply; forward deltas.
                    for try await partial in session.streamResponse(to: prompt) {
                        let text = partial.content
                        let delta = text.hasPrefix(previous) ? String(text.dropFirst(previous.count)) : text
                        previous = text
                        if !delta.isEmpty { continuation.yield(delta) }
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

// MARK: - Foundation Models tool adapters
//
// Thin `Tool` conformers that forward to the shared `CompanionToolbox`, so the
// query logic stays single-sourced. Each mirrors a spec in the toolbox.

/// A read tool whose only argument is a day window.
@available(iOS 26.0, macOS 26.0, *)
struct AppleDaysTool: Tool {
    let name: String
    let description: String
    let toolbox: CompanionToolbox

    @Generable
    struct Arguments {
        @Guide(description: "How many days back to include. Use a sensible default if unsure.")
        var days: Int?
    }

    func call(arguments: Arguments) async throws -> String {
        var args: [String: Any] = [:]
        if let days = arguments.days { args["days"] = days }
        return await toolbox.run(name: name, arguments: args)
    }
}

/// A read tool that takes no arguments.
@available(iOS 26.0, macOS 26.0, *)
struct AppleNoArgTool: Tool {
    let name: String
    let description: String
    let toolbox: CompanionToolbox

    @Generable
    struct Arguments {}

    func call(arguments: Arguments) async throws -> String {
        await toolbox.run(name: name, arguments: [:])
    }
}

/// Draft a symptom log for her to confirm.
@available(iOS 26.0, macOS 26.0, *)
struct AppleLogSymptomTool: Tool {
    let name = "propose_log_symptom"
    let description = "Draft a card for her to confirm logging one symptom. Call this whenever she asks to add, log, note, or record a symptom, even in passing. Saves nothing until she confirms. Never use for anything in the safety section."
    let toolbox: CompanionToolbox

    @Generable
    struct Arguments {
        @Guide(description: "The symptom, in her words, e.g. 'hot flushes'.")
        var name: String
        @Guide(description: "How strongly she felt it: mild, moderate or severe.")
        var severity: String?
    }

    func call(arguments: Arguments) async throws -> String {
        var args: [String: Any] = ["name": arguments.name]
        if let severity = arguments.severity { args["severity"] = severity }
        return await toolbox.run(name: name, arguments: args)
    }
}

/// Draft a check-in for her to confirm.
@available(iOS 26.0, macOS 26.0, *)
struct AppleLogCheckInTool: Tool {
    let name = "propose_log_checkin"
    let description = "Draft a card for her to confirm saving a check-in (also called an entry, or a log) for today. Call this whenever she asks to add, log, record, or save an entry or check-in, even if she has not given her mood or energy yet: leave those fields blank and she can fill them in on the card. Saves nothing until she confirms."
    let toolbox: CompanionToolbox

    @Generable
    struct Arguments {
        @Guide(description: "Her mood, only if she has said: great, good, okay, low or difficult.")
        var mood: String?
        @Guide(description: "Energy from 0 to 100, only if she has indicated it.")
        var energy: Int?
        @Guide(description: "A short note in her words, optional.")
        var note: String?
        @Guide(description: "Symptom names she mentioned, optional.")
        var symptoms: [String]?
    }

    func call(arguments: Arguments) async throws -> String {
        var args: [String: Any] = [:]
        if let mood = arguments.mood { args["mood"] = mood }
        if let energy = arguments.energy { args["energy"] = energy }
        if let note = arguments.note { args["note"] = note }
        if let symptoms = arguments.symptoms { args["symptoms"] = symptoms }
        return await toolbox.run(name: name, arguments: args)
    }
}

/// Builds the tool set from the toolbox specs, so the two engines stay in step.
@available(iOS 26.0, macOS 26.0, *)
enum CompanionAppleTools {
    @MainActor
    static func all(toolbox: CompanionToolbox) -> [any Tool] {
        var tools: [any Tool] = []
        for spec in toolbox.specs {
            switch spec.name {
            case "get_medications", "get_tracking_overview":
                tools.append(AppleNoArgTool(name: spec.name, description: spec.description, toolbox: toolbox))
            case "propose_log_symptom":
                tools.append(AppleLogSymptomTool(toolbox: toolbox))
            case "propose_log_checkin":
                tools.append(AppleLogCheckInTool(toolbox: toolbox))
            default:
                // The remaining read tools all take just a day window.
                tools.append(AppleDaysTool(name: spec.name, description: spec.description, toolbox: toolbox))
            }
        }
        return tools
    }
}
#endif
