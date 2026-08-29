import XCTest
import PDFKit
@testable import Keel

/// Guards the PDF output's invariants that are easy to regress: exactly two A4 pages,
/// and no author / creator / producer metadata (the OS/build string Quartz stamps into
/// Producer must be blanked). The visual layout is checked with the `-uitGPSummary`
/// probe; this locks the structural contract.
@MainActor
final class GPSummaryPDFRendererTests: XCTestCase {

    private func minimalDocument() -> GPSummaryDocument {
        GPSummaryDocument(
            name: nil, age: nil,
            periodLabel: "7 June to 29 August 2026",
            checkInsLabel: "0 of 84 days",
            priorities: [],
            symptomStats: [],
            checkInDaysThisPeriod: 0,
            checkInDaysPreviousPeriod: 0,
            previousWindowDayCount: 84,
            defaultSymptomMaxRows: 6,
            impactLine: nil,
            cycle: GPCycleBlock(lastPeriodStart: nil, periodsRecorded: 0,
                                cycleLengthRange: GPSummaryCopy.notEnoughPeriodsForRange,
                                flow: nil, intermenstrualBleeding: "Not recorded", notApplicable: nil),
            mht: GPMedTable(rows: [], overflowNote: nil),
            otherMeds: GPMedTable(rows: [], overflowNote: nil),
            supplements: GPMedTable(rows: [], overflowNote: nil),
            treatmentChanges: [],
            sleepLine: "0 of 0", energyLine: nil, moodLine: nil,
            questions: [],
            generatedOn: Date(timeIntervalSince1970: 1_700_000_000))
    }

    func testRendersTwoA4PagesWithNoIdentifyingMetadata() throws {
        let data = GPSummaryPDFRenderer(document: minimalDocument()).render()
        let pdf = try XCTUnwrap(PDFDocument(data: data), "render() must produce a valid PDF")

        XCTAssertEqual(pdf.pageCount, 2, "the summary is a hard two pages")
        let a4 = pdf.page(at: 0)?.bounds(for: .mediaBox)
        XCTAssertEqual(a4?.width ?? 0, 595, accuracy: 1)
        XCTAssertEqual(a4?.height ?? 0, 842, accuracy: 1)

        // No author/creator/producer survives (blanks are spaces only).
        let attrs = pdf.documentAttributes ?? [:]
        for key: PDFDocumentAttribute in [.authorAttribute, .creatorAttribute, .producerAttribute] {
            let value = (attrs[key] as? String) ?? ""
            XCTAssertTrue(value.trimmingCharacters(in: .whitespaces).isEmpty,
                          "\(key) must carry no identifier, got '\(value)'")
        }
    }

    func testRendersARichDocumentWithoutOverflowingPageOne() {
        var doc = minimalDocument()
        doc.priorities = ["One", "Two", "Three"]           // triggers the 5-row cap
        doc.symptomStats = (1...8).map {
            GPSymptomStat(name: "Symptom \($0)", isCustom: false, daysThisPeriod: 20 - $0,
                          daysPreviousPeriod: 0, meanSeverity: 2, lastLogged: Date())
        }
        doc.checkInDaysThisPeriod = 42
        doc.impactLine = "Affecting: sleep. Overall impact: Significant."
        let renderer = GPSummaryPDFRenderer(document: doc)
        _ = renderer.render()
        XCTAssertFalse(renderer.pageOneOverflowed, "page 1 must fit even with priorities, impact and a full table")
    }
}
