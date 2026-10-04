import XCTest
import SwiftData
import HealthKit
@testable import Keel

/// Menstrual flow from Apple Health (restored for V1): imported into the local-only
/// health store, labelled as from Apple Health, shown once when she also logs that day
/// in Keel, and never used for an estimate.
@MainActor
final class AppleHealthPeriodsTests: XCTestCase {

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

    /// Starts that, logged by hand, give an estimate (the regular set) and a cycle-length
    /// finding (the irregular set). Imported from Apple Health, they must give neither.
    private let regularStarts = [-84, -56, -28, 0]
    private let irregularStarts = [-95, -70, -35, 0]   // 25, 35 and 35 days apart

    private func flow(_ starts: [Int]) -> [Date: FlowLevel] {
        var out: [Date: FlowLevel] = [:]
        for start in starts { for i in 0..<4 { out[day.adding(days: start + i)] = .medium } }
        return out
    }

    /// Positive control: the same days logged in Keel DO drive the estimate and the
    /// pattern, so the test below would catch a leak.
    func testControlTheSameDaysLoggedInKeelDriveEstimateAndPattern() {
        let regular = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
        for (d, level) in flow(regularStarts) { regular.cycle.setFlow(level, on: d) }
        XCTAssertTrue(regular.cycle.stats(now: .now).canEstimate)

        let irregular = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
        for (d, level) in flow(irregularStarts) { irregular.cycle.setFlow(level, on: d) }
        XCTAssertEqual(PatternEngine.build(context: irregular.context).findings().map(\.kind), [.cycleVariability])
    }

    func testImportedPeriodsNeverFeedTheEstimateOrPatterns() {
        let regular = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
        var s = HealthSnapshot(); s.menstrualFlow = flow(regularStarts)
        regular.ingestHealthSnapshot(s)
        let stats = regular.cycle.stats(now: .now)
        XCTAssertNil(stats.lastStart)
        XCTAssertFalse(stats.canEstimate)
        XCTAssertNil(stats.estimatedWindow())
        XCTAssertEqual(regular.cycle.estimatedPhase(on: .now), .unknown)

        let irregular = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
        var t = HealthSnapshot(); t.menstrualFlow = flow(irregularStarts)
        irregular.ingestHealthSnapshot(t)
        XCTAssertTrue(PatternEngine.build(context: irregular.context).findings().isEmpty)
    }

    // MARK: Deleted in Apple Health

    /// Regression (review): a period day she deleted in Apple Health (or changed to
    /// "none") stayed on the Cycle screen. Within the read window, it's now removed.
    func testDayDeletedInAppleHealthIsRemoved() {
        let env = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
        var s = HealthSnapshot()
        s.menstrualFlow = [day: .light, day.adding(days: -1): .medium]
        s.flowWindowStart = day.adding(days: -30)
        env.ingestHealthSnapshot(s)

        s.menstrualFlow = [day: .light]                    // she deleted yesterday in Health
        env.ingestHealthSnapshot(s)
        XCTAssertEqual(env.cycle.importedFlow(from: day.adding(days: -5), to: day), [day: .light])

        s.menstrualFlow = [:]                              // and then everything
        env.ingestHealthSnapshot(s)
        XCTAssertTrue(rows(env).isEmpty)
    }

    /// Days older than the read window are left alone (the read didn't cover them).
    func testDaysBeforeTheWindowAreKept() {
        let env = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
        var s = HealthSnapshot(); s.menstrualFlow = [day.adding(days: -400): .medium]
        env.ingestHealthSnapshot(s)
        s.menstrualFlow = [:]; s.flowWindowStart = day.adding(days: -365)
        env.ingestHealthSnapshot(s)
        XCTAssertEqual(rows(env).count, 1)
    }

    /// Without a window (her answer for periods isn't known yet, or periods are switched
    /// off) an empty read removes nothing.
    func testNoWindowMeansNothingIsRemoved() {
        let env = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
        var s = HealthSnapshot(); s.menstrualFlow = [day: .light]
        env.ingestHealthSnapshot(s)
        env.ingestHealthSnapshot(HealthSnapshot())
        XCTAssertEqual(rows(env).count, 1)

        var off = HealthSnapshot(); off.flowWindowStart = day.adding(days: -30)
        env.ingestHealthSnapshot(HealthSyncCatalog.filter(off, disabled: ["periods"]))
        XCTAssertEqual(rows(env).count, 1)
    }

    /// The real sync only removes deleted days once she has answered for periods: on an
    /// upgrade (periods not asked yet) an empty read means "not shared", not "none".
    func testSyncRemovesDeletedDaysOnlyOnceSheHasAnsweredForPeriods() async {
        final class Source: HealthDataSource {
            var status: HealthRequestStatus = .shouldRequest
            func requestAuthorization() async -> Bool { true }
            func requestStatus() async -> HealthRequestStatus { status }
            func snapshot(lastDays: Int) async -> HealthSnapshot {
                var s = HealthSnapshot()                          // no periods returned
                s.sleepByDay = [Date.now.startOfDay: 7]
                s.flowWindowStart = Date.now.startOfDay.adding(days: -lastDays)
                return s
            }
        }
        let source = Source()
        let env = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true),
                                 provider: NoopSyncProvider(), health: source)
        env.users.setHealthKitAuthorized(true)
        env.context.insert(HealthFlowSample(date: day.adding(days: -2), flowLevel: .medium, ownerID: "o"))
        try? env.context.save()

        func sync() async {
            env.syncHealthData(force: true)
            let deadline = Date.now.addingTimeInterval(2)
            while env.isHealthSyncInFlight, Date.now < deadline { try? await Task.sleep(for: .milliseconds(5)) }
        }
        await sync()                                           // periods not answered yet
        XCTAssertEqual(rows(env).count, 1)

        source.status = .unnecessary                           // answered: empty means none
        await sync()
        XCTAssertTrue(rows(env).isEmpty)
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
