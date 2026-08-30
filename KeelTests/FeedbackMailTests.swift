import XCTest
@testable import Keel

/// The feedback / feature-request draft is built as a pure `mailto:` URL, so the
/// recipient, subject and body are covered here rather than only through the UI.
final class FeedbackMailTests: XCTestCase {

    private func makeURL(_ kind: FeedbackMail.Kind) -> URL {
        guard let url = FeedbackMail.url(kind: kind, version: "1.2 (47)", os: "iOS 26.5", device: "iPhone17,1") else {
            fatalError("expected a mailto URL")
        }
        return url
    }

    func testSchemeAndRecipient() {
        let url = makeURL(.feedback)
        XCTAssertEqual(url.scheme, "mailto")
        // The address sits before the query, unencoded, so any mail app resolves it.
        XCTAssertTrue(url.absoluteString.hasPrefix("mailto:keel@therecalibrationyears.com?"), url.absoluteString)
    }

    func testSubjectsDifferPerKind() {
        XCTAssertTrue(makeURL(.feedback).absoluteString.contains("subject=Keel%20feedback"))
        XCTAssertTrue(makeURL(.featureRequest).absoluteString.contains("subject=Keel%20feature%20request"))
    }

    func testBodyCarriesOpenerAndRealContext() {
        // Decode the whole draft back and assert on the human-readable text.
        let decoded = makeURL(.feedback).absoluteString.removingPercentEncoding ?? ""
        XCTAssertTrue(decoded.contains("Here's what's on my mind:"), decoded)
        XCTAssertTrue(decoded.contains("Sent from Keel 1.2 (47) · iOS 26.5 · iPhone17,1"), decoded)
        XCTAssertTrue(decoded.contains("You can delete this line."), decoded)
    }

    func testFeatureRequestOpenerDiffers() {
        let decoded = makeURL(.featureRequest).absoluteString.removingPercentEncoding ?? ""
        XCTAssertTrue(decoded.contains("I'd love Keel to be able to do"), decoded)
    }

    /// Everything that would otherwise break a mailto query must be percent-encoded:
    /// no raw spaces, newlines, apostrophes or `&`/`=` inside the values.
    func testDraftIsFullyPercentEncoded() {
        let full = makeURL(.feedback).absoluteString
        // Only the single delimiters we placed ourselves are literal.
        let afterQuery = String(full.split(separator: "?", maxSplits: 1).last ?? "")
        XCTAssertFalse(afterQuery.contains(" "), "raw space leaked into the query")
        XCTAssertFalse(afterQuery.contains("\n"), "raw newline leaked into the query")
        XCTAssertFalse(afterQuery.contains("'"), "raw apostrophe leaked into the query")
        // Spaces are encoded as %20, apostrophes as %27, newlines as %0A.
        XCTAssertTrue(afterQuery.contains("%20"))
        XCTAssertTrue(afterQuery.contains("%0A"))
    }

    /// The copy-fallback address and the drafted recipient are the same inbox.
    func testFallbackAddressMatchesRecipient() {
        XCTAssertEqual(FeedbackMail.address, "keel@therecalibrationyears.com")
        XCTAssertTrue(makeURL(.feedback).absoluteString.contains(FeedbackMail.address))
    }
}
