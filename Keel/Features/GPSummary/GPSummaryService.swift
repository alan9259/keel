import Foundation
import SwiftData

/// Reads her records for the chosen period and folds them, through the pure
/// `GPSummaryBuilder`, into a `GPSummaryDocument`. This layer only retrieves and
/// counts; every rule that could be interpreted lives in the tested builder. It never
/// writes, and it never invents a value: an un-entered date stays nil, a missing
/// section reads as its honest empty state.
///
/// Stopped treatments she dated are kept (not deleted): the MHT table shows them with
/// "Stopped [date]" and the changes list restates the stop. Dates are only ever the
/// ones she entered, never a system timestamp.
@MainActor
struct GPSummaryService {
    let context: ModelContext
    let checkIns: CheckInRepository
    let medications: MedicationRepository
    let cycle: CycleRepository
    let users: UserRepository
    var calendar: Calendar = .current

    func makeDocument(inputs: GPSummaryInputs, now: Date = .now) -> GPSummaryDocument {
        let window = inputs.period.window(today: now, calendar: calendar)
        let all = checkIns.all()
        let thisCheckIns = all.filter { window.contains($0.date, calendar: calendar) }
        let prevCheckIns = all.filter { window.previousContains($0.date, calendar: calendar) }

        let checkInDaysThis = distinctDays(thisCheckIns).count
        let checkInDaysPrev = distinctDays(prevCheckIns).count

        // Symptoms.
        let stats = symptomStats(this: thisCheckIns, prev: prevCheckIns, removed: inputs.removedSymptomNames)

        // Sleep: nights she logged a sleep-disruption symptom, of nights with a check-in.
        let disruptedNights = Set(thisCheckIns
            .filter { ci in ci.symptoms.contains { GPSummaryBuilder.isSleepDisruptionSymptom(name: $0.name) } }
            .map { calendar.startOfDay(for: $0.date) }).count
        let sleepLine = GPSummaryBuilder.sleepLine(disruptedNights: disruptedNights, checkInNights: checkInDaysThis)

        // Energy: her 0-100 level expressed out of 10 (a faithful rescale). Energy is
        // part of every check-in, so the entry count is the check-in count.
        let energies = thisCheckIns.map { Double($0.energy) / 10.0 }
        let energyMean = energies.isEmpty ? 0 : energies.reduce(0, +) / Double(energies.count)
        let energyLine = GPSummaryBuilder.energyLine(mean: energyMean, entryCount: energies.count)

        // Mood: three most-logged labels, count then better mood as the tie-break.
        let grouped: [Mood: [CheckIn]] = Dictionary(grouping: thisCheckIns, by: { $0.mood })
        let moodCounts: [(label: String, count: Int)] = grouped
            .sorted { a, b in
                a.value.count != b.value.count ? a.value.count > b.value.count : a.key.score > b.key.score
            }
            .map { (label: $0.key.label, count: $0.value.count) }
        let moodLine = GPSummaryBuilder.moodLine(topMoods: moodCounts)

        // Cycle.
        let cycleBlock = makeCycleBlock(window: window)

        // Treatment. Active meds fill the three tables; treatments she stopped during
        // the window are kept too (never deleted) so the MHT table can show them with
        // "Stopped [date]" and the changes list can restate the stop.
        let active = medications.active().filter { !inputs.removedMedIDs.contains($0.id) }
        let stoppedInWindow = medications.stoppedTreatments().filter { med in
            guard let stopped = med.stoppedAt, window.contains(stopped, calendar: calendar) else { return false }
            return !inputs.removedMedIDs.contains(med.id)
        }
        // Only MHT includes stopped items as rows (spec); a stopped other/supplement
        // still contributes a dated change but not a table row.
        let tableMeds = active + stoppedInWindow.filter { category(of: $0) == .mht }
        let medInputs = tableMeds.map(medInput(from:))
        let mht = GPSummaryBuilder.medTable(medInputs, category: .mht, dateStyle: dateStyle)
        let other = GPSummaryBuilder.medTable(medInputs, category: .otherPrescribed, dateStyle: dateStyle)
        let supplements = GPSummaryBuilder.medTable(medInputs, category: .supplement, dateStyle: dateStyle)

        // Changes: dose changes and stops she dated within the window, most recent first.
        var changeEvents: [GPTreatmentChange] = []
        for med in active + stoppedInWindow {
            if let changed = med.doseChangedAt, window.contains(changed, calendar: calendar) {
                changeEvents.append(GPTreatmentChange(date: changed, medName: med.name, kind: .doseChanged))
            }
        }
        for med in stoppedInWindow {
            if let stopped = med.stoppedAt {   // already filtered to the window and not removed
                changeEvents.append(GPTreatmentChange(date: stopped, medName: med.name, kind: .stopped))
            }
        }
        let treatmentChanges = GPSummaryBuilder.treatmentChanges(changeEvents, dateStyle: dateStyle)

        // Step-4 inputs (her words). Fixed labels lower-cased; her "other" text verbatim.
        var renderedAreas: [String] = inputs.impactAreas.map { $0.lowercased() }
        let otherText = inputs.impactOther.trimmingCharacters(in: .whitespacesAndNewlines)
        if !otherText.isEmpty { renderedAreas.append(otherText) }
        let impactLine = GPSummaryBuilder.impactLine(areas: renderedAreas, overall: inputs.impactOverall)
        let priorities = cleanLines(inputs.priorities, max: GPSummaryCopy.maxPriorities)
        let questions = cleanLines(inputs.questions, max: GPSummaryCopy.maxQuestions)

        // About me (both off by default).
        let profile = users.currentProfile()
        let name = inputs.includeName ? profile?.firstName.nilIfEmpty : nil
        let age = inputs.includeAge ? profile?.age : nil

        return GPSummaryDocument(
            name: name,
            age: age,
            periodLabel: periodLabel(window),
            checkInsLabel: "\(checkInDaysThis) of \(window.dayCount) \(window.dayCount == 1 ? "day" : "days")",
            priorities: priorities,
            symptomStats: stats,
            checkInDaysThisPeriod: checkInDaysThis,
            checkInDaysPreviousPeriod: checkInDaysPrev,
            previousWindowDayCount: window.dayCount,
            defaultSymptomMaxRows: priorities.count >= GPSummaryCopy.maxPriorities ? 5 : 6,
            impactLine: impactLine,
            cycle: cycleBlock,
            mht: mht,
            otherMeds: other,
            supplements: supplements,
            treatmentChanges: treatmentChanges,
            sleepLine: sleepLine,
            energyLine: energyLine,
            moodLine: moodLine,
            questions: questions,
            generatedOn: now,
            includeCycle: !inputs.removedSections.contains(.cycle),
            includeSleep: !inputs.removedSections.contains(.sleep),
            includeEnergy: !inputs.removedSections.contains(.energy),
            includeMood: !inputs.removedSections.contains(.mood))
    }

