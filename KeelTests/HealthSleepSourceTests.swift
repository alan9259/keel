import XCTest
import SwiftData
@testable import Keel

/// Apple Health is the source of truth for sleep, but a value she typed by hand
/// must not be overwritten: Health and manual entries own different days and never
/// compete. Health also self-corrects its own earlier readings (the inflated 13.2
/// hrs bug was a Health row).
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

    private func sleepRow(on d: Date) -> ActivityLog? {
        let all = (try? context.fetch(FetchDescriptor<ActivityLog>(
            predicate: #Predicate { $0.activityID == "sleep" && $0.deletedAt == nil }))) ?? []
        return all.first { $0.date.startOfDay == d.startOfDay }
    }

    func testHealthPreservesManualFillsGapsAndSelfCorrects() {
        let manualDay = day(-1), gapDay = day(-2), staleDay = day(-3)
        // A manual entry she typed, and a stale Health entry (the 13.2 hrs bug).
        context.insert(ActivityLog(date: manualDay, activityID: "sleep", amount: 6, source: .manual, ownerID: "o"))
        context.insert(ActivityLog(date: staleDay, activityID: "sleep", amount: 13.2, source: .healthKit, ownerID: "o"))
        try? context.save()

        _ = ingestor.ingest(HealthSnapshot(sleepByDay: [manualDay: 7.5, gapDay: 8.0, staleDay: 7.0]))

        // Her manual entry is untouched (Health didn't compete over her day).
        XCTAssertEqual(sleepRow(on: manualDay)?.amount, 6)
        XCTAssertEqual(sleepRow(on: manualDay)?.source, .manual)
        // A day she never logged is filled from Health.
        XCTAssertEqual(sleepRow(on: gapDay)?.amount, 8.0)
        XCTAssertEqual(sleepRow(on: gapDay)?.source, .healthKit)
        // Health's own earlier (inflated) reading is corrected.
        XCTAssertEqual(sleepRow(on: staleDay)?.amount, 7.0)
        XCTAssertEqual(sleepRow(on: staleDay)?.source, .healthKit)
    }

    func testNewSleepFromHealthIsTaggedHealthKit() {
        _ = ingestor.ingest(HealthSnapshot(sleepByDay: [day(-1): 7.0]))
        XCTAssertEqual(sleepRow(on: day(-1))?.source, .healthKit)
    }
}
