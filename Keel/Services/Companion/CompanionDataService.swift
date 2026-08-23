import Foundation
import SwiftData

/// The companion's window onto her data. One reusable read + analysis layer over
/// the existing repositories, so the agent's tools (and, later, the dashboard and
/// reports views) share the same aggregation rather than each re-deriving it.
///
/// Everything here is read-only. It returns `Encodable` value types the toolbox
/// serialises straight to JSON for the model. No interpretation, no advice: it
/// reflects her own numbers back.
@MainActor
struct CompanionDataService {
    let context: ModelContext
    let checkIns: CheckInRepository
    let symptoms: SymptomRepository
    let cycle: CycleRepository
    let medications: MedicationRepository
    let users: UserRepository

    // MARK: Windowing

    /// Start of a `days`-long window that includes today.
    private func windowStart(days: Int) -> Date {
        Date.now.startOfDay.adding(days: -(max(days, 1) - 1))
    }

    private static let dayFormat: Date.FormatStyle =
        .dateTime.year().month(.abbreviated).day()

    private func label(_ date: Date) -> String { date.formatted(Self.dayFormat) }

    // MARK: Check-ins

    struct SymptomNote: Encodable, Sendable {
        let name: String
        let severity: String
    }
    struct CheckInSummary: Encodable, Sendable {
        let date: String
        let mood: String
        let energyPercent: Int
        let note: String?
        let symptoms: [SymptomNote]
    }

    func recentCheckIns(days: Int) -> [CheckInSummary] {
        let start = windowStart(days: days)
        return checkIns.all()
            .filter { $0.date >= start }
            .map { ci in
                CheckInSummary(
                    date: label(ci.date),
                    mood: ci.mood.label,
                    energyPercent: ci.energy,
                    note: ci.notes,
                    symptoms: ci.symptoms.map { s in
                        SymptomNote(name: s.name,
                                    severity: (SymptomSeverity(rawValue: severity(of: s, on: ci)) ?? .mild).label)
                    }
                )
            }
    }

    /// Severity of a symptom on one check-in (via its join row).
    private func severity(of symptom: Symptom, on checkIn: CheckIn) -> Int {
        (checkIn.symptomLinks ?? []).first { !$0.isTombstoned && $0.symptom?.id == symptom.id }?.severity ?? 1
    }

    // MARK: Symptom trends

    struct SymptomTrend: Encodable, Sendable {
        let name: String
        let category: String
        let daysLogged: Int
        let lastLogged: String
    }

    func symptomTrends(days: Int) -> [SymptomTrend] {
        let start = windowStart(days: days)
        var counts: [String: (category: String, days: Set<Date>, last: Date)] = [:]
        for ci in checkIns.all() where ci.date >= start {
            for s in ci.symptoms {
                var entry = counts[s.name] ?? (s.category.label, [], ci.date)
                entry.days.insert(ci.date.startOfDay)
                entry.last = max(entry.last, ci.date)
                counts[s.name] = entry
            }
        }
        return counts
            .map { SymptomTrend(name: $0.key, category: $0.value.category,
                                daysLogged: $0.value.days.count, lastLogged: label($0.value.last)) }
            .sorted { $0.daysLogged > $1.daysLogged }
    }

    // MARK: Sleep and energy

    struct DayPoint: Encodable, Sendable {
        let date: String
        let energyPercent: Int?
        let sleepHours: Double?
    }
    struct SleepEnergyReport: Encodable, Sendable {
        let series: [DayPoint]
        let averageEnergyPercent: Int?
        let averageSleepHours: Double?
    }

    func sleepAndEnergy(days: Int) -> SleepEnergyReport {
        let span = max(days, 1)
        let dayStarts = (0..<span).map { Date.now.startOfDay.adding(days: -($0)) }.reversed()
        let all = checkIns.all()
        let sleepLogs = activityLogs(activityID: "sleep", since: windowStart(days: span))

        var points: [DayPoint] = []
        var energies: [Int] = []
        var sleeps: [Double] = []
        for day in dayStarts {
            let dayEnergies = all.filter { $0.date.isSameDay(as: day) }.map(\.energy)
            let energy = dayEnergies.isEmpty ? nil : dayEnergies.reduce(0, +) / dayEnergies.count
            let sleep = sleepLogs.first { $0.date.isSameDay(as: day) }?.amount
            if let energy { energies.append(energy) }
            if let sleep, sleep > 0 { sleeps.append(sleep) }
            points.append(DayPoint(date: label(day), energyPercent: energy,
                                   sleepHours: (sleep ?? 0) > 0 ? sleep : nil))
        }
        return SleepEnergyReport(
            series: points,
            averageEnergyPercent: energies.isEmpty ? nil : energies.reduce(0, +) / energies.count,
            averageSleepHours: sleeps.isEmpty ? nil : (sleeps.reduce(0, +) / Double(sleeps.count) * 10).rounded() / 10
        )
    }

