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

/// About Keel: the owner's wording (4 Oct 2026), checked against the copy rules.
final class AboutCopyTests: XCTestCase {
    private var allText: [String] {
        AboutView.opening + AboutView.sections.flatMap { [$0.0] + $0.1 }
            + [AboutView.closing, AboutView.versionLine, AboutView.emojiCredit]
    }

    func testNoDashes() {
        for text in allText { XCTAssertFalse(text.contains("—") || text.contains("–"), text) }
    }

    func testOpensAndClosesWithTheOwnersLines() {
        XCTAssertEqual(AboutView.opening.first, "You're not imagining it.")
        XCTAssertEqual(AboutView.sections.map(\.0), ["What we do", "Why we do it", "Built from lived experience", "What Keel isn't"])
        XCTAssertTrue(AboutView.closing.hasSuffix("whatever that means to you."))
    }

    /// No claim that Keel notices patterns or makes sense of things for her (V1 keeps
    /// the record; it doesn't interpret).
    func testNoInterpretationClaims() {
        for text in allText {
            XCTAssertFalse(text.contains("notices patterns"), text)
            XCTAssertFalse(text.contains("make sense of"), text)
        }
    }

    func testVersionLineUsesTheRealVersion() {
        XCTAssertEqual(AboutView.versionLine, "Keel · Version \(DeviceContext.shortVersion) · © 2026 TRY Keel Pty Ltd")
    }
}

/// Connect with Us links: the accounts the owner gave (3 Sep 2026), as web URLs that
/// open the app when installed.
final class SocialLinkTests: XCTestCase {
    func testSocialLinks() {
        XCTAssertEqual(KeelLinks.instagram.absoluteString, "https://www.instagram.com/keelperiapp/")
        XCTAssertEqual(KeelLinks.facebook.absoluteString, "https://www.facebook.com/KeelPeriApp")
    }
}
