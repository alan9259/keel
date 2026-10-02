import XCTest
import SwiftData
@testable import Keel

/// The hysterectomy question (submission pack 3C): recorded in Settings > My background,
/// on the GP summary only if she turns it on, labelled to match her answer. Keel records
/// the answer and changes nothing because of it.
@MainActor
final class HysterectomyTests: XCTestCase {

    private var context: ModelContext!
    private var users: UserRepository!
    private let cal = TestStore.utcCalendar
    private let now = Date(timeIntervalSince1970: 1_700_000_000)   // Nov 2023

    override func setUpWithError() throws {
        context = TestStore.makeContext()
        users = UserRepository(context: context, ownerID: TestStore.ownerID)
        users.upsertProfile(firstName: "Mischa", email: nil, appleUserID: nil)
    }

    override func tearDownWithError() throws { context = nil; users = nil }

    private func service() -> GPSummaryService {
        GPSummaryService(context: context,
                         checkIns: CheckInRepository(context: context, ownerID: TestStore.ownerID),
                         medications: MedicationRepository(context: context, ownerID: TestStore.ownerID),
                         cycle: CycleRepository(context: context, ownerID: TestStore.ownerID),
                         users: users, calendar: cal)
    }

    // MARK: The question

    func testTheSixOptionsMatchTheSpec() {
        XCTAssertEqual(Hysterectomy.question, "Have you had a hysterectomy?")
        XCTAssertEqual(Hysterectomy.allCases.map(\.label), [
            "No",
            "Yes, both ovaries were kept",
            "Yes, one ovary was removed",
            "Yes, both ovaries were removed",
            "Yes, I'm not sure about my ovaries",
            "I'd rather not say",
        ])
    }

    func testSummaryLineMatchesHerAnswer() {
        XCTAssertEqual(Hysterectomy.summaryLine(.yesOneOvaryRemoved, year: nil),
                       "yes, one ovary removed (as recorded by her)")
        XCTAssertEqual(Hysterectomy.summaryLine(.yesBothOvariesRemoved, year: 2019),
                       "yes, both ovaries removed, 2019 (as recorded by her)")
        XCTAssertEqual(Hysterectomy.summaryLine(.yesNotSureAboutOvaries, year: nil),
                       "yes, not sure about ovaries (as recorded by her)")
        XCTAssertEqual(Hysterectomy.summaryLine(.no, year: 2019), "no (as recorded by her)") // no year for "No"
        XCTAssertNil(Hysterectomy.summaryLine(.ratherNotSay, year: nil))   // never on the summary
        XCTAssertNil(Hysterectomy.summaryLine(nil, year: nil))              // unanswered
    }

    func testYearMustBePlausible() {
        XCTAssertEqual(Hysterectomy.validYear("2019", now: now, calendar: cal), 2019)
        XCTAssertEqual(Hysterectomy.validYear(" 1998 ", now: now, calendar: cal), 1998)
        XCTAssertNil(Hysterectomy.validYear("", now: now, calendar: cal))
        XCTAssertNil(Hysterectomy.validYear("20", now: now, calendar: cal))     // still typing
        XCTAssertNil(Hysterectomy.validYear("2031", now: now, calendar: cal))   // future
        XCTAssertNil(Hysterectomy.validYear("1850", now: now, calendar: cal))
    }

    // MARK: Recording

    func testRecordsTheAnswerAndKeepsAYearOnlyForYes() {
        users.setHysterectomy(.yesOvariesKept, year: 2015)
        XCTAssertEqual(users.currentProfile()?.hysterectomy, .yesOvariesKept)
        XCTAssertEqual(users.currentProfile()?.hysterectomyYear, 2015)

        users.setHysterectomy(.no, year: 2015)
        XCTAssertEqual(users.currentProfile()?.hysterectomy, .no)
        XCTAssertNil(users.currentProfile()?.hysterectomyYear)

        users.setHysterectomy(nil, year: nil)
        XCTAssertNil(users.currentProfile()?.hysterectomy)
    }

    // MARK: GP Visit Summary

    func testOffByDefaultOnTheSummary() {
        users.setHysterectomy(.yesOneOvaryRemoved, year: nil)
        let doc = service().makeDocument(inputs: GPSummaryInputs(), now: now)
        XCTAssertNil(doc.hysterectomyLine)
        XCTAssertFalse(GPSummaryInputs().includeHysterectomy)
    }

    func testIncludedOnlyWhenSheTurnsItOn() {
        users.setHysterectomy(.yesOneOvaryRemoved, year: nil)
        var inputs = GPSummaryInputs(); inputs.includeHysterectomy = true
        XCTAssertEqual(service().makeDocument(inputs: inputs, now: now).hysterectomyLine,
                       "yes, one ovary removed (as recorded by her)")
    }

    func testRatherNotSayIsNeverOfferedOrPrinted() {
        users.setHysterectomy(.ratherNotSay, year: nil)
        XCTAssertFalse(service().hasHysterectomyAnswer())
        var inputs = GPSummaryInputs(); inputs.includeHysterectomy = true
        XCTAssertNil(service().makeDocument(inputs: inputs, now: now).hysterectomyLine)

        users.setHysterectomy(.no, year: nil)
        XCTAssertTrue(service().hasHysterectomyAnswer())
    }

    // MARK: Backup (also covers the profile details backups used to drop)

    func testBackupKeepsHerBackgroundAndDetails() throws {
        users.setHysterectomy(.yesBothOvariesRemoved, year: 2012)
        users.setPeriodsNotApplicableReason("After a hysterectomy")
        users.updateBasicInfo(firstName: "Mischa", lastName: "R", birthYear: 1977, mobile: "0400 000 000", email: nil)
        let data = try BackupService.exportData(context: context)

        let fresh = TestStore.makeContext()
        try BackupService.restore(data: data, into: fresh)
        let p = try XCTUnwrap(try fresh.fetch(FetchDescriptor<UserProfile>()).first)
        XCTAssertEqual(p.hysterectomy, .yesBothOvariesRemoved)
        XCTAssertEqual(p.hysterectomyYear, 2012)
        XCTAssertEqual(p.periodsNotApplicableReason, "After a hysterectomy")
        XCTAssertEqual(p.lastName, "R")
        XCTAssertEqual(p.birthYear, 1977)
        XCTAssertEqual(p.mobile, "0400 000 000")
    }

    func testOlderBackupsWithoutTheseFieldsStillLoad() throws {
        let data = try BackupService.exportData(context: context)
        var json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        var profiles = try XCTUnwrap(json["profiles"] as? [[String: Any]])
        for key in ["hysterectomyRaw", "hysterectomyYear", "periodsNotApplicableReason", "lastName", "birthYear", "mobile"] {
            for i in profiles.indices { profiles[i].removeValue(forKey: key) }
        }
        json["profiles"] = profiles
        let fresh = TestStore.makeContext()
        try BackupService.restore(data: try JSONSerialization.data(withJSONObject: json), into: fresh)
        let p = try XCTUnwrap(try fresh.fetch(FetchDescriptor<UserProfile>()).first)
        XCTAssertEqual(p.firstName, "Mischa")
        XCTAssertNil(p.hysterectomy)
    }
}
