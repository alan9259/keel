import XCTest

/// App Review 5.1.1(iv): a screen shown before a system permission request may not
/// offer a way out ("Not now", "Skip"); it has one way forward ("Continue") and she
/// chooses on Apple's own sheet, which has "Don't Allow". Guards the three such screens
/// (onboarding's Apple Health step, the reminders explanation and the launch Health
/// offer) against an exit button creeping back in.
final class PrePermissionScreenTests: XCTestCase {

    private func source(_ relativePath: String) throws -> String {
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent()   // repo root
        return try String(contentsOf: root.appendingPathComponent(relativePath), encoding: .utf8)
    }

    func testOnboardingAppleHealthStepHasOnlyContinue() throws {
        let code = try source("Keel/Features/Onboarding/AppleHealthView.swift")
        XCTAssertFalse(code.contains("Skip for now"))
        XCTAssertFalse(code.contains("\"Not now\""))
        XCTAssertFalse(code.contains("\"Connect Apple Health\""))
        XCTAssertTrue(code.contains("KeelPrimaryButton(\"Continue\""))
    }

    /// The reminders explanation and the launch Health offer both live in MainView.
    func testLaunchAlertsBeforeSystemPromptsHaveOnlyContinue() throws {
        let code = try source("Keel/App/MainView.swift")
        XCTAssertFalse(code.contains("\"Not now\""))
        XCTAssertFalse(code.contains("\"Skip"))
    }
}
