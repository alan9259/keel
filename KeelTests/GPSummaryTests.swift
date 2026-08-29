import XCTest
@testable import Keel

/// The GP Visit Summary's deterministic core: the symptom section's ranking,
/// denominators, previous-period rule and overflow, plus the banned-verb guard over
/// Keel-generated copy.
final class GPSummaryTests: XCTestCase {

    private let cal = TestStore.utcCalendar
    private let base = Date(timeIntervalSince1970: 1_600_000_000)
    private func d(_ offset: Int) -> Date { cal.date(byAdding: .day, value: offset, to: base)! }

    private func stat(_ name: String, this: Int, prev: Int = 0, sev: Double = 2,
                      last: Int = 0, custom: Bool = false) -> GPSymptomStat {
        GPSymptomStat(name: name, isCustom: custom, daysThisPeriod: this,
                      daysPreviousPeriod: prev, meanSeverity: sev, lastLogged: d(last))
    }

    // MARK: Symptom section

    func testRanksByDaysThenSeverityThenRecency() {
        let stats = [
            stat("A", this: 5, sev: 1, last: 1),
            stat("B", this: 10, sev: 1, last: 1),  // most days
            stat("C", this: 5, sev: 3, last: 1),   // ties A on days, higher severity
            stat("D", this: 5, sev: 1, last: 5),   // ties A on days + severity, more recent
        ]
        let table = GPSummaryBuilder.symptomTable(
            stats: stats, checkInDaysThisPeriod: 20, checkInDaysPreviousPeriod: 0, previousWindowDayCount: 0)
        XCTAssertEqual(table.rows.map(\.name), ["B", "C", "D", "A"])
    }

    func testDenominatorsAreAlwaysShown() {
        let table = GPSummaryBuilder.symptomTable(
            stats: [stat("Trouble sleeping", this: 31, prev: 20)],
            checkInDaysThisPeriod: 64, checkInDaysPreviousPeriod: 48, previousWindowDayCount: 60)
        XCTAssertEqual(table.rows.first?.thisPeriod, "31 of 64 check-in days")
        XCTAssertEqual(table.rows.first?.previousPeriod, "20 of 48 check-in days")
    }

    func testPreviousColumnHiddenWhenEarlierWindowUnderHalf() {
        // 20 of 60 earlier days is 33%, under the 50% bar.
        let table = GPSummaryBuilder.symptomTable(
            stats: [stat("A", this: 3, prev: 2)],
            checkInDaysThisPeriod: 30, checkInDaysPreviousPeriod: 20, previousWindowDayCount: 60)
        XCTAssertFalse(table.showsPreviousColumn)
        XCTAssertNil(table.rows.first?.previousPeriod)
        XCTAssertEqual(table.previousUnavailableNote, "Not enough earlier records to compare.")
    }

    func testPreviousColumnShownAtExactlyHalf() {
        let table = GPSummaryBuilder.symptomTable(
            stats: [stat("A", this: 3, prev: 2)],
            checkInDaysThisPeriod: 30, checkInDaysPreviousPeriod: 30, previousWindowDayCount: 60)
        XCTAssertTrue(table.showsPreviousColumn)
        XCTAssertNil(table.previousUnavailableNote)
    }

    func testTopSixWithAlsoRecordedAlphabetical() {
        let stats = [
            stat("Zed", this: 20), stat("Yan", this: 19), stat("Xer", this: 18),
            stat("Wme", this: 17), stat("Vim", this: 16), stat("Uno", this: 15),
            stat("Bee", this: 4), stat("Ant", this: 3),  // outside top 6, 3+ days
            stat("Low", this: 2),                         // under 3 days, omitted
        ]
        let table = GPSummaryBuilder.symptomTable(
            stats: stats, checkInDaysThisPeriod: 30, checkInDaysPreviousPeriod: 0, previousWindowDayCount: 0)
        XCTAssertEqual(table.rows.count, 6)
        XCTAssertEqual(table.alsoRecorded, "Also recorded: Ant, Bee.")
    }

