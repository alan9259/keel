import XCTest
import SwiftData
@testable import Keel

/// 3D: a medicine saved from the old catalog kept its brand example, e.g. "Oral
/// micronised progesterone (e.g. Prometrium)". The launch cleanup tidies it to the
/// generic name and leaves everything else she typed alone.
@MainActor
final class MedicationNameCleanupTests: XCTestCase {

    func testStripsOnlyATrailingExample() {
        XCTAssertEqual(MedicationRepository.nameWithoutExample("Oral micronised progesterone (e.g. Prometrium)"),
                       "Oral micronised progesterone")
        XCTAssertEqual(MedicationRepository.nameWithoutExample("Oestrogen gel (E.G. Estrogel) "), "Oestrogen gel")
        // Her own parentheses, or an example that isn't trailing, are kept.
        XCTAssertEqual(MedicationRepository.nameWithoutExample("Magnesium (glycinate)"), "Magnesium (glycinate)")
        XCTAssertEqual(MedicationRepository.nameWithoutExample("Combined patch (oestrogen and progestogen)"),
                       "Combined patch (oestrogen and progestogen)")
        XCTAssertEqual(MedicationRepository.nameWithoutExample("Vitamin D"), "Vitamin D")
        // Never empties a name.
        XCTAssertEqual(MedicationRepository.nameWithoutExample("(e.g. Prometrium)"), "(e.g. Prometrium)")
    }

    func testCleanupRenamesSavedEntriesAndIsIdempotent() throws {
        let context = TestStore.makeContext()
        let repo = MedicationRepository(context: context, ownerID: TestStore.ownerID)
        let old = Medication(name: "Oral micronised progesterone (e.g. Prometrium)", dosage: "100mg",
                             timing: "Night", kind: .treatment, ownerID: "test-owner")
        let mine = Medication(name: "Magnesium (glycinate)", dosage: "400mg", timing: "Night",
                              kind: .supplement, ownerID: "test-owner")
        context.insert(old); context.insert(mine); try context.save()

        repo.stripExampleBrandsFromNames()
        repo.stripExampleBrandsFromNames()

        XCTAssertEqual(old.name, "Oral micronised progesterone")
        XCTAssertEqual(old.dosage, "100mg")              // nothing else changes
        XCTAssertEqual(mine.name, "Magnesium (glycinate)")
    }
}
