import Foundation

/// Drives the GP Visit Summary flow: which step she is on, the inputs she builds
/// (period, removed rows, priorities, impact, questions, name/age opt-in) and the
/// resolved document/PDF. All the rules live in the tested service and builder; this
/// only holds UI state and calls them.
@MainActor @Observable
final class GPSummaryFlowModel {
    enum Step: Int, CaseIterable { case period, review, details, preview }

    var step: Step = .period
    var inputs = GPSummaryInputs()

    // Custom-range dates, only used when the period is `.custom`.
    var customStart: Date
    var customEnd: Date

    // Everything the chosen period found, before any removal (for the review step).
    private(set) var candidateSymptoms: [GPSymptomStat] = []
    private(set) var reviewMeds: [(id: UUID, name: String, category: GPMedCategory)] = []
    /// Check-in days in the chosen window, so the review can show the same
    /// "X of Y check-in days" wording the PDF uses.
    private(set) var candidateCheckInDays: Int = 0

    // Three fixed slots each; blanks are dropped and the list capped when built.
    var priorityDrafts = ["", "", ""]
    var questionDrafts = ["", "", ""]

    let hasName: Bool
    let hasAge: Bool

    private let service: GPSummaryService

    init(service: GPSummaryService, today: Date = .now) {
        self.service = service
        self.customEnd = today.startOfDay
        self.customStart = today.startOfDay.adding(days: -83)
        let profile = service.profileNameAndAge()
        self.hasName = profile.name != nil
        self.hasAge = profile.age != nil
        refreshCandidates()
        #if DEBUG
        if let debugStep = DebugHarness.gpInitialStep { step = debugStep }
        #endif
    }

    // MARK: Period

    func selectPreset(_ period: GPPeriod) {
        inputs.period = period
        refreshCandidates()
    }

    func setCustomRange() {
        inputs.period = .custom(start: customStart, end: customEnd)
        refreshCandidates()
    }

    var isCustom: Bool { if case .custom = inputs.period { return true } else { return false } }

    /// The candidate lists depend only on the period, so rebuild them when it changes.
    private func refreshCandidates() {
        var base = inputs
        base.removedSymptomNames = []
        base.removedMedIDs = []
        let document = service.makeDocument(inputs: base)
        candidateSymptoms = GPSummaryBuilder.ranked(document.symptomStats)
        candidateCheckInDays = document.checkInDaysThisPeriod
        reviewMeds = service.reviewMeds()
    }

    // MARK: Review toggles

    func isSymptomIncluded(_ name: String) -> Bool { !inputs.removedSymptomNames.contains(name) }
    func toggleSymptom(_ name: String) {
        if inputs.removedSymptomNames.contains(name) { inputs.removedSymptomNames.remove(name) }
        else { inputs.removedSymptomNames.insert(name) }
    }

    func isMedIncluded(_ id: UUID) -> Bool { !inputs.removedMedIDs.contains(id) }
    func toggleMed(_ id: UUID) {
        if inputs.removedMedIDs.contains(id) { inputs.removedMedIDs.remove(id) }
        else { inputs.removedMedIDs.insert(id) }
    }

    func isSectionIncluded(_ section: GPSummarySection) -> Bool { !inputs.removedSections.contains(section) }
    func toggleSection(_ section: GPSummarySection) {
        if inputs.removedSections.contains(section) { inputs.removedSections.remove(section) }
        else { inputs.removedSections.insert(section) }
    }

    // MARK: Impact

    func isAreaSelected(_ area: String) -> Bool { inputs.impactAreas.contains(area) }
    func toggleArea(_ area: String) {
        if let i = inputs.impactAreas.firstIndex(of: area) { inputs.impactAreas.remove(at: i) }
        else { inputs.impactAreas.append(area) }
    }

    // MARK: Build

    /// Commit the free-text drafts and resolve the document from her records.
    func document() -> GPSummaryDocument {
        inputs.priorities = priorityDrafts
        inputs.questions = questionDrafts
        return service.makeDocument(inputs: inputs)
    }

    func pdfData() -> Data { GPSummaryPDFRenderer(document: document()).render() }

    // MARK: Navigation

    var isFirst: Bool { step == .period }
    var isLast: Bool { step == .preview }
    func next() { if let s = Step(rawValue: step.rawValue + 1) { step = s } }
    func back() { if let s = Step(rawValue: step.rawValue - 1) { step = s } }
}
