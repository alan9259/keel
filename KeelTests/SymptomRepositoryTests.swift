import XCTest
import SwiftData
@testable import Keel

/// The symptom catalog: seeding the built-ins idempotently, and finding-or-creating
/// custom symptoms without duplicating (case-insensitively). Duplicated symptoms
/// would fracture history and reporting, so this matters.
@MainActor
final class SymptomRepositoryTests: XCTestCase {

    private var context: ModelContext!
    private var repo: SymptomRepository!

    override func setUpWithError() throws {
        // syncBuiltIns is version-gated in UserDefaults; clear it so a fresh store
        // seeds deterministically.
        UserDefaults.standard.removeObject(forKey: "keel.symptomCatalogVersion")
        context = TestStore.makeContext()
        repo = SymptomRepository(context: context, ownerID: TestStore.ownerID)
    }

    override func tearDownWithError() throws { context = nil; repo = nil }

    func testSyncBuiltInsSeedsCatalog() {
        repo.syncBuiltIns()
        XCTAssertFalse(repo.allActive().isEmpty)
        // Built-ins are global reference data (empty ownerID).
        XCTAssertTrue(repo.allActive().contains { !$0.isCustom })
    }

    func testSyncBuiltInsIsIdempotent() {
        repo.syncBuiltIns()
        let first = repo.allActive().count
        repo.syncBuiltIns() // second call must not duplicate
        XCTAssertEqual(repo.allActive().count, first)
    }

    func testFindOrCreateCustomCreatesOnce() {
        let a = repo.findOrCreateCustom(name: "Itchy ears", category: .body)
        let b = repo.findOrCreateCustom(name: "Itchy ears", category: .body)
        XCTAssertEqual(a.id, b.id, "same name should return the same symptom")
        XCTAssertTrue(a.isCustom)
    }

    func testFindOrCreateCustomIsCaseAndWhitespaceInsensitive() {
        let a = repo.findOrCreateCustom(name: "Brain fog", category: .cognition)
        let b = repo.findOrCreateCustom(name: "  brain FOG ", category: .cognition)
        XCTAssertEqual(a.id, b.id)
    }

    func testSearchFiltersByName() {
        _ = repo.findOrCreateCustom(name: "Night sweats", category: .body)
        _ = repo.findOrCreateCustom(name: "Headache", category: .aches)
        XCTAssertEqual(repo.search("sweat").map(\.name), ["Night sweats"])
        // Empty query returns everything active.
        XCTAssertEqual(repo.search("   ").count, repo.allActive().count)
    }

    func testGroupedOnlyIncludesNonEmptyCategories() {
        _ = repo.findOrCreateCustom(name: "Custom body thing", category: .body)
        let grouped = repo.grouped()
        XCTAssertTrue(grouped.allSatisfy { !$0.symptoms.isEmpty })
        XCTAssertTrue(grouped.contains { $0.category == .body })
    }
}
