import XCTest
import SwiftData
@testable import Keel

/// Alcohol (submission pack 3B): an optional check-in count, off by default. Untouched
/// means not recorded; only a saved number counts, including zero. The GP summary line
/// counts days with a saved number, in the same neutral form as the symptom rows, and a
/// blank day is never treated as alcohol-free.
@MainActor
final class AlcoholTests: XCTestCase {

    private var context: ModelContext!
    private var repo: CheckInRepository!
    private let cal = TestStore.utcCalendar
    private let now = Date(timeIntervalSince1970: 1_700_000_000)
    private func d(_ offset: Int, hour: Int = 9) -> Date {
        cal.date(byAdding: .hour, value: hour, to: cal.startOfDay(for: cal.date(byAdding: .day, value: offset, to: now)!))!
    }

    override func setUpWithError() throws {
        context = TestStore.makeContext()
        repo = CheckInRepository(context: context, ownerID: TestStore.ownerID)
    }

    override func tearDownWithError() throws { context = nil; repo = nil }

    private func service() -> GPSummaryService {
        GPSummaryService(context: context, checkIns: repo,
                         medications: MedicationRepository(context: context, ownerID: TestStore.ownerID),
                         cycle: CycleRepository(context: context, ownerID: TestStore.ownerID),
                         users: UserRepository(context: context, ownerID: TestStore.ownerID),
                         calendar: cal)
    }

    private func entry(_ day: Int, alcohol: Int? = nil, hour: Int = 9) {
        let c = repo.create(mood: .okay, energy: 50, notes: nil, symptoms: [], date: d(day, hour: hour))
        if let alcohol { repo.setAlcohol(c, count: alcohol) }
    }

    // MARK: Recording

    func testNewEntryHasNoAlcoholRecorded() {
        let c = repo.create(mood: .okay, energy: 50, notes: nil, symptoms: [], date: now)
        XCTAssertNil(c.alcoholCount)
    }

    func testZeroIsARealAnswerAndClearReturnsToNotRecorded() {
        let c = repo.create(mood: .okay, energy: 50, notes: nil, symptoms: [], date: now)
        repo.setAlcohol(c, count: 0)
        XCTAssertEqual(c.alcoholCount, 0)        // zero is recorded, not "none"
        repo.setAlcohol(c, count: 3)
        XCTAssertEqual(c.alcoholCount, 3)
        repo.setAlcohol(c, count: nil)
        XCTAssertNil(c.alcoholCount)             // cleared = not recorded
        repo.setAlcohol(c, count: -2)
        XCTAssertEqual(c.alcoholCount, 0)        // never negative
    }

    func testEditingTheRestOfAnEntryKeepsItsAlcohol() {
        let c = repo.create(mood: .okay, energy: 50, notes: nil, symptoms: [], date: now)
        repo.setAlcohol(c, count: 2)
        repo.update(c, mood: .good, energy: 70, notes: "edited", symptoms: [])
        XCTAssertEqual(c.alcoholCount, 2)
    }

    /// On by default (4 Oct 2026), but only until she chooses: a stored choice to turn
    /// it off is kept, and the check-in field itself still starts empty.
    func testTheSettingIsOnByDefaultAndHerChoiceIsKept() {
        UserDefaults.standard.removeObject(forKey: "keel.notesAlcohol")
        XCTAssertTrue(SettingsStore().notesAlcohol)
        UserDefaults.standard.set(false, forKey: "keel.notesAlcohol")
        XCTAssertFalse(SettingsStore().notesAlcohol)
        UserDefaults.standard.removeObject(forKey: "keel.notesAlcohol")
    }

    // MARK: GP Visit Summary line

    func testLineFormatMatchesTheSymptomRows() {
        XCTAssertEqual(GPSummaryBuilder.alcoholLine(recordedDays: 6, checkInDays: 24),
                       "recorded on 6 of 24 check-in days")
        XCTAssertEqual(GPSummaryBuilder.alcoholLine(recordedDays: 1, checkInDays: 1),
                       "recorded on 1 of 1 check-in day")
        XCTAssertNil(GPSummaryBuilder.alcoholLine(recordedDays: 0, checkInDays: 24)) // nothing recorded: no line
    }

    func testOnlyDaysWithASavedNumberCountAndBlankIsNeverZero() {
        entry(-1, alcohol: 2)
        entry(-2, alcohol: 0)            // zero counts as recorded
        entry(-3)                        // blank: a check-in day, but not alcohol-free
        entry(-4)
        entry(-5, alcohol: 1, hour: 8)   // two entries on one day count once
        entry(-5, alcohol: nil, hour: 20)

        var inputs = GPSummaryInputs(); inputs.period = .fourWeeks
        let doc = service().makeDocument(inputs: inputs, now: now)
        XCTAssertEqual(doc.checkInDaysThisPeriod, 5)
        XCTAssertEqual(doc.alcoholLine, "recorded on 3 of 5 check-in days")
    }

    func testNoLineWhenSheNeverRecordedAlcohol() {
        entry(-1); entry(-2)
        var inputs = GPSummaryInputs(); inputs.period = .fourWeeks
        XCTAssertNil(service().makeDocument(inputs: inputs, now: now).alcoholLine)
    }

    func testSheCanLeaveTheAlcoholRowOff() {
        entry(-1, alcohol: 1)
        var inputs = GPSummaryInputs(); inputs.period = .fourWeeks
        XCTAssertTrue(service().makeDocument(inputs: inputs, now: now).includeAlcohol)
        inputs.removedSections.insert(.alcohol)
        XCTAssertFalse(service().makeDocument(inputs: inputs, now: now).includeAlcohol)
    }

    // MARK: Backup

    func testBackupRoundTripsAlcoholAndOldBackupsStillLoad() throws {
        entry(-1, alcohol: 0)
        entry(-2, alcohol: 4)
        entry(-3)
        let data = try BackupService.exportData(context: context)

        let fresh = TestStore.makeContext()
        try BackupService.restore(data: data, into: fresh)
        let counts = try fresh.fetch(FetchDescriptor<CheckIn>()).map(\.alcoholCount)
        XCTAssertEqual(Set(counts.map { $0 ?? -1 }), [0, 4, -1])   // zero kept, blank stays blank

        // A backup made before alcohol existed has no key: it decodes as not recorded.
        var json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        var checkIns = try XCTUnwrap(json["checkIns"] as? [[String: Any]])
        for i in checkIns.indices { checkIns[i].removeValue(forKey: "alcoholCount") }
        json["checkIns"] = checkIns
        let old = try JSONSerialization.data(withJSONObject: json)
        let older = TestStore.makeContext()
        try BackupService.restore(data: old, into: older)
        XCTAssertTrue(try older.fetch(FetchDescriptor<CheckIn>()).allSatisfy { $0.alcoholCount == nil })
    }

    // MARK: Sync mapping (dormant today, but must not drop the field)

    func testRemoteFieldsCarryAlcoholOnlyWhenRecorded() {
        let c = repo.create(mood: .okay, energy: 50, notes: nil, symptoms: [], date: now)
        XCTAssertNil(c.remoteFields()["alcoholCount"])
        repo.setAlcohol(c, count: 0)
        XCTAssertEqual(c.remoteFields()["alcoholCount"]?.asInt, 0)
    }
}

/// Copy for the intimacy and bladder group (owner wording, 4 Oct 2026).
final class SensitiveGroupCopyTests: XCTestCase {
    func testIntimacyIntroWording() {
        XCTAssertEqual(SymptomCategory.intimacy.intro,
                       "These can be harder to talk about. Note only what feels relevant to you. They stay on your phone unless you choose to share them.")
    }
}
