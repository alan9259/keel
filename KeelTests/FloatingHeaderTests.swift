import XCTest
@testable import Keel

/// The floating header (back, page title, Home) appears only once the screen's own
/// header has scrolled fully out of view, and hides again when it's back.
final class FloatingHeaderTests: XCTestCase {
    func testHeaderAtRestIsNotScrolledAway() {
        XCTAssertFalse(ScreenHeader.isScrolledAway(headerMaxY: 74))   // header at the top
        XCTAssertFalse(ScreenHeader.isScrolledAway(headerMaxY: 1))    // a sliver still showing
    }

    func testHeaderFullyAboveTheTopIsScrolledAway() {
        XCTAssertFalse(ScreenHeader.isScrolledAway(headerMaxY: 0))    // boundary: just touching
        XCTAssertTrue(ScreenHeader.isScrolledAway(headerMaxY: -0.5))
        XCTAssertTrue(ScreenHeader.isScrolledAway(headerMaxY: -594))  // far down a long page
    }
}
