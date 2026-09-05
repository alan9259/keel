import XCTest
import SwiftData
@testable import Keel

/// Imported Apple Health sleep now lives in the local-only health store
/// (`HealthActivitySample`), separate from her manual `ActivityLog` entries. The merge
/// (`MergedActivity`) prefers her own value for a day and fills gaps from Health; Health
/// still self-corrects its own earlier readings (the inflated 13.2 hrs bug was a Health
/// row).
@MainActor
final class HealthSleepSourceTests: XCTestCase {

    private var context: ModelContext!
    private var ingestor: HealthIngestor!

    override func setUpWithError() throws {
        context = TestStore.makeContext()
        let symptoms = SymptomRepository(context: context, ownerID: TestStore.ownerID)
        ingestor = HealthIngestor(context: context, ownerID: TestStore.ownerID, symptoms: symptoms)
    }

    override func tearDownWithError() throws { context = nil; ingestor = nil }

    private func day(_ offset: Int) -> Date { Date.now.startOfDay.adding(days: offset) }

    private func manualSleep(on d: Date) -> ActivityLog? {
        (((try? context.fetch(FetchDescriptor<ActivityLog>(
            predicate: #Predicate { $0.activityID == "sleep" && $0.deletedAt == nil }))) ?? []))
            .first { $0.date.startOfDay == d.startOfDay }
    }
    private func importedSleep(on d: Date) -> HealthActivitySample? {
        (((try? context.fetch(FetchDescriptor<HealthActivitySample>(
            predicate: #Predicate { $0.activityID == "sleep" && $0.deletedAt == nil }))) ?? []))
            .first { $0.date.startOfDay == d.startOfDay }
    }

    func testImportedSleepLandsInHealthStoreAndMergePrefersManual() {
        let manualDay = day(-1), gapDay = day(-2), staleDay = day(-3)
        // A manual entry she typed, and a stale imported Health value (the 13.2 hrs bug).
        context.insert(ActivityLog(date: manualDay, activityID: "sleep", amount: 6, source: .manual, ownerID: "o"))
        context.insert(HealthActivitySample(date: staleDay, activityID: "sleep", amount: 13.2, ownerID: "o"))
        try? context.save()

        _ = ingestor.ingest(HealthSnapshot(sleepByDay: [manualDay: 7.5, gapDay: 8.0, staleDay: 7.0]))

        // Imports go to the health store, never ActivityLog. Her manual entry is untouched.
        XCTAssertEqual(manualSleep(on: manualDay)?.amount, 6)
        XCTAssertNil(manualSleep(on: gapDay))
        XCTAssertEqual(importedSleep(on: gapDay)?.amount, 8.0)
        XCTAssertEqual(importedSleep(on: staleDay)?.amount, 7.0) // Health self-corrected

        // The merge: her own value wins her day; gaps + corrections come from Health.
        let manual = (try? context.fetch(FetchDescriptor<ActivityLog>())) ?? []
        let imported = (try? context.fetch(FetchDescriptor<HealthActivitySample>())) ?? []
        XCTAssertEqual(MergedActivity.amount("sleep", on: manualDay, manual: manual, imported: imported), 6)
        XCTAssertEqual(MergedActivity.amount("sleep", on: gapDay, manual: manual, imported: imported), 8.0)
        XCTAssertEqual(MergedActivity.amount("sleep", on: staleDay, manual: manual, imported: imported), 7.0)
    }

    func testNewSleepFromHealthGoesToHealthStoreOnly() {
        _ = ingestor.ingest(HealthSnapshot(sleepByDay: [day(-1): 7.0]))
        XCTAssertEqual(importedSleep(on: day(-1))?.amount, 7.0)
        XCTAssertNil(manualSleep(on: day(-1)))
    }
}
