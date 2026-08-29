import XCTest
import SwiftData
@testable import Keel

/// The GP Visit Summary's store-reading layer: given seeded check-ins, cycle entries
/// and treatments, `GPSummaryService` produces the resolved document with the right
/// counts, denominators and her-words fields. In-memory store, fixed UTC calendar and
/// a fixed `now`, so nothing depends on the wall clock.
@MainActor
final class GPSummaryServiceTests: XCTestCase {

    private var context: ModelContext!
    private var service: GPSummaryService!
    private let cal = TestStore.utcCalendar
    private let now = Date(timeIntervalSince1970: 1_700_000_000)  // fixed "today"
    private func d(_ offset: Int) -> Date { cal.startOfDay(for: cal.date(byAdding: .day, value: offset, to: now)!) }

    private func styled(_ date: Date) -> String {
        let f = DateFormatter()
        f.timeZone = cal.timeZone; f.locale = Locale(identifier: "en_AU")
        f.dateFormat = "d MMMM yyyy"
        return f.string(from: date)
    }

    override func setUpWithError() throws {
        context = TestStore.makeContext()
        service = GPSummaryService(
            context: context,
            checkIns: CheckInRepository(context: context, ownerID: TestStore.ownerID),
            medications: MedicationRepository(context: context, ownerID: TestStore.ownerID),
            cycle: CycleRepository(context: context, ownerID: TestStore.ownerID),
            users: UserRepository(context: context, ownerID: TestStore.ownerID),
            calendar: cal)
    }

    override func tearDownWithError() throws { context = nil; service = nil }

    private func symptom(_ name: String) -> Symptom {
        let s = Symptom(name: name, category: .body, isCustom: name.first == "z", ownerID: "test-owner")
        context.insert(s); try? context.save()
        return s
    }

    private func seedFullPicture() {
        let checkIns = CheckInRepository(context: context, ownerID: TestStore.ownerID)
        let sleep = symptom("Trouble sleeping")
        let flush = symptom("Hot flushes")
        _ = checkIns.create(mood: .okay, energy: 60, notes: nil, symptoms: [(sleep, 2), (flush, 3)], date: d(-2))
        _ = checkIns.create(mood: .okay, energy: 40, notes: nil, symptoms: [(flush, 2)], date: d(-5))
        _ = checkIns.create(mood: .low, energy: 80, notes: nil, symptoms: [(sleep, 1)], date: d(-10))

        let cycle = CycleRepository(context: context, ownerID: TestStore.ownerID)
        for k in [20, 19, 18] { cycle.togglePeriodDay(d(-k)) }   // a 3-day run -> one start
        cycle.setFlow(.spotting, on: d(-10))                     // standalone spotting

        let oestrogel = Medication(name: "Oestrogel", dosage: "2 pumps", timing: "Morning",
                                   kind: .treatment, catalogGroupID: "oestrogen",
                                   date: d(-100), doseChangedAt: d(-3), ownerID: "test-owner")
        let magnesium = Medication(name: "Magnesium", dosage: "400mg", timing: "Night",
                                   kind: .supplement, ownerID: "test-owner")
        context.insert(oestrogel); context.insert(magnesium)
        try? context.save()

        UserRepository(context: context, ownerID: TestStore.ownerID)
            .updateBasicInfo(firstName: "Mischa", lastName: nil, birthYear: 1977, mobile: nil, email: nil)
    }

    func testResolvesCountsSymptomsCycleAndMeds() {
        seedFullPicture()
        var inputs = GPSummaryInputs()
        inputs.period = .fourWeeks
        inputs.includeName = true
        let doc = service.makeDocument(inputs: inputs, now: now)

        // About + the flat check-in line (denominator is the whole window).
        XCTAssertEqual(doc.name, "Mischa")
        XCTAssertEqual(doc.checkInsLabel, "3 of 28 days")

        // Symptoms: tie on days (2 each) broken by higher mean severity (flush 2.5 > 1.5).
        let table = doc.symptomTable(maxRows: doc.defaultSymptomMaxRows)
        XCTAssertEqual(table.rows.map(\.name), ["Hot flushes", "Trouble sleeping"])
        XCTAssertEqual(table.rows.first?.thisPeriod, "2 of 3 check-in days")
        XCTAssertFalse(table.showsPreviousColumn)  // no earlier check-ins
        XCTAssertEqual(doc.defaultSymptomMaxRows, 6)

        // Sleep / energy / mood.
        XCTAssertEqual(doc.sleepLine, "2 of 3")   // Trouble sleeping on 2 of 3 check-in days
        XCTAssertEqual(doc.energyLine, "average 6.0 of 10, from 3 entries")  // (6+4+8)/3
        XCTAssertEqual(doc.moodLine, "Okay 2, Low 1")

        // Cycle: one recorded start, standalone spotting reads as bleeding between periods.
        XCTAssertEqual(doc.cycle.periodsRecorded, 1)
        XCTAssertEqual(doc.cycle.cycleLengthRange, "not enough recorded periods to show a range")
        XCTAssertEqual(doc.cycle.intermenstrualBleeding, "Yes")
        XCTAssertEqual(doc.cycle.flow, "Period")   // most-recorded descriptor
        // The cycle repo dates in the machine calendar, so assert presence not the exact
        // instant here; the start-detection itself is covered in CycleStatsTests.
        XCTAssertNotNil(doc.cycle.lastPeriodStart)

        // Treatment tables + dated change.
        XCTAssertEqual(doc.mht.rows.count, 1)
        XCTAssertEqual(doc.mht.rows.first?.col2, "2 pumps")
        XCTAssertEqual(doc.mht.rows.first?.col3, styled(d(-3)))
        XCTAssertEqual(doc.supplements.rows.first?.col1, "Magnesium")
        XCTAssertEqual(doc.supplements.rows.first?.col2, "400mg")
        XCTAssertTrue(doc.otherMeds.isEmpty)
        XCTAssertEqual(doc.treatmentChanges, ["\(styled(d(-3))): changed Oestrogel dose"])
    }

