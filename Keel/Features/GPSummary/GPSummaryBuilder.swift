import Foundation

/// One symptom's recorded counts for the summary period and the equal-length window
/// immediately before it. Pure input, so the builder never touches the store and the
/// rules are testable on a fixed calendar. `meanSeverity`/`lastLogged` are only used
/// to break ranking ties; nothing here is scored or shown as severity.
struct GPSymptomStat: Equatable {
    let name: String
    let isCustom: Bool
    let daysThisPeriod: Int
    let daysPreviousPeriod: Int
    let meanSeverity: Double   // mean of severities SHE recorded, tie-break only
    let lastLogged: Date       // most recent log this period, tie-break only
}

/// The "Symptoms I logged most often" section, ready to render.
struct GPSymptomTable: Equatable {
    struct Row: Equatable {
        let name: String              // full label; the PDF truncates to 40 at render
        let thisPeriod: String        // "31 of 64 check-in days" (denominator required)
        let previousPeriod: String?   // "20 of 48 check-in days"; nil when column hidden
    }
    let rows: [Row]
    let showsPreviousColumn: Bool
    /// Printed in place of the previous column when the earlier window is too sparse.
    let previousUnavailableNote: String?
    /// "Also recorded: a, b, c." for 3+ day symptoms that fell outside the top rows.
    let alsoRecorded: String?
    /// True when nothing was recorded; the PDF prints the empty-state line instead.
    var isEmpty: Bool { rows.isEmpty }
}

enum GPSummaryBuilder {
    /// Rank by days logged, then higher mean recorded severity, then most recent log,
    /// then name. Shared by the table and the review step so both order identically.
    static func ranked(_ stats: [GPSymptomStat]) -> [GPSymptomStat] {
        stats.sorted { a, b in
            if a.daysThisPeriod != b.daysThisPeriod { return a.daysThisPeriod > b.daysThisPeriod }
            if a.meanSeverity != b.meanSeverity { return a.meanSeverity > b.meanSeverity }
            if a.lastLogged != b.lastLogged { return a.lastLogged > b.lastLogged }
            return a.name.localizedCaseInsensitiveCompare(b.name) == .orderedAscending
        }
    }

    /// The symptom section, exactly per spec: ranked by days logged, then higher mean
    /// recorded severity, then most recent log; denominators are always shown; the
    /// previous-period column appears only when that window is at least half
    /// checked-in; and 3+ day symptoms outside the top rows fall to an alphabetical
    /// "Also recorded" line. No arrows, trend words or interpretation.
    static func symptomTable(
        stats: [GPSymptomStat],
        checkInDaysThisPeriod: Int,
        checkInDaysPreviousPeriod: Int,
        previousWindowDayCount: Int,
        maxRows: Int = 6
    ) -> GPSymptomTable {
        let recorded = stats.filter { $0.daysThisPeriod > 0 }
        let showPrevious = previousWindowDayCount > 0
            && Double(checkInDaysPreviousPeriod) >= 0.5 * Double(previousWindowDayCount)

        let ranked = ranked(recorded)

        let top = Array(ranked.prefix(max(maxRows, 0)))
        let rows = top.map { stat in
            GPSymptomTable.Row(
                name: stat.name,
                thisPeriod: "\(stat.daysThisPeriod) of \(checkInDaysThisPeriod) check-in days",
                previousPeriod: showPrevious
                    ? "\(stat.daysPreviousPeriod) of \(checkInDaysPreviousPeriod) check-in days"
                    : nil)
        }

        // Beyond the top rows: symptoms recorded on 3+ days, listed alphabetically.
        let overflow = ranked.dropFirst(top.count)
            .filter { $0.daysThisPeriod >= 3 }
            .map(\.name)
            .sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
        let alsoRecorded = overflow.isEmpty ? nil : "Also recorded: \(overflow.joined(separator: ", "))."

        return GPSymptomTable(
            rows: rows,
            showsPreviousColumn: showPrevious,
            previousUnavailableNote: showPrevious ? nil : GPSummaryCopy.noEarlierComparison,
            alsoRecorded: alsoRecorded)
    }
}

/// Whether she recorded bleeding or spotting between periods.
enum GPBleeding: String { case yes = "Yes", no = "No", notRecorded = "Not recorded" }

/// The page-1 "Periods and cycle" block. Never labels the cycle regular, irregular,
/// normal or abnormal, and never flags bleeding; it only restates what she recorded.
struct GPCycleBlock: Equatable {
    let lastPeriodStart: Date?        // nil renders as "not recorded"
    let periodsRecorded: Int
    let cycleLengthRange: String      // "28 to 34 days", or the not-enough note
    let flow: String?                 // most-recorded descriptor, her words
    let intermenstrualBleeding: String
    let notApplicable: String?        // her reason, only if she entered one
}

