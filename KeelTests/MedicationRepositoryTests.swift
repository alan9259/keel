import XCTest
import SwiftData
@testable import Keel

/// Medication logging against a real (in-memory) SwiftData store: whole-day vs
/// per-slot ticks, un-ticking, and auto-log idempotency. These are the writes
/// behind the home Medicines log and the reminder skip logic.
@MainActor
final class MedicationRepositoryTests: XCTestCase {

    private var container: ModelContainer!
    private var context: ModelContext!
    private var repo: MedicationRepository!

    override func setUpWithError() throws {
        container = KeelSchema.makeContainer(inMemory: true)
        context = container.mainContext
        repo = MedicationRepository(context: context, ownerID: { "test-owner" })
    }

    override func tearDownWithError() throws {
        container = nil; context = nil; repo = nil
    }

    private func makeMed(hour: Int? = 8, autoLog: Bool = false) -> Medication {
        let slot = DoseSlot(weekdays: Set(1...7), hour: hour)
        let sched = DoseSchedule(kind: .weekly, slots: [slot])
        let med = Medication(name: "Test", dosage: "1", timing: "t", autoLogDoses: autoLog,
                             schedule: sched, ownerID: "test-owner", syncStatus: .synced)
        context.insert(med)
        try? context.save()
        return med
    }

    // MARK: stop / reactivate

    func testStopRecordsDateAndKeepsRecord_reactivateClearsIt() {
        let med = makeMed()
        XCTAssertTrue(med.isActive)

        // Mark stopped with a date she entered.
        var draft = TreatmentDraft(med)
        draft.isActive = false
        draft.stoppedAt = Date(timeIntervalSince1970: 1_700_000_000)
        repo.update(med, with: draft)

        XCTAssertFalse(med.isActive)
        XCTAssertEqual(med.stoppedAt, Date(timeIntervalSince1970: 1_700_000_000))
        XCTAssertNil(med.deletedAt, "stopping keeps the record, it is not a delete")
        XCTAssertFalse(repo.active().contains { $0.id == med.id })
        XCTAssertTrue(repo.stoppedTreatments().contains { $0.id == med.id })

        // Reactivating clears the stop date and returns it to the active list.
        var back = TreatmentDraft(med)
        back.isActive = true
        repo.update(med, with: back)
        XCTAssertTrue(med.isActive)
        XCTAssertNil(med.stoppedAt)
        XCTAssertTrue(repo.active().contains { $0.id == med.id })
        XCTAssertFalse(repo.stoppedTreatments().contains { $0.id == med.id })
    }

    func testStoppingWithoutADateLeavesItBlank() {
        // Product-alignment item 1: never substitute a system timestamp for a date she
        // didn't enter. Stopping with no date must leave stoppedAt nil.
        let med = makeMed()
        var draft = TreatmentDraft(med)
        draft.isActive = false
        draft.stoppedAt = nil
        repo.update(med, with: draft)
        XCTAssertFalse(med.isActive)
        XCTAssertNil(med.stoppedAt, "no date entered means the stop date stays blank")
    }

    // MARK: whole-day (nil slot) tick

    func testWholeDayTickAndRead() {
        let med = makeMed()
        XCTAssertFalse(repo.isTaken(med, on: .now, slot: nil))
        repo.setTaken(med, on: .now, slot: nil, taken: true)
        XCTAssertTrue(repo.isTaken(med, on: .now, slot: nil))
    }

    func testUntickRemovesLog() {
        let med = makeMed()
        repo.setTaken(med, on: .now, slot: nil, taken: true)
        repo.setTaken(med, on: .now, slot: nil, taken: false)
        XCTAssertFalse(repo.isTaken(med, on: .now, slot: nil))
    }

    func testTickIsPerDay() {
        let med = makeMed()
        let today = Date.now
        let yesterday = today.addingTimeInterval(-86_400)
        repo.setTaken(med, on: today, slot: nil, taken: true)
        XCTAssertTrue(repo.isTaken(med, on: today, slot: nil))
        XCTAssertFalse(repo.isTaken(med, on: yesterday, slot: nil))
    }

