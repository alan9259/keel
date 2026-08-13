import XCTest
@testable import Keel

/// The treatment picker's search filter, plus content-regression guards for the
/// four medication-picker feedbacks (no brands, no "why" indication lines, no
/// empty categories).
final class TreatmentCatalogTests: XCTestCase {

    private func item(_ name: String) -> TreatmentCatalog.Item {
        TreatmentCatalog.Item(name: name, method: nil, offLabel: nil, compounded: nil)
    }

    private func group(_ id: String, _ names: [String]) -> TreatmentCatalog.Group {
        TreatmentCatalog.Group(id: id, kind: .treatment, section: id, title: id,
                               note: nil, defaultMethod: nil, items: names.map(item))
    }

    // MARK: Search filter

    func testFilterBlankQueryReturnsEverything() {
        let g = [group("a", ["One", "Two"]), group("b", ["Three"])]
        XCTAssertEqual(TreatmentCatalog.filter(g, matching: "   ").count, 2)
        XCTAssertEqual(TreatmentCatalog.filter(g, matching: "").count, 2)
    }

    func testFilterKeepsOnlyMatchingItemsAndDropsEmptyGroups() {
        let g = [group("oestrogen", ["Oestrogen patch", "Oestrogen gel"]),
                 group("progesterone", ["Progestogen IUD"])]
        let result = TreatmentCatalog.filter(g, matching: "gel")
        XCTAssertEqual(result.count, 1)                       // progesterone group drops out
        XCTAssertEqual(result.first?.items.map(\.name), ["Oestrogen gel"])
    }

    func testFilterIsCaseInsensitiveAndSubstring() {
        let g = [group("oestrogen", ["Oestrogen patch"])]
        XCTAssertEqual(TreatmentCatalog.filter(g, matching: "PATCH").first?.items.count, 1)
        XCTAssertEqual(TreatmentCatalog.filter(g, matching: "oestro").first?.items.count, 1)
    }

    func testFilterNoMatchReturnsEmpty() {
        let g = [group("oestrogen", ["Oestrogen patch"])]
        XCTAssertTrue(TreatmentCatalog.filter(g, matching: "vitamin").isEmpty)
    }

    // MARK: Bundled catalog content (the feedback)

    @MainActor
    func testBundledTreatmentsHaveNoBrandsNoIndicationNotesNoEmpties() {
        let treatments = TreatmentCatalogService().catalog.groups.filter { $0.kind == .treatment }
        XCTAssertFalse(treatments.isEmpty, "catalog failed to load")

        for g in treatments {
            // No "why" indication lines: treatment groups describe what, not why,
            // so they carry no note at all.
            XCTAssertNil(g.note, "\(g.id) has an indication/why note")
            // No empty categories.
            XCTAssertFalse(g.items.isEmpty, "\(g.id) is an empty category")
        }

        // No built-in brand names, generic descriptive entries only.
        let brands = ["Estradot", "Estraderm", "Estrogel", "Estramon", "AndroFeme",
                      "Testogel", "Prometrium", "Femoston", "Mirena"]
        let names = treatments.flatMap { $0.items.map(\.name) }
        for brand in brands {
            XCTAssertFalse(names.contains { $0.localizedCaseInsensitiveContains(brand) },
                           "brand '\(brand)' is still listed")
        }
    }
}