    // MARK: Symptoms

    private func distinctDays(_ checkIns: [CheckIn]) -> Set<Date> {
        Set(checkIns.map { calendar.startOfDay(for: $0.date) })
    }

    private struct SymptomAcc {
        var isCustom: Bool
        var days: Set<Date> = []
        var severitySum = 0
        var severityCount = 0
        var last: Date
        var previousDays: Set<Date> = []
    }

    private func symptomStats(this: [CheckIn], prev: [CheckIn], removed: Set<String>) -> [GPSymptomStat] {
        var acc: [String: SymptomAcc] = [:]
        for ci in this {
            let day = calendar.startOfDay(for: ci.date)
            for link in (ci.symptomLinks ?? []) where !link.isTombstoned {
                guard let symptom = link.symptom else { continue }
                var entry = acc[symptom.name] ?? SymptomAcc(isCustom: symptom.isCustom, last: ci.date)
                entry.days.insert(day)
                entry.severitySum += link.severity
                entry.severityCount += 1
                entry.last = max(entry.last, ci.date)
                acc[symptom.name] = entry
            }
        }
        for ci in prev {
            let day = calendar.startOfDay(for: ci.date)
            for symptom in ci.symptoms {
                var entry = acc[symptom.name] ?? SymptomAcc(isCustom: symptom.isCustom, last: ci.date)
                entry.previousDays.insert(day)
                acc[symptom.name] = entry
            }
        }
        return acc
            .filter { !removed.contains($0.key) }
            .map { name, entry in
                GPSymptomStat(
                    name: name,
                    isCustom: entry.isCustom,
                    daysThisPeriod: entry.days.count,
                    daysPreviousPeriod: entry.previousDays.count,
                    meanSeverity: entry.severityCount > 0 ? Double(entry.severitySum) / Double(entry.severityCount) : 0,
                    lastLogged: entry.last)
            }
    }