    func testClearTakenWipesEverySlotForTheDay() {
        let med = makeMed()
        repo.setTaken(med, on: .now, slot: "slot-a", taken: true)
        repo.setTaken(med, on: .now, slot: "slot-b", taken: true)
        repo.clearTaken(med, on: .now)
        XCTAssertFalse(repo.isTaken(med, on: .now, slot: "slot-a"))
        XCTAssertFalse(repo.isTaken(med, on: .now, slot: "slot-b"))
    }

    // MARK: auto-log

    func testAutoLogLogsADuePastDoseOnce() {
        // Dose at 01:00; "now" at 10:00 → its time has passed today.
        _ = makeMed(hour: 1, autoLog: true)
        let cal = Calendar.current
        let now = cal.date(bySettingHour: 10, minute: 0, second: 0, of: .now)!

        let first = repo.autoLogTodaysDueDoses(now: now)
        XCTAssertEqual(first.count, 1, "one due dose should auto-log")

        // Idempotent: a second run (e.g. app reopened) logs nothing new.
        let second = repo.autoLogTodaysDueDoses(now: now)
        XCTAssertEqual(second.count, 0, "already-logged dose must not double-log")
    }

    func testAutoLogSkipsAFutureDoseToday() {
        // Dose at 23:00; "now" at 10:00 → not due yet today, so nothing is logged.
        // (Regression: a `[String?]` compactMap kept the ternary's nil as an
        // element, wrongly whole-day-logging a still-future dose.)
        _ = makeMed(hour: 23, autoLog: true)
        let cal = Calendar.current
        let now = cal.date(bySettingHour: 10, minute: 0, second: 0, of: .now)!
        XCTAssertEqual(repo.autoLogTodaysDueDoses(now: now).count, 0)
    }

    func testAutoLogUntimedDoseLogsBySlotAsSoonAsDue() {
        // A dose with no set time is due as soon as the day is; auto-log logs it by
        // its slot id (not a whole-day nil).
        let slot = DoseSlot(weekdays: Set(1...7), hour: nil)
        let med = Medication(name: "Untimed", dosage: "1", timing: "t", autoLogDoses: true,
                             schedule: DoseSchedule(kind: .weekly, slots: [slot]),
                             ownerID: "test-owner", syncStatus: .synced)
        context.insert(med); try? context.save()
        let logged = repo.autoLogTodaysDueDoses(now: .now)
        XCTAssertEqual(logged.count, 1)
        XCTAssertEqual(logged.first?.slot, slot.id.uuidString)
    }

    func testAutoLogSkipsDayTheMedIsNotDue() {
        // Weekly med due only on the weekday OPPOSITE today → not due → log nothing.
        let cal = Calendar.current
        let today = cal.component(.weekday, from: .now)          // 1...7
        let notToday = today == 1 ? 2 : 1                        // a different weekday
        let slot = DoseSlot(weekdays: [notToday], hour: 1)       // 1am, but wrong day
        let med = Medication(name: "OtherDay", dosage: "1", timing: "t", autoLogDoses: true,
                             schedule: DoseSchedule(kind: .weekly, slots: [slot]),
                             ownerID: "test-owner", syncStatus: .synced)
        context.insert(med); try? context.save()
        let now = cal.date(bySettingHour: 10, minute: 0, second: 0, of: .now)!
        XCTAssertEqual(repo.autoLogTodaysDueDoses(now: now).count, 0)
    }

    func testAutoLogIgnoresMedsWithoutAutoLog() {
        _ = makeMed(hour: 1, autoLog: false)
        let cal = Calendar.current
        let now = cal.date(bySettingHour: 10, minute: 0, second: 0, of: .now)!
        XCTAssertEqual(repo.autoLogTodaysDueDoses(now: now).count, 0)
    }
}
