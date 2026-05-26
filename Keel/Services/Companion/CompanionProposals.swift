import Foundation
import SwiftData

/// A write the companion has drafted for her to confirm. Nothing is saved until
/// she taps confirm, so the model never mutates her record on its own.
struct AgentProposal: Identifiable, Sendable {
    let id: UUID
    let title: String
    /// Plain description of exactly what will be saved.
    let summary: String
    let confirmLabel: String
    let kind: Kind

    enum Kind: Sendable {
        case logSymptom(name: String, severity: Int, date: Date)
        case logCheckIn(mood: Mood?, energy: Int?, note: String?, symptoms: [String])
    }
}

/// Holds the companion's pending proposals and performs them on her confirmation.
/// `ChatView` observes `pending` and renders a confirm/dismiss card.
@MainActor
@Observable
final class CompanionProposals {
    private(set) var pending: [AgentProposal] = []

    private let context: ModelContext
    private let checkIns: CheckInRepository
    private let symptoms: SymptomRepository
    private let ownerID: OwnerIDProvider

    init(context: ModelContext, checkIns: CheckInRepository,
         symptoms: SymptomRepository, ownerID: @escaping OwnerIDProvider) {
        self.context = context
        self.checkIns = checkIns
        self.symptoms = symptoms
        self.ownerID = ownerID
    }

    /// Queue a proposal and return it, so the tool can describe it back to the model.
    @discardableResult
    func propose(_ kind: AgentProposal.Kind) -> AgentProposal {
        let proposal = Self.describe(kind)
        pending.append(proposal)
        return proposal
    }

    func dismiss(_ proposal: AgentProposal) {
        pending.removeAll { $0.id == proposal.id }
    }

    /// Perform the write, then clear the card. Reuses the existing repositories so
    /// a chat-logged entry is identical to one made in the normal UI.
    func confirm(_ proposal: AgentProposal) {
        switch proposal.kind {
        case let .logSymptom(name, severity, date):
            logSymptom(name: name, severity: severity, date: date)
        case let .logCheckIn(mood, energy, note, symptomNames):
            let picks = symptomPicks(symptomNames, severity: SymptomSeverity.mild.rawValue)
            checkIns.create(mood: mood ?? .okay, energy: energy ?? EnergyLevel.okay.percent,
                            notes: note, symptoms: picks)
        }
        pending.removeAll { $0.id == proposal.id }
    }

    // MARK: Writes

    private func logSymptom(name: String, severity: Int, date: Date) {
        let symptom = symptoms.findOrCreateCustom(name: name, category: .body)
        let owner = ownerID()
        if let checkIn = checkIn(on: date) {
            // Don't double-log the same symptom on the same check-in.
            let already = checkIn.symptomLinks.contains { !$0.isTombstoned && $0.symptom?.id == symptom.id }
            guard !already else { return }
            context.insert(CheckInSymptom(checkIn: checkIn, symptom: symptom, severity: severity, ownerID: owner))
        } else {
            // No check-in on that day yet: start a gentle, neutral one she can
            // adjust. She confirmed this, and the card said so.
            let checkIn = CheckIn(date: date, mood: .okay, energy: EnergyLevel.okay.percent, ownerID: owner)
            context.insert(checkIn)
            context.insert(CheckInSymptom(checkIn: checkIn, symptom: symptom, severity: severity, ownerID: owner))
        }
        try? context.save()
    }

    private func symptomPicks(_ names: [String], severity: Int) -> [(symptom: Symptom, severity: Int)] {
        names
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
            .map { (symptoms.findOrCreateCustom(name: $0, category: .body), severity) }
    }

    private func checkIn(on date: Date) -> CheckIn? {
        let start = date.startOfDay
        let end = start.adding(days: 1)
        let descriptor = FetchDescriptor<CheckIn>(
            predicate: #Predicate { $0.deletedAt == nil && $0.date >= start && $0.date < end },
            sortBy: [SortDescriptor(\.date, order: .reverse)]
        )
        return (try? context.fetch(descriptor))?.first
    }

    // MARK: Card copy

    private static func describe(_ kind: AgentProposal.Kind) -> AgentProposal {
        switch kind {
        case let .logSymptom(name, severity, date):
            let level = (SymptomSeverity(rawValue: severity) ?? .mild).label.lowercased()
            let when = Calendar.current.isDateInToday(date) ? "today's check-in"
                : "your check-in on \(date.formatted(.dateTime.month(.abbreviated).day()))"
            return AgentProposal(
                id: UUID(), title: "Log a symptom",
                summary: "Add \(name) (\(level)) to \(when).",
                confirmLabel: "Log it", kind: kind
            )
        case let .logCheckIn(mood, energy, note, symptoms):
            var parts: [String] = []
            if let mood { parts.append("mood \(mood.label.lowercased())") }
            if let energy { parts.append("energy \(EnergyLevel.from(percent: energy).label.lowercased())") }
            if !symptoms.isEmpty { parts.append(symptoms.joined(separator: ", ")) }
            if note != nil { parts.append("a note") }
            let detail = parts.isEmpty ? "a check-in for today" : parts.joined(separator: ", ")
            return AgentProposal(
                id: UUID(), title: "Save a check-in",
                summary: "Save \(detail).",
                confirmLabel: "Save it", kind: kind
            )
        }
    }
}