    // MARK: Cycle

    private func makeCycleBlock(window: GPDateWindow) -> GPCycleBlock {
        let entries = cycle.entries(from: window.start, to: window.end).filter { $0.type != .periodEnd }
        let menstruationDays = entries.filter { $0.flowLevel != .spotting }.map { calendar.startOfDay(for: $0.date) }
        let spottingDays = entries.filter { $0.flowLevel == .spotting }.map { calendar.startOfDay(for: $0.date) }
        let startsInWindow = CycleStats.periodStarts(fromDays: menstruationDays, calendar: calendar)

        // Most-recorded flow descriptor, in her words (the level she chose).
        let flowCounts = Dictionary(grouping: entries, by: { $0.flowLevel.label }).mapValues(\.count)
        let mostFrequentFlow = flowCounts.max { $0.value != $1.value ? $0.value < $1.value : $0.key > $1.key }?.key

        let bleeding = GPSummaryBuilder.interPeriodBleeding(
            menstruationDays: menstruationDays, spottingDays: spottingDays, calendar: calendar)

        return GPSummaryBuilder.cycleBlock(
            periodStartsInPeriod: startsInWindow,
            lastRecordedStart: cycle.stats(now: window.end).lastStart,
            mostFrequentFlow: mostFrequentFlow,
            intermenstrualBleeding: bleeding,
            notApplicableReason: users.currentProfile()?.periodsNotApplicableReason,
            calendar: calendar)
    }

    // MARK: Review

    /// The active treatments as removable rows for the review step: id (to toggle
    /// removal), her name, and which table they fall in.
    func reviewMeds() -> [(id: UUID, name: String, category: GPMedCategory)] {
        medications.active().map { (id: $0.id, name: $0.name, category: category(of: $0)) }
    }

    /// Whether a name/age exist to offer as opt-in "About me" fields.
    func profileNameAndAge() -> (name: String?, age: Int?) {
        let profile = users.currentProfile()
        return (profile?.firstName.nilIfEmpty, profile?.age)
    }

    // MARK: Medications

    private func category(of med: Medication) -> GPMedCategory {
        if med.kind == .supplement { return .supplement }
        if let group = med.catalogGroupID, GPSummaryBuilder.mhtGroupIDs.contains(group) { return .mht }
        return .otherPrescribed
    }

    private func medInput(from med: Medication) -> GPMedInput {
        GPMedInput(
            name: med.name,
            dose: med.dosage.nilIfEmpty,
            frequency: (med.hasSchedule ? med.schedule.summary : med.timing).nilIfEmpty,
            started: med.date,
            doseChangedAt: med.doseChangedAt,
            stoppedAt: med.stoppedAt,   // her entered stop date, if she stopped it
            category: category(of: med))
    }

    // MARK: Formatting

    private func periodLabel(_ window: GPDateWindow) -> String {
        let sameYear = calendar.component(.year, from: window.start) == calendar.component(.year, from: window.end)
        let start = (sameYear ? formatter("d MMMM") : formatter("d MMMM yyyy")).string(from: window.start)
        let end = formatter("d MMMM yyyy").string(from: window.end)
        return "\(start) to \(end)"
    }

    private func dateStyle(_ date: Date) -> String { formatter("d MMMM yyyy").string(from: date) }

    private func formatter(_ format: String) -> DateFormatter {
        // Fixed English (AU) locale so month names are always spelled out and the PDF
        // reads the same on any device; the injected timezone keeps a day on its day.
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_AU")
        f.timeZone = calendar.timeZone
        f.dateFormat = format
        return f
    }

    // MARK: Her free-text lines

    /// Trim, drop blanks, cap the count, keep her order. Never reorders or ranks.
    private func cleanLines(_ lines: [String], max: Int) -> [String] {
        Array(lines.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .prefix(max))
    }
}