extension GPSummaryBuilder {
    static func cycleBlock(
        periodStartsInPeriod: [Date],
        lastRecordedStart: Date?,
        mostFrequentFlow: String?,
        intermenstrualBleeding: GPBleeding,
        notApplicableReason: String?,
        calendar: Calendar = .current
    ) -> GPCycleBlock {
        let starts = periodStartsInPeriod.sorted()
        // A range needs at least three recorded starts (two consecutive gaps).
        var rangeText = GPSummaryCopy.notEnoughPeriodsForRange
        if starts.count >= 3 {
            var gaps: [Int] = []
            for i in 1..<starts.count {
                if let gap = calendar.dateComponents([.day], from: starts[i - 1], to: starts[i]).day {
                    gaps.append(gap)
                }
            }
            if let lo = gaps.min(), let hi = gaps.max() {
                rangeText = lo == hi ? "\(lo) days" : "\(lo) to \(hi) days"
            }
        }
        return GPCycleBlock(
            lastPeriodStart: lastRecordedStart,
            periodsRecorded: starts.count,
            cycleLengthRange: rangeText,
            flow: mostFrequentFlow?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
            intermenstrualBleeding: intermenstrualBleeding.rawValue,
            notApplicable: notApplicableReason?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty)
    }
}

/// The page-2 "Sleep, energy and mood" lines: three plain counts, no charts, manual
/// entry only (no device data in the PDF for V1).
extension GPSummaryBuilder {
    /// "41 of 64" — nights she logged disrupted sleep, out of nights with a check-in.
    static func sleepLine(disruptedNights: Int, checkInNights: Int) -> String {
        "\(disruptedNights) of \(checkInNights)"
    }

    /// "average 4.2 of 10, from 58 entries" — mean of the values she entered. Nil when
    /// she recorded no energy at all.
    static func energyLine(mean: Double, entryCount: Int, scaleMax: Int = 10) -> String? {
        guard entryCount > 0 else { return nil }
        return "average \(String(format: "%.1f", mean)) of \(scaleMax), from \(entryCount) \(entryCount == 1 ? "entry" : "entries")"
    }

    /// The three most-logged mood labels with counts, e.g. "Okay 20, Good 12, Low 5".
    static func moodLine(topMoods: [(label: String, count: Int)]) -> String? {
        let top = Array(topMoods.prefix(3))
        guard !top.isEmpty else { return nil }
        return top.map { "\($0.label) \($0.count)" }.joined(separator: ", ")
    }
}

// MARK: - Medication tables (page 2)

/// Which of the three page-2 tables a medicine belongs in. Grouping only, never a
/// judgement: MHT is the hormonal treatment groups, other prescriptions are the rest
/// of `.treatment`, and supplements are `.supplement`.
enum GPMedCategory: Equatable { case mht, otherPrescribed, supplement }

/// One medication/supplement, already reduced from the model to the strings and dates
/// the tables print. `dose` is her free text and is printed verbatim.
struct GPMedInput: Equatable {
    let name: String
    let dose: String?          // her words, verbatim; nil renders as "not recorded"
    let frequency: String?     // schedule/timing text (other + supplement tables)
    let started: Date?         // date she entered, if any
    let doseChangedAt: Date?   // date she entered, if any
    let stoppedAt: Date?       // date she entered when she stopped it, if any
    let category: GPMedCategory
}

struct GPMedRow: Equatable {
    let col1: String   // name / treatment
    let col2: String   // dose (verbatim) or "not recorded"
    let col3: String   // MHT: started-or-last-changed (+ "Stopped [date]"); else frequency
}

struct GPMedTable: Equatable {
    let rows: [GPMedRow]
    /// "and 3 further items recorded in Keel" when the category exceeds the row cap.
    let overflowNote: String?
    var isEmpty: Bool { rows.isEmpty }
}

/// One entered change to her treatment, restated for the dated list. Never linked to a
/// symptom. The template words ("changed", "stopped", "dose") are Keel's; the medicine
/// name is her words.
struct GPTreatmentChange: Equatable {
    enum Kind: Equatable { case doseChanged, stopped }
    let date: Date          // the date she entered; printed exactly as given
    let medName: String
    let kind: Kind
    var description: String {
        switch kind {
        case .doseChanged: "changed \(medName) dose"
        case .stopped: "stopped \(medName)"
        }
    }
}

