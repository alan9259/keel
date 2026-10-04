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

    /// Regression (review): the month came from the phone's calendar, so on the Hebrew
    /// calendar (Elul is month 13, as on 1 Sep 2026) the month-name lookup crashed. The
    /// summary always means the Gregorian month, whatever calendar the phone uses.
    func testOtherPhoneCalendarsStillMeanTheGregorianMonth() {
        var hebrew = Calendar(identifier: .hebrew); hebrew.timeZone = TimeZone(identifier: "UTC")!
        let firstSeptember = Date(timeIntervalSince1970: 1_788_220_800)   // 1 Sep 2026, UTC
        XCTAssertEqual(hebrew.component(.month, from: firstSeptember), 13)

        let summary = MonthSummary.build(entries: [
            entry(at(2, month: 9)), entry(at(30, month: 9)), entry(at(1, month: 10)),
        ], now: firstSeptember, calendar: hebrew)
        XCTAssertEqual(summary.heading(calendar: hebrew), "September so far")
        XCTAssertEqual(summary.daysCheckedIn, 2)                    // 2 and 30 Sep, not 1 Oct

        for id in [Calendar.Identifier.coptic, .ethiopicAmeteMihret, .islamicUmmAlQura, .chinese] {
            XCTAssertEqual(MonthSummary.build(entries: [], now: firstSeptember, calendar: Calendar(identifier: id))
                .heading(calendar: Calendar(identifier: id)), "September so far", "\(id)")
        }
    }

    /// The store path: only this month's live check-ins, with their symptoms and notes.
    @MainActor
    func testCurrentReadsThisMonthFromTheStore() {
        let env = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
        _ = env.checkIns.create(mood: .okay, energy: 60, notes: "In the month", symptoms: [], date: at(3))
        _ = env.checkIns.create(mood: .okay, energy: 60, notes: "Last month", symptoms: [], date: at(30, month: 9))
        let deleted = env.checkIns.create(mood: .okay, energy: 60, notes: "Deleted", symptoms: [], date: at(4))
        deleted.softDelete(); try? env.context.save()

        let summary = MonthSummary.current(context: env.context, now: now, calendar: cal)
        XCTAssertEqual(summary.daysCheckedIn, 1)
        XCTAssertEqual(summary.notes.map(\.text), ["In the month"])
    }

    /// Regression: earlier builds stored AI-written daily reflections and companion chat.
    /// On update both are deleted, and neither comes back from a backup.
    @MainActor
    func testGeneratedTextIsDeletedAndNotRestored() throws {
        let env = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
        env.context.insert(DailySummary(day: .now, text: "A reflection", source: .ai, ownerID: "o"))
        env.context.insert(DailySummary(day: .now.adding(days: -3), text: "Another", source: .deterministic, ownerID: "o"))
        env.context.insert(ChatMessage(role: .assistant, text: "A companion reply", ownerID: "o"))
        try env.context.save()
        let archive = try BackupService.export(context: env.context)
        XCTAssertTrue(archive.dailySummaries.isEmpty)               // no longer exported
        XCTAssertTrue(archive.chatMessages.isEmpty)

        env.purgeGeneratedText()
        XCTAssertEqual(try env.context.fetchCount(FetchDescriptor<DailySummary>()), 0)
        XCTAssertEqual(try env.context.fetchCount(FetchDescriptor<ChatMessage>()), 0)

        // An older archive that still carries them doesn't bring them back.
        var older = archive
        older.dailySummaries = [DailySummaryDTO(DailySummary(day: .now, text: "Old", source: .ai, ownerID: "o"))]
        older.chatMessages = [ChatMessageDTO(ChatMessage(role: .assistant, text: "Old reply", ownerID: "o"))]
        try BackupService.restore(from: older, into: env.context)
        XCTAssertEqual(try env.context.fetchCount(FetchDescriptor<DailySummary>()), 0)
        XCTAssertEqual(try env.context.fetchCount(FetchDescriptor<ChatMessage>()), 0)
    }
}

/// Home, Energy card: her most-picked level from three entries in the week; fewer shows the count.
/// Only check-ins where she picked an energy level count (it's optional).
final class EnergyCardLabelTests: XCTestCase {
    /// Regression (review): a check-in saved without an energy pick was stored as
    /// "okay", so three such check-ins read as "mostly okay". It's now "not recorded".
    @MainActor
    func testAMissingEnergyPickIsNotRecordedAsOkay() {
        let skipped = CheckIn(mood: .okay, energy: CheckIn.energyNotRecorded, ownerID: "o")
        XCTAssertNil(skipped.energyLevel)
        XCTAssertEqual(CheckIn(mood: .okay, energy: EnergyLevel.okay.percent, ownerID: "o").energyLevel, .okay)
        XCTAssertEqual(CheckIn(mood: .low, energy: EnergyLevel.drained.percent, ownerID: "o").energyLevel, .drained)
    }

    private let base = Date(timeIntervalSince1970: 1_791_972_000)
    private func e(_ hoursAgo: Double, _ level: EnergyLevel) -> (date: Date, level: EnergyLevel) {
        (base.addingTimeInterval(-hoursAgo * 3600), level)
    }

    func testLabelNeedsThreeEntries() {
        XCTAssertEqual(DashboardView.energyTrailing([]), "No data yet")
        XCTAssertEqual(DashboardView.energyTrailing([e(1, .okay)]), "1 entry this week")
        XCTAssertEqual(DashboardView.energyTrailing([e(1, .okay), e(2, .low)]), "2 entries this week")
        XCTAssertEqual(DashboardView.energyTrailing([e(1, .okay), e(2, .low), e(3, .okay)]), "Okay")
    }

    /// Regression: the label was an average, so Drained, Drained, Charged showed "Low",
    /// a level she never picked. It's now the level she picked most often.
    func testMostlyIsTheMostFrequentLevelNotAnAverage() {
        XCTAssertEqual(DashboardView.energyTrailing([e(1, .drained), e(30, .drained), e(60, .charged)]),
                       "Drained")
        XCTAssertEqual(DashboardView.energyTrailing([e(1, .good), e(2, .charged), e(3, .good), e(4, .low)]),
                       "Good")
    }

    /// A tie goes to the tied level she picked most recently.
    func testTieGoesToTheMostRecentlyPickedLevel() {
        XCTAssertEqual(DashboardView.mostFrequentEnergy([e(5, .low), e(1, .good), e(10, .low), e(3, .good)]), .good)
        XCTAssertEqual(DashboardView.mostFrequentEnergy([e(1, .low), e(5, .good), e(10, .low), e(3, .good)]), .low)
        XCTAssertNil(DashboardView.mostFrequentEnergy([]))
    }
}
