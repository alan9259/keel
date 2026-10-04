import XCTest
@testable import Keel

/// Reminder wording (owner feedback, 4 Oct 2026). Reminders show on the lock screen,
/// and nothing in them may make a health claim or say a dose was taken that she
/// didn't confirm or choose.
final class ReminderWordingTests: XCTestCase {

    /// Regression: the title named her medicine ("Time for Prometrium") on the lock
    /// screen by default. It's generic unless she turns names on in Settings.
    func testMedicineNameHiddenByDefault() {
        let text = NotificationService.medicationReminderText(name: "Prometrium", showName: false, autoLog: false)
        XCTAssertEqual(text.title, "Time for your medication")
        XCTAssertFalse(text.title.contains("Prometrium"))
        XCTAssertFalse(text.body.contains("Prometrium"))
    }

    func testMedicineNameShownWhenSheChoosesIt() {
        XCTAssertEqual(NotificationService.medicationReminderText(name: "Prometrium", showName: true, autoLog: false).title,
                       "Time for Prometrium")
    }

    func testBodiesAreHonestAboutLogging() {
        XCTAssertEqual(NotificationService.medicationReminderText(name: "x", showName: false, autoLog: false).body,
                       "A gentle reminder. Tap to mark it taken.")
        XCTAssertEqual(NotificationService.medicationReminderText(name: "x", showName: false, autoLog: true).body,
                       "A gentle reminder. Keel will record it as taken unless you change it.")
    }

    @MainActor
    func testTheSettingIsOffByDefault() {
        UserDefaults.standard.removeObject(forKey: "keel.medNamesInReminders")
        XCTAssertFalse(SettingsStore().medicineNamesInReminders)
    }

    func testEverydayRemindersMakeNoHealthClaims() {
        typealias C = NotificationService.Copy
        XCTAssertEqual(C.hydrationBody, "A small sip counts.")
        XCTAssertEqual(C.checkInBody, "A quick check-in adds to your record.")
        XCTAssertEqual(C.windDownBody, "A calmer evening, whatever that looks like for you.")
        let all = [C.checkInTitle, C.checkInBody, C.hydrationTitle, C.hydrationBody,
                   C.movementTitle, C.movementBody, C.windDownTitle, C.windDownBody]
        for text in all {
            for claim in ["can help", "headache", "better night", "sharpens", "—", "–"] {
                XCTAssertFalse(text.lowercased().contains(claim), "\(text) contains \(claim)")
            }
        }
    }
}
