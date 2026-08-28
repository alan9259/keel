import XCTest
@testable import Keel

/// A medicine appears in the home Medicines log when she ticks it to track OR when it
/// auto-logs, so an auto-logged dose is never silently invisible. (A tester set
/// testosterone to auto-log every day and it never showed on the home list.)
final class MedicationHomeLogTests: XCTestCase {

    private func med(tracked: Bool, autoLog: Bool) -> Medication {
        Medication(name: "Testosterone", dosage: "0.5ml", timing: "daily",
                   isTracked: tracked, autoLogDoses: autoLog,
                   schedule: DoseSchedule(kind: .weekly, slots: []), ownerID: "o")
    }

    func testAppearsWhenTrackedOrAutoLogged() {
        XCTAssertTrue(med(tracked: true, autoLog: false).appearsInHomeLog)
        XCTAssertTrue(med(tracked: false, autoLog: true).appearsInHomeLog, "auto-logged must show even when untracked")
        XCTAssertTrue(med(tracked: true, autoLog: true).appearsInHomeLog)
        XCTAssertFalse(med(tracked: false, autoLog: false).appearsInHomeLog)
    }
}
