import XCTest
@testable import Keel

/// The in-app privacy summary (Your privacy): the owner's wording, checked against the
/// copy rules and the real build.
final class PrivacySummaryTests: XCTestCase {

    private var allText: [String] {
        PrivacySummary.shortVersion + PrivacySummary.columns + [PrivacySummary.title, PrivacySummary.lastUpdated]
            + PrivacySummary.atAGlance.flatMap { [$0.what, $0.kept, $0.access, $0.retention] }
    }

    /// The draft's table shape: "Your information" first, then the owner's three columns.
    func testTableColumnsFollowTheDraft() {
        XCTAssertEqual(PrivacySummary.columns, ["Your information", "Where it is kept", "Access", "Retention and deletion"])
    }

    func testAllSevenRowsWithEveryColumnFilled() {
        XCTAssertEqual(PrivacySummary.atAGlance.count, 7)
        for row in PrivacySummary.atAGlance {
            XCTAssertFalse(row.kept.isEmpty || row.access.isEmpty || row.retention.isEmpty, row.what)
        }
    }

    func testNoDashesAndAustralianSpelling() {
        for text in allText {
            XCTAssertFalse(text.contains("—") || text.contains("–"), text)
            XCTAssertFalse(text.contains("Authorized"), text)
        }
    }

    /// The deletion it names must exist under that name.
    func testNamesTheRealDeleteControl() {
        XCTAssertTrue(allText.contains { $0.contains("Delete all my data") })
    }

    func testFullPolicyLinkIsTheCompanySite() {
        XCTAssertEqual(KeelLinks.privacyPolicy.absoluteString, "https://therecalibrationyears.com/keel-privacy")
    }
}
