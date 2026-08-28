import XCTest
@testable import Keel

/// Dose units, including "pumps" for gels and sprays (a tester logs "2 pumps of gel").
final class DoseUnitTests: XCTestCase {

    func testPumpsIsSelectable() {
        XCTAssertTrue(DoseUnit.allCases.contains(.pumps))
        XCTAssertEqual(DoseUnit.pumps.label, "pumps")
    }

    func testPumpsFormatSingularisesAndSpaces() {
        XCTAssertEqual(DoseUnit.pumps.format(1), "1 pump")
        XCTAssertEqual(DoseUnit.pumps.format(2), "2 pumps")
        XCTAssertEqual(DoseUnit.pumps.format(0.5), "0.5 pumps")
    }

    func testOtherUnitsAreUnchanged() {
        XCTAssertEqual(DoseUnit.mg.format(400), "400mg")
        XCTAssertEqual(DoseUnit.iu.format(2000), "2000 IU")
        XCTAssertEqual(DoseUnit.ml.format(0.5), "0.5ml")
    }
}