    // MARK: Medications

    struct MedicationSummary: Encodable, Sendable {
        let name: String
        let kind: String
        let dose: String
        let schedule: String
        let method: String?
        let adherencePercent: Int?
    }

    func medicationSummaries(days: Int = 7) -> [MedicationSummary] {
        let active = medications.active()
        let start = windowStart(days: days)
        return active.map { med in
            let adherence = adherencePercent(for: med, since: start, days: days)
            return MedicationSummary(
                name: med.name,
                kind: med.kind.label,
                dose: med.dosage,
                schedule: med.hasSchedule ? med.schedule.summary : med.timing,
                method: med.method?.label,
                adherencePercent: adherence
            )
        }
    }

    /// Share of scheduled doses marked taken across the window, per medication.
    private func adherencePercent(for med: Medication, since: Date, days: Int) -> Int? {
        guard med.hasSchedule, med.schedule.kind != .asNeeded else { return nil }
        let dueCount = (0..<max(days, 1)).reduce(0) { sum, offset in
            let day = Date.now.startOfDay.adding(days: -offset)
            return sum + (med.schedule.isDue(on: day) ? med.schedule.dueSlots(on: day).count : 0)
        }
        guard dueCount > 0 else { return nil }
        let medID = med.id
        let descriptor = FetchDescriptor<MedicationLog>(
            predicate: #Predicate { $0.deletedAt == nil && $0.taken == true && $0.date >= since && $0.medication?.id == medID }
        )
        let taken = (try? context.fetch(descriptor))?.count ?? 0
        return min(Int((Double(taken) / Double(dueCount) * 100).rounded()), 100)
    }

    // MARK: Cycle

    struct CycleEventSummary: Encodable, Sendable {
        let date: String
        let type: String
    }
    struct CycleSummary: Encodable, Sendable {
        let events: [CycleEventSummary]
        let lastPeriodStart: String?
        let estimatedPhase: String
        let note: String
    }

    func cycleSummary(days: Int) -> CycleSummary {
        let start = windowStart(days: days)
        let events = cycle.entries(from: start, to: .now)
            .map { CycleEventSummary(date: label($0.date), type: $0.type.rawValue) }
        let last = cycle.cycleStart(before: .now)
        return CycleSummary(
            events: events,
            lastPeriodStart: last.map(label),
            estimatedPhase: cycle.estimatedPhase(on: .now).label,
            note: "Perimenopausal cycles are irregular, so the phase is a gentle estimate, not a prediction."
        )
    }

    // MARK: Tracking overview

    struct TrackingOverview: Encodable, Sendable {
        let trackingSince: String?
        let daysTracked: Int
        let checkInCount: Int
        let activeMedicationCount: Int
        let pathway: String?
    }

    func trackingOverview() -> TrackingOverview {
        let profile = users.currentProfile()
        let all = checkIns.all()
        return TrackingOverview(
            trackingSince: profile.map { label($0.trackingStartDate) },
            daysTracked: Set(all.map { $0.date.startOfDay }).count,
            checkInCount: all.count,
            activeMedicationCount: medications.active().count,
            pathway: profile?.pathway?.title
        )
    }

    // MARK: GP report

