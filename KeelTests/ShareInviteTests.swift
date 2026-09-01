import XCTest
@testable import Keel

/// "Tell your friends" shares a fixed note plus the app link. The content is pure,
/// so it is checked here rather than only through the share sheet.
final class ShareInviteTests: XCTestCase {

    func testMessageIsWarmAndFollowsHouseStyle() {
        XCTAssertFalse(ShareInvite.message.isEmpty)
        XCTAssertTrue(ShareInvite.message.contains("Keel"))
        // House style: no em or en dashes in user-facing copy.
        XCTAssertFalse(ShareInvite.message.contains("\u{2014}"))
        XCTAssertFalse(ShareInvite.message.contains("\u{2013}"))
    }

    func testLinkIsAnAppStoreHTTPSURL() {
        XCTAssertEqual(ShareInvite.appStoreURL.scheme, "https")
        XCTAssertEqual(ShareInvite.appStoreURL.host, "apps.apple.com")
    }

    func testActivityItemsCarryTheNoteThenTheLink() {
        let items = ShareInvite.activityItems
        XCTAssertEqual(items.count, 2)
        XCTAssertEqual(items.first as? String, ShareInvite.message)
        XCTAssertEqual(items.last as? URL, ShareInvite.appStoreURL)
    }
}
