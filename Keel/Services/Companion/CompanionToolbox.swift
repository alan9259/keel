import Foundation

/// One tool the agent can call, described once and shared by both engines. Gemini
/// consumes `parameters` as a function declaration; the Apple Intelligence engine
/// wraps each in a Foundation Models `Tool` that delegates to `run`.
struct AgentToolSpec {
    let name: String
    let description: String
    /// JSON Schema object (`type`/`properties`/`required`) for the arguments.
    let parameters: [String: Any]
}

/// The companion's tools over her Keel data. Read tools reflect her data back;
/// write tools never mutate, they queue a `CompanionProposals` card she confirms.
///
/// All work happens on the main actor because the repositories and SwiftData
/// context do. Engines `await` `run(name:arguments:)` from their own tasks.
@MainActor
final class CompanionToolbox {
    let data: CompanionDataService
    let proposals: CompanionProposals

    init(data: CompanionDataService, proposals: CompanionProposals) {
        self.data = data
        self.proposals = proposals
    }

    // MARK: Specs

    var specs: [AgentToolSpec] {
        [
            AgentToolSpec(
                name: "get_recent_checkins",
                description: "Her recent daily check-ins: date, mood, energy, any note, and symptoms logged with severity. Use before reflecting on how she has been.",
                parameters: daysSchema(defaultHint: 14)
            ),
            AgentToolSpec(
                name: "get_symptom_trends",
                description: "How often each symptom appeared over a window, most frequent first, with the day it was last logged. Use to spot what has been recurring.",
                parameters: daysSchema(defaultHint: 30)
            ),
            AgentToolSpec(
                name: "get_sleep_and_energy",
                description: "Her day-by-day energy and logged sleep hours over a window, with averages. Use to see how sleep and energy have been moving.",
                parameters: daysSchema(defaultHint: 14)
            ),
            AgentToolSpec(
                name: "get_medications",
                description: "Her active medications and supplements: name, dose, schedule, and how much of the schedule she has taken recently. Never rank or judge what she takes.",
                parameters: emptySchema()
            ),
            AgentToolSpec(
                name: "get_cycle_summary",
                description: "Recent period events, her last period start, and a gentle estimated cycle phase. Phases are estimates, not predictions.",
                parameters: daysSchema(defaultHint: 90)
            ),
            AgentToolSpec(
                name: "get_tracking_overview",
                description: "How long she has been tracking, how many check-ins and active medications she has, and her chosen pathway. Good for orienting at the start.",
                parameters: emptySchema()
            ),
            AgentToolSpec(
                name: "build_gp_report",
                description: "Build a plain-text summary of her recent data she can take to her GP: check-ins, average energy, symptom-free days, sleep, medications, most-reported symptoms.",
                parameters: daysSchema(defaultHint: 30)
            ),
            AgentToolSpec(
                name: "propose_log_symptom",
                description: "Draft a card for her to confirm logging one symptom on a check-in. Call this whenever she asks to add, log, note, or record a symptom. Does NOT save anything until she confirms. Never use for anything in the safety section.",
                parameters: [
                    "type": "object",
                    "properties": [
                        "name": ["type": "string", "description": "The symptom, in her words, e.g. 'hot flushes'."],
                        "severity": ["type": "string", "enum": ["mild", "moderate", "severe"],
                                     "description": "How strongly she felt it. Defaults to mild if unsure."],
                    ],
                    "required": ["name"],
                ]
            ),
            AgentToolSpec(
                name: "propose_log_checkin",
                description: "Draft a card for her to confirm saving a check-in (also called an entry, or a log) for today. Call this whenever she asks to add, log, record, or save an entry or check-in, even if she has not given her mood or energy yet: leave those blank and she can fill them in. Does NOT save anything until she confirms.",
                parameters: [
                    "type": "object",
                    "properties": [
                        "mood": ["type": "string", "enum": Mood.allCases.map(\.rawValue),
                                 "description": "Her mood, only if she has said."],
                        "energy": ["type": "integer", "minimum": 0, "maximum": 100,
                                   "description": "Energy 0 to 100, only if she has indicated it."],
                        "note": ["type": "string", "description": "A short note in her words, optional."],
                        "symptoms": ["type": "array", "items": ["type": "string"],
                                     "description": "Symptom names she mentioned, optional."],
                    ],
                    "required": [],
                ]
            ),
        ]
    }

