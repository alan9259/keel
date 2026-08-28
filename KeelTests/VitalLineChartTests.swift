import XCTest
import SwiftUI
@testable import Keel

/// The smooth-curve builder behind the vitals chart: safe on empty/small inputs and
/// producing a real path for a normal series.
@MainActor
final class VitalLineChartTests: XCTestCase {

    func testEmptyInputProducesEmptyPath() {
        XCTAssertTrue(VitalLineChart.smoothPath(through: []).isEmpty)
    }

    func testTwoPointsFallBackToALine() {
        let path = VitalLineChart.smoothPath(through: [CGPoint(x: 0, y: 0), CGPoint(x: 10, y: 5)])
        XCTAssertFalse(path.isEmpty)
    }

    func testManyPointsProduceACurveThroughTheEnds() {
        let pts = (0..<8).map { CGPoint(x: Double($0) * 10, y: Double($0 % 2) * 20) }
        let path = VitalLineChart.smoothPath(through: pts)
        XCTAssertFalse(path.isEmpty)
        // A Catmull-Rom curve interpolates its points, so the ends sit on the path.
        let bounds = path.boundingRect
        XCTAssertLessThanOrEqual(bounds.minX, 0.5)
        XCTAssertGreaterThanOrEqual(bounds.maxX, 69.5)
    }
}