    func testReducingRowsBumpsTheSixthIntoAlsoRecorded() {
        let stats = (1...6).map { stat("S\($0)", this: 20 - $0) }  // S1..S6, decreasing, all 3+ days
        let table = GPSummaryBuilder.symptomTable(
            stats: stats, checkInDaysThisPeriod: 30, checkInDaysPreviousPeriod: 0, previousWindowDayCount: 0, maxRows: 5)
        XCTAssertEqual(table.rows.count, 5)
        XCTAssertEqual(table.alsoRecorded, "Also recorded: S6.")
    }

    func testEmptyStateWhenNothingRecorded() {
        let table = GPSummaryBuilder.symptomTable(
            stats: [stat("A", this: 0)], checkInDaysThisPeriod: 10, checkInDaysPreviousPeriod: 0, previousWindowDayCount: 0)
        XCTAssertTrue(table.isEmpty)
        XCTAssertNil(table.alsoRecorded)
    }

    // MARK: Cycle block

    func testCycleLengthRangeNeedsThreeStarts() {
        let two = GPSummaryBuilder.cycleBlock(
            periodStartsInPeriod: [d(0), d(28)], lastRecordedStart: d(28),
            mostFrequentFlow: "Medium", intermenstrualBleeding: .no, notApplicableReason: nil, calendar: cal)
        XCTAssertEqual(two.periodsRecorded, 2)
        XCTAssertEqual(two.cycleLengthRange, "not enough recorded periods to show a range")

        let three = GPSummaryBuilder.cycleBlock(
            periodStartsInPeriod: [d(0), d(28), d(62)], lastRecordedStart: d(62),
            mostFrequentFlow: "Medium", intermenstrualBleeding: .yes, notApplicableReason: nil, calendar: cal)
        XCTAssertEqual(three.periodsRecorded, 3)
        XCTAssertEqual(three.cycleLengthRange, "28 to 34 days")   // gaps 28 and 34
        XCTAssertEqual(three.intermenstrualBleeding, "Yes")
        XCTAssertEqual(three.flow, "Medium")
    }

    func testCycleLengthEqualGapsReadAsSingleNumber() {
        let block = GPSummaryBuilder.cycleBlock(
            periodStartsInPeriod: [d(0), d(28), d(56)], lastRecordedStart: d(56),
            mostFrequentFlow: nil, intermenstrualBleeding: .notRecorded, notApplicableReason: "   ", calendar: cal)
        XCTAssertEqual(block.cycleLengthRange, "28 days")
        XCTAssertNil(block.notApplicable, "whitespace-only reason is treated as none")
        XCTAssertNil(block.flow)
    }

    // MARK: Sleep, energy and mood

    func testSleepAndEnergyAndMoodLines() {
        XCTAssertEqual(GPSummaryBuilder.sleepLine(disruptedNights: 41, checkInNights: 64), "41 of 64")

        XCTAssertEqual(GPSummaryBuilder.energyLine(mean: 4.2, entryCount: 58), "average 4.2 of 10, from 58 entries")
        XCTAssertEqual(GPSummaryBuilder.energyLine(mean: 5, entryCount: 1), "average 5.0 of 10, from 1 entry")
        XCTAssertNil(GPSummaryBuilder.energyLine(mean: 0, entryCount: 0))

        XCTAssertEqual(GPSummaryBuilder.moodLine(topMoods: [("Okay", 20), ("Good", 12), ("Low", 5), ("Great", 1)]),
                       "Okay 20, Good 12, Low 5")
        XCTAssertNil(GPSummaryBuilder.moodLine(topMoods: []))
    }

    // MARK: Medication tables

    /// Deterministic "d MMMM yyyy" so date assertions don't depend on locale or zone.
    private func styled(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        f.dateFormat = "d MMMM yyyy"
        return f.string(from: date)
    }

    private func med(_ name: String, dose: String? = "1 pump", freq: String? = "Daily",
                     started: Date? = nil, changed: Date? = nil, stopped: Date? = nil,
                     _ cat: GPMedCategory) -> GPMedInput {
        GPMedInput(name: name, dose: dose, frequency: freq,
                   started: started, doseChangedAt: changed, stoppedAt: stopped, category: cat)
    }