    private func daysSchema(defaultHint: Int) -> [String: Any] {
        [
            "type": "object",
            "properties": [
                "days": ["type": "integer", "minimum": 1, "maximum": 365,
                         "description": "How many days back to include (defaults to \(defaultHint))."],
            ],
            "required": [],
        ]
    }

    private func emptySchema() -> [String: Any] {
        ["type": "object", "properties": [String: Any](), "required": [String]()]
    }

    // MARK: Dispatch

    /// Run a tool by name and return a JSON (or short status) string for the model.
    func run(name: String, arguments: [String: Any]) async -> String {
        switch name {
        case "get_recent_checkins":
            return encode(data.recentCheckIns(days: intArg(arguments["days"], default: 14)))
        case "get_symptom_trends":
            return encode(data.symptomTrends(days: intArg(arguments["days"], default: 30)))
        case "get_sleep_and_energy":
            return encode(data.sleepAndEnergy(days: intArg(arguments["days"], default: 14)))
        case "get_medications":
            return encode(data.medicationSummaries())
        case "get_cycle_summary":
            return encode(data.cycleSummary(days: intArg(arguments["days"], default: 90)))
        case "get_tracking_overview":
            return encode(data.trackingOverview())
        case "build_gp_report":
            return data.gpReport(days: intArg(arguments["days"], default: 30))

        case "propose_log_symptom":
            guard let symptomName = stringArg(arguments["name"])?.nilIfEmpty else {
                return "No symptom name was given, so nothing was drafted."
            }
            let severity = SymptomSeverity.from(label: stringArg(arguments["severity"]))
            let proposal = proposals.propose(.logSymptom(name: symptomName, severity: severity, date: .now))
            return "Drafted a confirmation card for her: \(proposal.summary) It is not saved until she confirms."

        case "propose_log_checkin":
            let mood = stringArg(arguments["mood"]).flatMap(Mood.init(rawValue:))
            let energy = arguments["energy"].map { intArg($0, default: EnergyLevel.okay.percent) }
            let note = stringArg(arguments["note"])?.nilIfEmpty
            let symptomNames = stringArrayArg(arguments["symptoms"])
            let proposal = proposals.propose(.logCheckIn(mood: mood, energy: energy, note: note, symptoms: symptomNames))
            return "Drafted a confirmation card for her: \(proposal.summary) It is not saved until she confirms."

        default:
            return "Unknown tool: \(name)."
        }
    }

    // MARK: Encoding + argument coercion

    private func encode<T: Encodable>(_ value: T) -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        guard let data = try? encoder.encode(value), let string = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return string
    }

    private func intArg(_ value: Any?, default fallback: Int) -> Int {
        switch value {
        case let n as Int: return n
        case let d as Double: return Int(d)
        case let n as NSNumber: return n.intValue
        case let s as String: return Int(s) ?? fallback
        default: return fallback
        }
    }

    private func stringArg(_ value: Any?) -> String? {
        switch value {
        case let s as String: return s
        case let n as NSNumber: return n.stringValue
        default: return nil
        }
    }

    private func stringArrayArg(_ value: Any?) -> [String] {
        switch value {
        case let array as [Any]: return array.compactMap { stringArg($0) }
        case let s as String: return [s]
        default: return []
        }
    }
}

private extension SymptomSeverity {
    /// Map a free-text level ("mild"/"moderate"/"severe") to a stored severity,
    /// defaulting to mild.
    static func from(label: String?) -> Int {
        switch label?.lowercased() {
        case "severe": return SymptomSeverity.severe.rawValue
        case "moderate": return SymptomSeverity.moderate.rawValue
        default: return SymptomSeverity.mild.rawValue
        }
    }
}
