import XCTest
import SwiftUI
import UIKit
@testable import Keel

/// The in-app mark is drawn in code (`KeelMark`), so prove it matches Mischa's final v2
/// art (`brand/keel-icon-1024.png`): render the square form at 1024 and compare pixels.
@MainActor
final class KeelMarkTests: XCTestCase {

    private func rgba(_ image: CGImage, size: Int = 1024) -> [UInt8] {
        var buf = [UInt8](repeating: 0, count: size * size * 4)
        let ctx = CGContext(data: &buf, width: size, height: size, bitsPerComponent: 8, bytesPerRow: size * 4,
                            space: CGColorSpace(name: CGColorSpace.sRGB)!,
                            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: size, height: size))
        return buf
    }

    private func brandIcon() throws -> CGImage {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("brand/keel-icon-1024.png")
        let image = try XCTUnwrap(UIImage(contentsOfFile: url.path), "missing \(url.path)")
        return try XCTUnwrap(image.cgImage)
    }

    private func renderedMark() throws -> CGImage {
        let renderer = ImageRenderer(content: KeelMark(cornerRadius: 0).frame(width: 1024, height: 1024))
        renderer.scale = 1
        return try XCTUnwrap(renderer.cgImage)
    }

    /// Every pixel within a small tolerance of the PNG, allowing only anti-aliased edges
    /// to differ. Catches a wrong colour, size, position, stroke weight or waterline.
    func testMarkMatchesTheV2BrandArt() throws {
        let a = rgba(try renderedMark()), b = rgba(try brandIcon())
        var differing = 0, worst = 0
        for i in stride(from: 0, to: a.count, by: 4) {
            let d = (0..<3).map { abs(Int(a[i + $0]) - Int(b[i + $0])) }.max()!
            worst = max(worst, d)
            if d > 24 { differing += 1 }
        }
        let fraction = Double(differing) / Double(1024 * 1024)
        XCTAssertLessThan(fraction, 0.005, "\(differing) pixels differ by >24 (worst \(worst))")
    }

    /// The launch-screen tile in the app bundle is the v2 art (solid off-white disc,
    /// mist water), not the retired v1 (translucent disc, off-white water).
    func testLaunchMarkAssetIsV2() throws {
        let image = try XCTUnwrap(UIImage(named: "LaunchMark")?.cgImage, "LaunchMark asset missing")
        let a = rgba(image)
        func hex(_ x: Int, _ y: Int) -> String {
            let i = (y * 1024 + x) * 4
            return String(format: "#%02X%02X%02X", a[i], a[i + 1], a[i + 2])
        }
        XCTAssertEqual(hex(512, 250), "#F7F5F0") // disc above the water
        XCTAssertEqual(hex(512, 780), "#CFD4D4") // water
    }

    /// The four colours land where the art has them.
    func testMarkColoursAtKnownPoints() throws {
        let a = rgba(try renderedMark())
        func hex(_ x: Int, _ y: Int) -> String {
            let i = (y * 1024 + x) * 4
            return String(format: "#%02X%02X%02X", a[i], a[i + 1], a[i + 2])
        }
        XCTAssertEqual(hex(20, 20), "#8C4A45")   // rosewood tile
        XCTAssertEqual(hex(512, 250), "#F7F5F0") // solid off-white disc (v1 was translucent)
        XCTAssertEqual(hex(512, 780), "#CFD4D4") // mist water (v1 was off-white)
        XCTAssertEqual(hex(408, 500), "#8C4A45") // K spine
    }
}
