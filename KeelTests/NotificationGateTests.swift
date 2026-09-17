import XCTest
import UserNotifications
@testable import Keel

/// The reminder toggle's authorization gate (`NotificationService.gate`). This is
/// the decision behind the tester report: after notifications are turned off in iOS
/// Settings the status is `.denied`, and iOS will not re-prompt, so the toggle must
/// route to Settings rather than silently do nothing.
final class NotificationGateTests: XCTestCase {

    func testNotDeterminedRequestsThePrompt() {
        XCTAssertEqual(NotificationService.gate(for: .notDetermined), .request)
    }

    func testDeniedIsBlockedSoWeGuideToSettings() {
        // The reported case: revoked in Settings -> no re-prompt -> guide to Settings.
        XCTAssertEqual(NotificationService.gate(for: .denied), .blocked)
    }

    func testAuthorizedProceeds() {
        XCTAssertEqual(NotificationService.gate(for: .authorized), .proceed)
    }

    func testProvisionalProceeds() {
        XCTAssertEqual(NotificationService.gate(for: .provisional), .proceed)
    }

    func testEphemeralProceeds() {
        XCTAssertEqual(NotificationService.gate(for: .ephemeral), .proceed)
    }
}
