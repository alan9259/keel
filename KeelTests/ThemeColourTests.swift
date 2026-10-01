import XCTest
import SwiftUI
@testable import Keel

/// Colours come from the injected theme, not hard-coded hexes: the warm scales follow the
/// active theme, and no feature/component/model source reintroduces a fixed colour.
final class ThemeColourTests: XCTestCase {

    private let earthy = KeelTheme.resolve(themeID: "earthy", isDark: false)
    private let slate = KeelTheme.resolve(themeID: "slate", isDark: false)
    private let dark = KeelTheme.resolve(themeID: "earthy", isDark: true)

    func testSeverityScaleReadsFromTheTheme() {
        XCTAssertEqual(SymptomSeverity.mild.color(in: earthy), earthy.sand)
        XCTAssertEqual(SymptomSeverity.moderate.color(in: earthy), earthy.copper)
        XCTAssertEqual(SymptomSeverity.severe.color(in: earthy), earthy.accent)
    }

    func testEnergyScaleReadsFromTheTheme() {
        XCTAssertEqual(EnergyLevel.drained.color(in: earthy), earthy.accent)
        XCTAssertEqual(EnergyLevel.low.color(in: earthy), earthy.copper)
        XCTAssertEqual(EnergyLevel.okay.color(in: earthy), earthy.sand)
        XCTAssertEqual(EnergyLevel.good.color(in: earthy), earthy.sageSoft)
        XCTAssertEqual(EnergyLevel.charged.color(in: earthy), earthy.sage)
    }

    /// Regression: the scales used fixed hexes, so a theme with a different accent (or
    /// dark mode) left them on the default rosewood. They now follow the theme.
    func testScalesFollowTheChosenThemeAndMode() {
        XCTAssertNotEqual(earthy.accent, slate.accent)
        XCTAssertEqual(SymptomSeverity.severe.color(in: slate), slate.accent)
        XCTAssertNotEqual(SymptomSeverity.severe.color(in: earthy), SymptomSeverity.severe.color(in: slate))
        // Dark mode keeps the theme's accent but lightens amber, so the copper step shifts.
        XCTAssertEqual(EnergyLevel.low.color(in: dark), dark.copper)
        XCTAssertNotEqual(EnergyLevel.low.color(in: dark), EnergyLevel.low.color(in: earthy))
    }

    /// Guard: hard-coded colours belong only in `Keel/DesignSystem/` (the tokens). Any
    /// `Color(hex:)`, RGB, or named system colour elsewhere in shipping code fails here.
    /// `DebugHarness` is debug-only and exempt; Sign in with Apple's `.black` button style
    /// is Apple-mandated (not a colour) and doesn't match these patterns.
    func testNoHardCodedColoursOutsideTheDesignSystem() throws {
        let appDir = URL(fileURLWithPath: #filePath)        // …/KeelTests/ThemeColourTests.swift
            .deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("Keel")
        let patterns = [
            #"Color\(hex:"#, #"Color\(red:"#, #"UIColor\(red:"#, #"Color\(white:"#,
            #"Color\.(red|orange|yellow|green|blue|pink|purple|gray|black|white|brown|cyan|mint|teal|indigo)\b"#,
            #"[(\s?:,]\.(red|orange|yellow|green|blue|pink|purple|gray|black|white|brown|cyan|mint|teal|indigo)(\.opacity|\)|\s)"#,
        ].map { try! NSRegularExpression(pattern: $0) }

        var offenders: [String] = []
        let files = FileManager.default.enumerator(at: appDir, includingPropertiesForKeys: nil)
        while let url = files?.nextObject() as? URL {
            guard url.pathExtension == "swift",
                  !url.path.contains("/Keel/DesignSystem/"),
                  url.lastPathComponent != "DebugHarness.swift" else { continue }
            let lines = try String(contentsOf: url, encoding: .utf8).components(separatedBy: "\n")
            for (i, line) in lines.enumerated() {
                let code = line.components(separatedBy: "//").first ?? line
                if code.contains("signInWithAppleButtonStyle") { continue }
                let range = NSRange(code.startIndex..., in: code)
                if patterns.contains(where: { $0.firstMatch(in: code, range: range) != nil }) {
                    offenders.append("\(url.lastPathComponent):\(i + 1): \(line.trimmingCharacters(in: .whitespaces))")
                }
            }
        }
        XCTAssertTrue(FileManager.default.fileExists(atPath: appDir.path), "source dir not found: \(appDir.path)")
        XCTAssertEqual(offenders, [], "Use theme tokens instead:\n" + offenders.joined(separator: "\n"))
    }
}
