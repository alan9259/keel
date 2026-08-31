import Foundation

/// The time span the summary covers. Default is twelve weeks (spec). Custom is her
/// own two dates. The window it produces also carries the equal-length window
/// immediately before it, for the symptom section's "previous period" column.
enum GPPeriod: Equatable, Hashable {
    case fourWeeks, eightWeeks, twelveWeeks, sixMonths
    case custom(start: Date, end: Date)

    static let `default`: GPPeriod = .twelveWeeks

    /// The pickable presets, in spec order. Custom is offered separately in the UI.
    static let presets: [GPPeriod] = [.fourWeeks, .eightWeeks, .twelveWeeks, .sixMonths]

    var label: String {
        switch self {
        case .fourWeeks: "4 weeks"
        case .eightWeeks: "8 weeks"
        case .twelveWeeks: "12 weeks"
        case .sixMonths: "6 months"
        case .custom: "Custom"
        }
    }

    func window(today: Date, calendar: Calendar) -> GPDateWindow {
        let end = calendar.startOfDay(for: today)
        let start: Date
        switch self {
        case .fourWeeks:   start = calendar.date(byAdding: .day, value: -27, to: end)!
        case .eightWeeks:  start = calendar.date(byAdding: .day, value: -55, to: end)!
        case .twelveWeeks: start = calendar.date(byAdding: .day, value: -83, to: end)!
        case .sixMonths:   start = calendar.date(byAdding: .month, value: -6, to: end)!
        case let .custom(s, e):
            return GPDateWindow(start: calendar.startOfDay(for: min(s, e)),
                                end: calendar.startOfDay(for: max(s, e)), calendar: calendar)
        }
        return GPDateWindow(start: start, end: end, calendar: calendar)
    }
}

/// An inclusive day range plus the equal-length range immediately before it.
struct GPDateWindow: Equatable {
    let start: Date          // inclusive, start of day
    let end: Date            // inclusive, start of day
    let dayCount: Int        // inclusive day count
    let previousStart: Date  // inclusive
    let previousEnd: Date     // inclusive

    init(start: Date, end: Date, calendar: Calendar) {
        self.start = start
        self.end = end
        let count = (calendar.dateComponents([.day], from: start, to: end).day ?? 0) + 1
        self.dayCount = max(count, 1)
        self.previousEnd = calendar.date(byAdding: .day, value: -1, to: start)!
        self.previousStart = calendar.date(byAdding: .day, value: -self.dayCount, to: start)!
    }

    func contains(_ date: Date, calendar: Calendar) -> Bool {
        let d = calendar.startOfDay(for: date)
        return d >= start && d <= end
    }

    func previousContains(_ date: Date, calendar: Calendar) -> Bool {
        let d = calendar.startOfDay(for: date)
        return d >= previousStart && d <= previousEnd
    }
}

/// The auto-populated sections she can remove at the review step. Period dates and
/// check-in counts are the identifying frame and stay in; everything here is optional.
enum GPSummarySection: Hashable, CaseIterable {
    case cycle, sleep, energy, mood
}

/// Everything she adds at step 4, plus what she chose to remove at step 3. Kept
/// separate from the store-derived data so the builder never invents any of it.
struct GPSummaryInputs: Equatable {
    var period: GPPeriod = .default
    var includeName: Bool = false        // off by default (spec)
    var includeAge: Bool = false         // off by default (spec)
    var priorities: [String] = []        // up to 3, her words, in her order
    var impactAreas: [String] = []       // selected fixed labels
    var impactOther: String = ""         // her 60-char free text (verbatim)
    var impactOverall: String? = nil     // "Mild" / "Moderate" / "Significant"
    var questions: [String] = []         // up to 3, her words
    var removedSymptomNames: Set<String> = []  // rows she removed at step 3
    var removedMedIDs: Set<UUID> = []          // med/supplement rows she removed
    var removedSections: Set<GPSummarySection> = []  // cycle/sleep/energy/mood she left off
}

/// The fully-resolved summary, ready to render. The preview and the PDF both read
/// from this one value so they cannot drift (spec: preview pixel-identical to PDF).
/// Every field is either retrieved from her records or typed by her; nothing inferred.
struct GPSummaryDocument: Equatable {
    // About me
    var name: String?
    var age: Int?
    var periodLabel: String       // "18 May to 16 August 2026"
    var checkInsLabel: String     // "64 of 90 days"

    // What I most want help with today
    var priorities: [String]

    // Symptoms I logged most often — kept as stats so the renderer can reduce rows
    // to fit page 1 (6 -> 5 -> 4) without losing data to the "Also recorded" line.
    var symptomStats: [GPSymptomStat]
    var checkInDaysThisPeriod: Int
    var checkInDaysPreviousPeriod: Int
    var previousWindowDayCount: Int
    /// 6 normally; 5 when all three priority lines are used (spec overflow rule).
    var defaultSymptomMaxRows: Int

    // How this is affecting me
    var impactLine: String?

    // Periods and cycle
    var cycle: GPCycleBlock

    // Page 2: treatment
    var mht: GPMedTable
    var otherMeds: GPMedTable
    var supplements: GPMedTable
    var treatmentChanges: [String]

    // Sleep, energy and mood
    var sleepLine: String
    var energyLine: String?
    var moodLine: String?

    // Questions I want to discuss
    var questions: [String]

    var generatedOn: Date

    // Removable auto-populated sections (default in; the review step can turn any off).
    var includeCycle: Bool = true
    var includeSleep: Bool = true
    var includeEnergy: Bool = true
    var includeMood: Bool = true

    /// The symptom section at a chosen row cap. The renderer calls this with the
    /// number of rows that fit; the preview calls it the same way.
    func symptomTable(maxRows: Int) -> GPSymptomTable {
        GPSummaryBuilder.symptomTable(
            stats: symptomStats,
            checkInDaysThisPeriod: checkInDaysThisPeriod,
            checkInDaysPreviousPeriod: checkInDaysPreviousPeriod,
            previousWindowDayCount: previousWindowDayCount,
            maxRows: maxRows)
    }
}