    func testStoppedTreatmentsShowInMHTAndChangesButNotOtherTables() {
        seedFullPicture()   // active Oestrogel (MHT) + Magnesium (supplement)
        // A stopped MHT and a stopped supplement, both dated inside the 4-week window.
        let oldPatch = Medication(name: "Old patch", dosage: "50mcg", timing: "",
                                  isActive: false, kind: .treatment, catalogGroupID: "oestrogen",
                                  stoppedAt: d(-10), ownerID: "test-owner")
        let oldMag = Medication(name: "Old magnesium", dosage: "300mg", timing: "",
                                isActive: false, kind: .supplement, stoppedAt: d(-10), ownerID: "test-owner")
        context.insert(oldPatch); context.insert(oldMag)
        try? context.save()

        var inputs = GPSummaryInputs(); inputs.period = .fourWeeks
        let doc = service.makeDocument(inputs: inputs, now: now)

        // MHT lists the stopped treatment with "Stopped [date]".
        let mhtNames = doc.mht.rows.map(\.col1)
        XCTAssertTrue(mhtNames.contains("Old patch"))
        let stoppedRow = doc.mht.rows.first { $0.col1 == "Old patch" }
        XCTAssertTrue(stoppedRow?.col3.contains("Stopped \(styled(d(-10)))") ?? false, "got \(stoppedRow?.col3 ?? "nil")")

        // A stopped supplement never becomes a supplement row (spec: MHT only).
        XCTAssertFalse(doc.supplements.rows.map(\.col1).contains("Old magnesium"))

        // Both stops are restated in the dated changes list.
        XCTAssertTrue(doc.treatmentChanges.contains("\(styled(d(-10))): stopped Old patch"))
        XCTAssertTrue(doc.treatmentChanges.contains("\(styled(d(-10))): stopped Old magnesium"))
    }

    func testPeriodsNotApplicableReasonFlowsIntoCycleBlock() {
        seedFullPicture()   // creates a profile
        UserRepository(context: context, ownerID: TestStore.ownerID)
            .setPeriodsNotApplicableReason("  After a hysterectomy in 2019  ")
        let doc = service.makeDocument(inputs: GPSummaryInputs(), now: now)
        XCTAssertEqual(doc.cycle.notApplicable, "After a hysterectomy in 2019")   // trimmed, her words
    }

    func testStopOutsideWindowIsNotShown() {
        seedFullPicture()
        let ancient = Medication(name: "Ancient HRT", dosage: "1mg", timing: "",
                                 isActive: false, kind: .treatment, catalogGroupID: "oestrogen",
                                 stoppedAt: d(-400), ownerID: "test-owner")
        context.insert(ancient); try? context.save()
        var inputs = GPSummaryInputs(); inputs.period = .fourWeeks
        let doc = service.makeDocument(inputs: inputs, now: now)
        XCTAssertFalse(doc.mht.rows.map(\.col1).contains("Ancient HRT"))
        XCTAssertFalse(doc.treatmentChanges.contains { $0.contains("Ancient HRT") })
    }

    func testNameAndAgeOffByDefaultAndPrioritiesDropSymptomRow() {
        seedFullPicture()
        var inputs = GPSummaryInputs()
        inputs.period = .fourWeeks
        inputs.priorities = ["Sleep help", "  ", "Mood", "Energy"]  // blank dropped, capped at 3
        inputs.impactAreas = ["Sleep", "Work or concentration"]
        inputs.impactOverall = "Significant"
        inputs.questions = ["Do I need a blood test?"]
        let doc = service.makeDocument(inputs: inputs, now: now)

        XCTAssertNil(doc.name, "name is off by default")
        XCTAssertNil(doc.age, "age is off by default")
        XCTAssertEqual(doc.priorities, ["Sleep help", "Mood", "Energy"])
        XCTAssertEqual(doc.defaultSymptomMaxRows, 5, "three priorities reduce the symptom rows")
        XCTAssertEqual(doc.impactLine, "Affecting: sleep, work or concentration. Overall impact: Significant.")
        XCTAssertEqual(doc.questions, ["Do I need a blood test?"])
    }

    func testEmptyStoreGivesHonestEmptyStates() {
        let doc = service.makeDocument(inputs: GPSummaryInputs(), now: now)
        XCTAssertEqual(doc.checkInsLabel, "0 of 84 days")   // 12-week default
        XCTAssertTrue(doc.symptomTable(maxRows: 6).isEmpty)
        XCTAssertEqual(doc.cycle.periodsRecorded, 0)
        XCTAssertEqual(doc.cycle.intermenstrualBleeding, "Not recorded")
        XCTAssertNil(doc.cycle.lastPeriodStart)
        XCTAssertEqual(doc.sleepLine, "0 of 0")
        XCTAssertNil(doc.energyLine)   // no check-ins, no entries
        XCTAssertNil(doc.moodLine)
        XCTAssertTrue(doc.mht.isEmpty && doc.otherMeds.isEmpty && doc.supplements.isEmpty)
        XCTAssertTrue(doc.treatmentChanges.isEmpty)
        XCTAssertNil(doc.impactLine)
    }
}