extension GPSummaryBuilder {
    /// Catalog groups that make a treatment MHT. Everything else in `.treatment` is an
    /// "other prescribed medication".
    static let mhtGroupIDs: Set<String> = ["oestrogen", "progesterone", "combined", "testosterone"]

    /// One of the three med tables. Rows print her entries verbatim; dates use the
    /// injected style so the builder stays pure and testable. Caps at `maxRows`, then
    /// appends "and [n] further items recorded in Keel" rather than dropping data.
    static func medTable(
        _ meds: [GPMedInput],
        category: GPMedCategory,
        maxRows: Int = 10,
        dateStyle: (Date) -> String
    ) -> GPMedTable {
        let inCategory = meds.filter { $0.category == category }
        let capped = Array(inCategory.prefix(maxRows))
        let rows = capped.map { med -> GPMedRow in
            let dose = med.dose?.nilIfEmpty ?? GPSummaryCopy.notRecorded
            let col3: String
            switch category {
            case .mht:
                var parts: [String] = []
                if let anchor = med.doseChangedAt ?? med.started { parts.append(dateStyle(anchor)) }
                if let stopped = med.stoppedAt { parts.append("Stopped \(dateStyle(stopped))") }
                if parts.isEmpty { parts.append(GPSummaryCopy.notRecorded) }
                col3 = parts.joined(separator: " · ")
            case .otherPrescribed, .supplement:
                col3 = med.frequency?.nilIfEmpty ?? GPSummaryCopy.notRecorded
            }
            return GPMedRow(col1: med.name, col2: dose, col3: col3)
        }
        let extra = inCategory.count - capped.count
        let note = extra > 0
            ? "and \(extra) further \(extra == 1 ? "item" : "items") recorded in Keel"
            : nil
        return GPMedTable(rows: rows, overflowNote: note)
    }

    /// The dated "Changes to my treatment" list: most recent first, capped, one line
    /// each, e.g. "14 July 2026: changed oestradiol gel dose". Just restates the
    /// events; never pairs a change with a symptom.
    static func treatmentChanges(
        _ changes: [GPTreatmentChange],
        maxRows: Int = 8,
        dateStyle: (Date) -> String
    ) -> [String] {
        changes.sorted { $0.date > $1.date }
            .prefix(maxRows)
            .map { "\(dateStyle($0.date)): \($0.description)" }
    }

    /// The compact "How this is affecting me" line, e.g. "Affecting: sleep, work or
    /// concentration, exercise. Overall impact: Significant." The fixed area labels are
    /// lower-cased; her "Other" text is passed through already in `areas` and printed
    /// verbatim. Nil when she selected nothing. Never styled as a score or scale.
    static func impactLine(areas: [String], overall: String?) -> String? {
        let cleaned = areas.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        guard !cleaned.isEmpty || overall != nil else { return nil }
        var line = ""
        if !cleaned.isEmpty { line += "Affecting: \(cleaned.joined(separator: ", "))." }
        if let overall {
            if !line.isEmpty { line += " " }
            line += "Overall impact: \(overall)."
        }
        return line
    }

    /// Whether a symptom counts toward the "nights disrupted sleep" line: the built-in
    /// sleep symptoms (Trouble sleeping, Insomnia, Restless sleep) and any custom label
    /// she named with "sleep". A mechanical name match, never an inference about quality.
    static func isSleepDisruptionSymptom(name: String) -> Bool {
        let lowered = name.lowercased()
        return lowered.contains("sleep") || lowered == "insomnia"
    }

    /// The "Bleeding or spotting between periods" line, decided mechanically from what
    /// she recorded, never interpreted. A spotting day counts as "between periods" only
    /// when neither the day before nor after is a menstruation day (adjacency, not a
    /// pattern). Nothing here labels bleeding heavy, unexpected or abnormal.
    /// - Yes: at least one standalone spotting day.
    /// - No: she recorded cycle data but no standalone spotting.
    /// - Not recorded: no cycle entries at all.
    static func interPeriodBleeding(
        menstruationDays: [Date],
        spottingDays: [Date],
        calendar: Calendar = .current
    ) -> GPBleeding {
        if menstruationDays.isEmpty && spottingDays.isEmpty { return .notRecorded }
        let period = Set(menstruationDays.map { calendar.startOfDay(for: $0) })
        for spot in spottingDays {
            let day = calendar.startOfDay(for: spot)
            let before = calendar.date(byAdding: .day, value: -1, to: day)!
            let after = calendar.date(byAdding: .day, value: 1, to: day)!
            if !period.contains(day) && !period.contains(before) && !period.contains(after) {
                return .yes
            }
        }
        return .no
    }
}