    func testMHTTableUsesStartedOrLastChangedAndStopped() {
        let meds = [
            med("Oestrogel", dose: "2 pumps", started: d(0), changed: d(30), .mht),  // last-changed wins
            med("Utrogestan", dose: "100mg", started: d(5), .mht),                    // started only
            med("Testosterone", dose: "as directed", started: d(2), stopped: d(40), .mht),
            med("Vitamin D", .supplement),  // filtered out of the MHT table
        ]
        let table = GPSummaryBuilder.medTable(meds, category: .mht, dateStyle: styled)
        XCTAssertEqual(table.rows.count, 3)
        XCTAssertEqual(table.rows[0].col2, "2 pumps")                 // dose verbatim
        XCTAssertEqual(table.rows[0].col3, styled(d(30)))            // last changed, not started
        XCTAssertEqual(table.rows[1].col3, styled(d(5)))            // started only
        XCTAssertEqual(table.rows[2].col3, "\(styled(d(2))) · Stopped \(styled(d(40)))")
    }

    func testOtherAndSupplementTablesUseFrequencyAndNotRecorded() {
        let meds = [
            med("Sertraline", dose: nil, freq: "Once daily", .otherPrescribed),
            med("Magnesium", dose: "400mg", freq: nil, .supplement),
        ]
        let other = GPSummaryBuilder.medTable(meds, category: .otherPrescribed, dateStyle: styled)
        XCTAssertEqual(other.rows.first?.col2, "not recorded")   // no dose entered
        XCTAssertEqual(other.rows.first?.col3, "Once daily")     // frequency, not a date

        let supp = GPSummaryBuilder.medTable(meds, category: .supplement, dateStyle: styled)
        XCTAssertEqual(supp.rows.first?.col2, "400mg")
        XCTAssertEqual(supp.rows.first?.col3, "not recorded")    // no frequency entered
    }

    func testMedTableCapsAtTenWithFurtherItemsNote() {
        let meds = (1...12).map { med("S\($0)", .supplement) }
        let table = GPSummaryBuilder.medTable(meds, category: .supplement, dateStyle: styled)
        XCTAssertEqual(table.rows.count, 10)
        XCTAssertEqual(table.overflowNote, "and 2 further items recorded in Keel")

        let eleven = GPSummaryBuilder.medTable((1...11).map { med("S\($0)", .supplement) },
                                               category: .supplement, dateStyle: styled)
        XCTAssertEqual(eleven.overflowNote, "and 1 further item recorded in Keel")
    }

    func testTreatmentChangesMostRecentFirstCappedAtEight() {
        let changes = [
            GPTreatmentChange(date: d(10), medName: "oestradiol gel", kind: .doseChanged),
            GPTreatmentChange(date: d(30), medName: "magnesium", kind: .stopped),
            GPTreatmentChange(date: d(20), medName: "progesterone", kind: .doseChanged),
        ]
        let lines = GPSummaryBuilder.treatmentChanges(changes, dateStyle: styled)
        XCTAssertEqual(lines, [
            "\(styled(d(30))): stopped magnesium",
            "\(styled(d(20))): changed progesterone dose",
            "\(styled(d(10))): changed oestradiol gel dose",
        ])

        let many = (1...10).map { GPTreatmentChange(date: d($0), medName: "m\($0)", kind: .doseChanged) }
        XCTAssertEqual(GPSummaryBuilder.treatmentChanges(many, dateStyle: styled).count, 8)
    }

    // MARK: Banned-verb guard

    func testGeneratedCopyHasNoBannedVerb() {
        for string in GPSummaryCopy.lintableStrings {
            XCTAssertFalse(GPSummaryCopy.containsBannedVerb(string), "banned verb in: \(string)")
        }
    }

    func testBannedVerbDetection() {
        XCTAssertTrue(GPSummaryCopy.containsBannedVerb("Her sleep improved this month"))
        XCTAssertTrue(GPSummaryCopy.containsBannedVerb("This suggests a pattern"))
        XCTAssertFalse(GPSummaryCopy.containsBannedVerb("31 of 64 check-in days"))
        XCTAssertFalse(GPSummaryCopy.containsBannedVerb("recorded, logged, entered, noted"))
    }
}
