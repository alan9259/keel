import XCTest
import SwiftData
@testable import Keel

/// Looking back is a fixed-wording summary of the current month (no generated text),
/// and the stored AI reflections are deleted on update.
final class LookingBackTests: XCTestCase {

    private let cal = TestStore.utcCalendar
    /// 14 October 2026, 10:00 UTC.
    private let now = Date(timeIntervalSince1970: 1_791_972_000)
    private func at(_ dayOfMonth: Int, hour: Int = 9, month: Int = 10) -> Date {
        cal.date(from: DateComponents(year: 2026, month: month, day: dayOfMonth, hour: hour))!
    }
    private func entry(_ date: Date, _ symptoms: [String] = [], notes: String? = nil) -> MonthSummary.Entry {
        MonthSummary.Entry(id: UUID(), date: date, symptoms: symptoms, notes: notes)
    }

    func testCountsDaysSymptomsAndNotesForThisMonthOnly() {
        let summary = MonthSummary.build(entries: [
            entry(at(2), ["Hot flushes", "Headaches"], notes: "Rough night"),
            entry(at(2, hour: 18), ["Hot flushes"]),                 // same day: counted once
            entry(at(9), ["Hot flushes"], notes: "  "),              // blank note ignored
            entry(at(14), ["Brain fog"], notes: "Better today"),
            entry(at(28, month: 9), ["Hot flushes"], notes: "Last month"), // outside the month
        ], now: now, calendar: cal)

        XCTAssertEqual(summary.daysCheckedIn, 3)
        XCTAssertEqual(summary.checkInLine, "You checked in on 3 days this month.")
        XCTAssertEqual(summary.topSymptoms, [
            .init(name: "Hot flushes", days: 2),
            .init(name: "Brain fog", days: 1),
            .init(name: "Headaches", days: 1),                     // ties by name
        ])
        XCTAssertEqual(summary.notes.map(\.text), ["Better today", "Rough night"]) // newest first
        XCTAssertEqual(summary.heading(calendar: cal), "October so far")
    }

    func testEmptyMonthHasHonestWording() {
        let summary = MonthSummary.build(entries: [], now: now, calendar: cal)
        XCTAssertEqual(summary.daysCheckedIn, 0)
        XCTAssertEqual(summary.checkInLine, "No check-ins yet this month.")
        XCTAssertTrue(summary.topSymptoms.isEmpty)
        XCTAssertTrue(summary.notes.isEmpty)
    }

    func testOneDayIsSingular() {
        let summary = MonthSummary.build(entries: [entry(at(3))], now: now, calendar: cal)
        XCTAssertEqual(summary.checkInLine, "You checked in on 1 day this month.")
        XCTAssertEqual(MonthSummary.days(1), "1 day")
        XCTAssertEqual(MonthSummary.days(4), "4 days")
    }

    func testTopSymptomsCappedAtFive() {
        let names = ["A", "B", "C", "D", "E", "F", "G"]
        let summary = MonthSummary.build(entries: [entry(at(5), names)], now: now, calendar: cal)
        XCTAssertEqual(summary.topSymptoms.count, MonthSummary.topLimit)
    }

    func testMonthBoundaryFirstAndLastDay() {
        let summary = MonthSummary.build(entries: [
            entry(at(1, hour: 0)), entry(at(31, hour: 23)), entry(at(1, hour: 0, month: 11)),
        ], now: now, calendar: cal)
        XCTAssertEqual(summary.daysCheckedIn, 2)
    }

    /// Regression: earlier builds stored AI-written daily reflections. On update they're
    /// deleted, and none come back from a backup.
    @MainActor
    func testStoredReflectionsAreDeletedAndNotRestored() throws {
        let env = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
        env.context.insert(DailySummary(day: .now, text: "A reflection", source: .ai, ownerID: "o"))
        env.context.insert(DailySummary(day: .now.adding(days: -3), text: "Another", source: .deterministic, ownerID: "o"))
        try env.context.save()
        let archive = try BackupService.export(context: env.context)
        XCTAssertTrue(archive.dailySummaries.isEmpty)               // no longer exported

        env.purgeStoredReflections()
        XCTAssertEqual(try env.context.fetchCount(FetchDescriptor<DailySummary>()), 0)

        // An older archive that still carries reflections doesn't bring them back.
        var older = archive
        older.dailySummaries = [DailySummaryDTO(DailySummary(day: .now, text: "Old", source: .ai, ownerID: "o"))]
        try BackupService.restore(from: older, into: env.context)
        XCTAssertEqual(try env.context.fetchCount(FetchDescriptor<DailySummary>()), 0)
    }
}

/// Home, Energy card: a word only from three entries in the week; fewer shows the count.
final class EnergyCardLabelTests: XCTestCase {
    func testLabelNeedsThreeEntries() {
        XCTAssertEqual(DashboardView.energyTrailing(entryCount: 0, averagePercent: 0), "No data yet")
        XCTAssertEqual(DashboardView.energyTrailing(entryCount: 1, averagePercent: 50), "1 entry this week")
        XCTAssertEqual(DashboardView.energyTrailing(entryCount: 2, averagePercent: 50), "2 entries this week")
        XCTAssertTrue(DashboardView.energyTrailing(entryCount: 3, averagePercent: 50).hasPrefix("mostly "))
        XCTAssertTrue(DashboardView.energyTrailing(entryCount: 9, averagePercent: 50).hasPrefix("mostly "))
    }
}
