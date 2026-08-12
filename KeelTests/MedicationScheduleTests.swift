import XCTest
@testable import Keel

/// A Medication stores its schedule as flat columns (kind, weekday mask, cycle
/// numbers, dose-times JSON) and hands it back as a `DoseSchedule`. This round-trip
/// is easy to break (it bit us once), so pin that set-then-get preserves the
/// schedule's shape.
final class MedicationScheduleTests: XCTestCase {

    func testWeeklyScheduleRoundTrips() {
        let slot = DoseSlot(weekdays: [2, 4, 6], hour: 8, minute: 30)
        let med = Medication(name: "Test", dosage: "1", timing: "t",
                             schedule: DoseSchedule(kind: .weekly, slots: [slot]),
                             ownerID: "o")
        let back = med.schedule
        XCTAssertEqual(back.kind, .weekly)
        XCTAssertEqual(back.sortedSlots.count, 1)
        let s = back.sortedSlots.first
        XCTAssertEqual(s?.hour, 8)
        XCTAssertEqual(s?.minute, 30)
        XCTAssertEqual(s?.weekdays, [2, 4, 6])
        XCTAssertTrue(med.hasSchedule)
    }

    func testCycleScheduleRoundTrips() {
        let med = Medication(name: "Test", dosage: "1", timing: "t",
                             schedule: DoseSchedule(kind: .cycle, slots: [DoseSlot(hour: 9)],
                                                    cycleLength: 28, pauseDays: 7),
                             ownerID: "o")
        let back = med.schedule
        XCTAssertEqual(back.kind, .cycle)
        XCTAssertEqual(back.cycleLength, 28)
        XCTAssertEqual(back.pauseDays, 7)
        XCTAssertEqual(back.sortedSlots.first?.hour, 9)
    }

    func testMultipleDoseTimesRoundTrip() {
        let morning = DoseSlot(weekdays: Set(1...7), hour: 8)
        let evening = DoseSlot(weekdays: Set(1...7), hour: 20)
        let med = Medication(name: "Test", dosage: "1", timing: "t",
                             schedule: DoseSchedule(kind: .weekly, slots: [morning, evening]),
                             ownerID: "o")
        let hours = med.schedule.sortedSlots.compactMap(\.hour)
        XCTAssertEqual(hours, [8, 20]) // sorted by clock time
    }

    func testUntimedDosePreservedAsHasScheduleWithNoTime() {
        let med = Medication(name: "Test", dosage: "1", timing: "t",
                             schedule: DoseSchedule(kind: .weekly, slots: [DoseSlot(weekdays: Set(1...7))]),
                             ownerID: "o")
        XCTAssertTrue(med.hasSchedule)
        XCTAssertFalse(med.schedule.sortedSlots.contains { $0.hasTime })
    }

    func testNoScheduleReadsAsNotHavingOne() {
        let med = Medication(name: "Test", dosage: "1", timing: "Every day", ownerID: "o")
        XCTAssertFalse(med.hasSchedule) // free-text entry, no structured schedule set
    }

    func testWeekdaysMaskRoundTripsThroughStaticHelpers() {
        let days: Set<Int> = [1, 3, 5, 7]
        XCTAssertEqual(Medication.weekdays(from: Medication.mask(from: days)), days)
        XCTAssertEqual(Medication.mask(from: []), 0)
    }
}