    /// A plain-text summary she can take to her GP. Lifts the wording from
    /// `ReportsView.exportText` into one place so both can share it.
    func gpReport(days: Int) -> String {
        let start = windowStart(days: days)
        let window = checkIns.all().filter { $0.date >= start }
        let energies = window.map(\.energy)
        let avgEnergy = energies.isEmpty ? 0 : energies.reduce(0, +) / energies.count
        let symptomFreeDays = window.filter { $0.symptoms.isEmpty }.count

        // Symptom days merged from her check-ins AND Apple Health's own logs.
        let tally = symptomTally(days: days)
        let topSymptoms = tally.ranked().prefix(5)

        let sleep = sleepAndEnergy(days: days)

        var lines = ["Keel summary: last \(days) days", ""]
        lines.append("Check-ins: \(window.count)")
        lines.append("Average energy: \(avgEnergy)%")
        lines.append("Symptom-free days: \(symptomFreeDays)")
        if let s = sleep.averageSleepHours { lines.append("Average sleep: \(s) hours") }
        var vitalLines = [
            vitalReportLine(typeID: "restingHeartRate", label: "Resting heart rate", unit: "bpm", since: start),
            vitalReportLine(typeID: "hrv", label: "Heart rate variability", unit: "ms", since: start),
            vitalReportLine(typeID: "bodyMass", label: "Weight", unit: "kg", since: start, minCount: 2),
        ].compactMap { $0 }
        if let bp = bloodPressureLine(since: start) { vitalLines.append(bp) }
        if !vitalLines.isEmpty {
            lines.append("")
            lines.append("Body (from Apple Health):")
            lines.append(contentsOf: vitalLines)
        }
        let meds = medicationSummaries(days: days)
        if !meds.isEmpty {
            lines.append("")
            lines.append("Medications and supplements:")
            for m in meds {
                let adherence = m.adherencePercent.map { ", taken \($0)% of scheduled doses" } ?? ""
                lines.append("  \(m.name): \(m.dose), \(m.schedule)\(adherence)")
            }
        }
        if tally.vasomotorDays > 0 {
            lines.append("")
            lines.append("Vasomotor: hot flushes or night sweats on \(tally.vasomotorDays) of \(days) days.")
        }
        if !topSymptoms.isEmpty {
            lines.append("")
            lines.append("Most reported symptoms (days, includes Apple Health logs):")
            for s in topSymptoms { lines.append("  \(s.name): \(s.days) days") }
        }
        return lines.joined(separator: "\n")
    }

    // MARK: Symptom tally (check-ins + Apple Health's own logs)

    /// Distinct symptom days over the window, merged from her check-ins and the
    /// `symptom.*` `HealthSample` rows Keel archives for Apple Health logs on days she
    /// didn't check in. Shared shape with the Reports screen (see `SymptomTally`).
    private func symptomTally(days: Int) -> SymptomTally {
        let start = windowStart(days: days)
        var tally = SymptomTally()
        for ci in checkIns.all() where ci.date >= start {
            for symptom in ci.symptoms { tally.add(name: symptom.name, day: ci.date.startOfDay) }
        }
        let descriptor = FetchDescriptor<HealthSample>(
            predicate: #Predicate { $0.deletedAt == nil && $0.day >= start }
        )
        for sample in ((try? context.fetch(descriptor)) ?? []) {
            if let name = SymptomTally.name(fromHealthTypeID: sample.typeID) {
                tally.add(name: name, day: sample.day.startOfDay)
            }
        }
        return tally
    }

    // MARK: Vitals helper (from imported Apple Health samples)

    /// One GP-report line for an imported vital: its average and range over the
    /// window, or nil until there are at least three days. Reuses `VitalTrend`, so
    /// every figure is her own Apple Health data, never an invented one.
    private func vitalTrend(typeID: String, since: Date) -> VitalTrend {
        let start = since.startOfDay
        let descriptor = FetchDescriptor<HealthSample>(
            predicate: #Predicate<HealthSample> { $0.deletedAt == nil && $0.typeID == typeID && $0.day >= start }
        )
        let points = ((try? context.fetch(descriptor)) ?? [])
            .map { VitalTrend.Point(day: $0.day.startOfDay, value: $0.value) }
        return VitalTrend(points: points)
    }

    private func vitalReportLine(typeID: String, label: String, unit: String, since: Date, minCount: Int = 3) -> String? {
        let trend = vitalTrend(typeID: typeID, since: since)
        guard trend.count >= minCount, let avg = trend.average else { return nil }
        let v = trend.values
        var range = ""
        if let lo = v.min(), let hi = v.max(), lo != hi {
            range = " (range \(Int(lo.rounded()))–\(Int(hi.rounded())))"
        }
        return "  \(label): avg \(avg) \(unit)\(range)"
    }

    /// Systolic/diastolic together, the way a clinician reads it. Nil until she has a
    /// couple of readings (she records these with a cuff, so they're occasional).
    private func bloodPressureLine(since: Date) -> String? {
        let systolic = vitalTrend(typeID: "bloodPressureSystolic", since: since)
        let diastolic = vitalTrend(typeID: "bloodPressureDiastolic", since: since)
        guard systolic.count >= 2, diastolic.count >= 2,
              let sys = systolic.average, let dia = diastolic.average else { return nil }
        return "  Blood pressure: avg \(sys)/\(dia) mmHg"
    }

    // MARK: Activity helper (no repository exists for ActivityLog)

    private func activityLogs(activityID: String, since: Date) -> [ActivityLog] {
        let start = since.startOfDay
        let descriptor = FetchDescriptor<ActivityLog>(
            predicate: #Predicate { $0.deletedAt == nil && $0.activityID == activityID && $0.date >= start },
            sortBy: [SortDescriptor(\.date)]
        )
        return (try? context.fetch(descriptor)) ?? []
    }
}
