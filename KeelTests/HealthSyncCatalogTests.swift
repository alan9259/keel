import XCTest
import SwiftData
@testable import Keel

/// Per-item Apple Health sync controls: the pure snapshot filter that drops
/// switched-off items before import, and an end-to-end check that a disabled item
/// isn't ingested while an enabled one is.
final class HealthSyncCatalogTests: XCTestCase {

    private let day = Date.now.startOfDay

    private func sampleSnapshot() -> HealthSnapshot {
        var s = HealthSnapshot()
        s.sleepByDay = [day: 7.5]
        s.activityAmounts = ["steps": [day: 8000], "exercise": [day: 30], "meditation": [day: 10]]
        s.vitals = [
            .init(typeID: "restingHeartRate", unit: "bpm", byDay: [day: 58]),
            .init(typeID: "hrv", unit: "ms", byDay: [day: 42]),
            .init(typeID: "activeEnergy", unit: "kcal", byDay: [day: 400]),
            .init(typeID: "bodyMass", unit: "kg", byDay: [day: 68]),
        ]
        return s
    }

    // MARK: Pure filter

    func testEmptyDisabledIsPassthrough() {
        let s = sampleSnapshot()
        let out = HealthSyncCatalog.filter(s, disabled: [])
        XCTAssertEqual(out.activityAmounts.keys.sorted(), ["exercise", "meditation", "steps"])
        XCTAssertEqual(out.vitals.count, 4)
        XCTAssertFalse(out.sleepByDay.isEmpty)
    }

    func testDisablingExerciseRemovesOnlyExercise() {
        let out = HealthSyncCatalog.filter(sampleSnapshot(), disabled: ["exercise"])
        XCTAssertNil(out.activityAmounts["exercise"])
        XCTAssertNotNil(out.activityAmounts["steps"])
        XCTAssertNotNil(out.activityAmounts["meditation"])
    }

    func testDisablingSleepClearsSleep() {
        let out = HealthSyncCatalog.filter(sampleSnapshot(), disabled: ["sleep"])
        XCTAssertTrue(out.sleepByDay.isEmpty)
        XCTAssertNotNil(out.activityAmounts["steps"]) // other activity untouched
    }

    func testDisablingHeartVitalsRemovesItsVitalsOnly() {
        let out = HealthSyncCatalog.filter(sampleSnapshot(), disabled: ["heartVitals"])
        let ids = Set(out.vitals.map(\.typeID))
        XCTAssertFalse(ids.contains("restingHeartRate"))
        XCTAssertFalse(ids.contains("hrv"))
        XCTAssertTrue(ids.contains("activeEnergy")) // a different item, still on
        XCTAssertTrue(ids.contains("bodyMass"))
    }

    func testCatalogIDsAreUnique() {
        let ids = HealthSyncCatalog.all.map(\.id)
        XCTAssertEqual(ids.count, Set(ids).count)
    }

    // MARK: End-to-end honouring through the real ingest

    @MainActor
    func testDisabledItemIsNotIngestedButEnabledIs() {
        let env = AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
        let filtered = HealthSyncCatalog.filter(sampleSnapshot(), disabled: ["exercise"])
        env.ingestHealthSnapshot(filtered)

        let activity = (try? env.context.fetch(FetchDescriptor<HealthActivitySample>())) ?? []
        let ids = Set(activity.map(\.activityID))
        XCTAssertTrue(ids.contains("steps"))
        XCTAssertFalse(ids.contains("exercise")) // switched off -> never imported
    }
}
