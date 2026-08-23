import XCTest
import SwiftData
@testable import Keel

/// The Profile screen's logic: age ↔ birth-year conversion, the basic-info edit
/// round-trip, and the ownership re-stamp that carries her data over when a local
/// identity is upgraded to a Sign in with Apple account.
@MainActor
final class ProfileTests: XCTestCase {

    private let cal = TestStore.utcCalendar
    private var now: Date { cal.date(from: DateComponents(year: 2026, month: 8, day: 21))! }

    // MARK: Age ↔ birth year

    func testAgeAndBirthYearAreInverse() {
        XCTAssertEqual(UserProfile.birthYear(fromAge: 49, now: now, calendar: cal), 1977)
        XCTAssertEqual(UserProfile.age(fromBirthYear: 1977, now: now, calendar: cal), 49)
    }

    func testDerivedAgeUsesStoredBirthYear() {
        let p = UserProfile(firstName: "Mischa", birthYear: 1977, ownerID: "o")
        // `age` uses the current year; assert it matches the same derivation.
        XCTAssertEqual(p.age, UserProfile.age(fromBirthYear: 1977))
        let none = UserProfile(firstName: "Mischa", ownerID: "o")
        XCTAssertNil(none.age)
    }

    // MARK: updateBasicInfo

    func testUpdateBasicInfoRoundTripsAndClears() {
        let ctx = TestStore.makeContext()
        let repo = UserRepository(context: ctx, ownerID: TestStore.ownerID)
        repo.upsertProfile(firstName: "there", email: nil, appleUserID: nil) // a skipped-sign-up profile

        repo.updateBasicInfo(firstName: "Mischa", lastName: "Reed", birthYear: 1977,
                             mobile: "0400 000 000", email: "m@example.com")
        let saved = repo.currentProfile()
        XCTAssertEqual(saved?.firstName, "Mischa")
        XCTAssertEqual(saved?.lastName, "Reed")
        XCTAssertEqual(saved?.birthYear, 1977)
        XCTAssertEqual(saved?.mobile, "0400 000 000")
        XCTAssertEqual(saved?.email, "m@example.com")

        // nil clears the optional fields (the form is the source of truth).
        repo.updateBasicInfo(firstName: "Mischa", lastName: nil, birthYear: nil, mobile: nil, email: nil)
        let cleared = repo.currentProfile()
        XCTAssertNil(cleared?.lastName)
        XCTAssertNil(cleared?.birthYear)
        XCTAssertNil(cleared?.mobile)
        XCTAssertNil(cleared?.email)

        // A blank first name is ignored, so she's never left unnamed.
        repo.updateBasicInfo(firstName: "   ", lastName: nil, birthYear: nil, mobile: nil, email: nil)
        XCTAssertEqual(repo.currentProfile()?.firstName, "Mischa")
    }

    func testUpdateBasicInfoCreatesAProfileIfNoneExists() {
        let ctx = TestStore.makeContext()
        let repo = UserRepository(context: ctx, ownerID: TestStore.ownerID)
        XCTAssertNil(repo.currentProfile())
        repo.updateBasicInfo(firstName: "Mischa", lastName: nil, birthYear: nil, mobile: nil, email: nil)
        XCTAssertEqual(repo.currentProfile()?.firstName, "Mischa")
    }

    // MARK: Ownership re-stamp (local → Apple upgrade)

    func testReassignOwnershipMovesOwnedRowsAndLeavesGlobalsAlone() {
        let ctx = TestStore.makeContext()
        let old = "local-abc", new = "apple-xyz"

        ctx.insert(CheckIn(date: now, mood: .okay, energy: 50, ownerID: old))
        ctx.insert(ActivityLog(date: now, activityID: "sleep", amount: 7, ownerID: old))
        ctx.insert(HealthSample(typeID: "restingHeartRate", day: now, value: 60, unit: "bpm", ownerID: old))
        ctx.insert(UserProfile(firstName: "Mischa", ownerID: old))
        // A global built-in symptom (empty ownerID) must NOT be swept up.
        ctx.insert(Symptom(name: "Hot flushes", category: .body, isCustom: false, ownerID: ""))
        try? ctx.save()

        let moved = OwnershipMigration.reassign(in: ctx, from: old, to: new)
        XCTAssertEqual(moved, 4) // check-in, activity, sample, profile — not the global symptom

        let checkIns = (try? ctx.fetch(FetchDescriptor<CheckIn>())) ?? []
        XCTAssertTrue(checkIns.allSatisfy { $0.ownerID == new })
        let profiles = (try? ctx.fetch(FetchDescriptor<UserProfile>())) ?? []
        XCTAssertEqual(profiles.first?.ownerID, new)
        let symptoms = (try? ctx.fetch(FetchDescriptor<Symptom>())) ?? []
        XCTAssertEqual(symptoms.first?.ownerID, "") // untouched

        // Moved rows are queued for the next sync pass under the new owner.
        XCTAssertTrue(checkIns.allSatisfy { $0.syncStatus == .pendingUpload })
    }

    func testReassignOwnershipIsANoOpForSameOrEmptyOwner() {
        let ctx = TestStore.makeContext()
        ctx.insert(CheckIn(date: now, mood: .okay, energy: 50, ownerID: "local-abc"))
        try? ctx.save()
        XCTAssertEqual(OwnershipMigration.reassign(in: ctx, from: "apple-x", to: "apple-x"), 0)
        XCTAssertEqual(OwnershipMigration.reassign(in: ctx, from: "", to: "apple-x"), 0)
    }
}
