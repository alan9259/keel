import XCTest
import SwiftData
import HealthKit
@testable import Keel

/// Menstrual flow from Apple Health (restored for V1): imported into the local-only
/// health store, labelled as from Apple Health, shown once when she also logs that day
/// in Keel, and never used for an estimate.
@MainActor
final class AppleHealthPeriodsTests: XCTestCase {

    private let cal = TestStore.utcCalendar
    private var day: Date { Date.now.startOfDay }

    // MARK: Reading Apple Health's values

    func testFlowMappingDropsNoneAndKeepsTheHeaviestPerDay() {
        let d = day
        let samples: [(day: Date, value: Int)] = [
            (d, HKCategoryValueVaginalBleeding.light.rawValue),
            (d, HKCategoryValueVaginalBleeding.heavy.rawValue),          // same day, heavier wins
            (d.adding(days: -1), HKCategoryValueVaginalBleeding.none.rawValue),   // not a period day
            (d.adding(days: -2), HKCategoryValueVaginalBleeding.unspecified.rawValue),
        ]
        XCTAssertEqual(HealthKitService.flowByDay(samples), [d: .heavy, d.adding(days: -2): .unspecified])
    }

    func testSnapshotWithOnlyFlowIsNotEmpty() {
        var s = HealthSnapshot(); s.menstrualFlow = [day: .light]
        XCTAssertFalse(s.isEmpty)
    }

    // MARK: Import

    func testImportIsIdempotentAndRefreshesTheLevel() {
        let env = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
        var s = HealthSnapshot(); s.menstrualFlow = [day: .light, day.adding(days: -1): .medium]
        env.ingestHealthSnapshot(s)
        env.ingestHealthSnapshot(s)
        XCTAssertEqual(rows(env).count, 2)

        s.menstrualFlow[day] = .heavy                      // Apple Health revised the day
        env.ingestHealthSnapshot(s)
        XCTAssertEqual(env.cycle.importedFlow(from: day, to: day)[day], .heavy)
        XCTAssertEqual((try? env.context.fetchCount(FetchDescriptor<CycleEntry>())) ?? -1, 0)
    }

    func testSwitchingPeriodsOffStopsImport() {
        var s = HealthSnapshot(); s.menstrualFlow = [day: .light]
        XCTAssertTrue(HealthSyncCatalog.filter(s, disabled: ["periods"]).menstrualFlow.isEmpty)
        XCTAssertEqual(HealthSyncCatalog.filter(s, disabled: ["steps"]).menstrualFlow, [day: .light])
    }

    // MARK: Shown once, labelled

    func testHerOwnEntryWinsAndTheDayIsShownOnce() {
        let shared = day, healthOnly = day.adding(days: -1), hersOnly = day.adding(days: -2)
        let marks = CycleDayMark.merge(manual: [shared: .heavy, hersOnly: .light],
                                       imported: [shared: .light, healthOnly: .medium])
        XCTAssertEqual(marks.count, 3)
        XCTAssertEqual(marks[shared], CycleDayMark(level: .heavy, fromAppleHealth: false))
        XCTAssertEqual(marks[healthOnly], CycleDayMark(level: .medium, fromAppleHealth: true))
        XCTAssertEqual(marks[hersOnly], CycleDayMark(level: .light, fromAppleHealth: false))
    }

    // MARK: Never used for an estimate

    func testImportedPeriodsNeverFeedTheEstimate() {
        let env = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
        // Three regular imported cycles that would otherwise give an estimate and a phase.
        var s = HealthSnapshot()
        for start in [-84, -56, -28, 0] {
            for i in 0..<4 { s.menstrualFlow[day.adding(days: start + i)] = .medium }
        }
        env.ingestHealthSnapshot(s)

        let stats = env.cycle.stats(now: .now)
        XCTAssertNil(stats.lastStart)
        XCTAssertFalse(stats.canEstimate)
        XCTAssertNil(stats.estimatedWindow())
        XCTAssertEqual(env.cycle.estimatedPhase(on: .now), .unknown)
        XCTAssertTrue(PatternEngine.build(context: env.context).findings().isEmpty)
    }

    // MARK: Removal

    func testRemoveImportedAndDeleteAllClearImportedPeriods() {
        let env = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
        var s = HealthSnapshot(); s.menstrualFlow = [day: .light]
        env.ingestHealthSnapshot(s)
        env.healthIngestor.purgeAllImportedHealthData()
        XCTAssertTrue(rows(env).isEmpty)

        env.ingestHealthSnapshot(s)
        env.eraseAllData()
        XCTAssertTrue(rows(env).isEmpty)
    }

    override func tearDown() {
        AppLockService().disable()
        SettingsStore().resetToDefaults()
        AuthService().signOut()
    }

    private func rows(_ env: AppEnvironment) -> [HealthFlowSample] {
        (try? env.context.fetch(FetchDescriptor<HealthFlowSample>())) ?? []
    }
}
