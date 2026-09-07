import XCTest
import SwiftData
@testable import Keel

/// End-to-end integration of the Apple Health import path through a real `AppEnvironment`
/// (in-memory two-store container), asserting the isolation guarantees the on-sim
/// `-uitHealthImport` probe checks by hand: imported activity + vitals land in the
/// local-only health store, symptoms and cycle are never imported, ingestion is
/// idempotent, and the activity merge prefers her own manual entry.
@MainActor
final class HealthKitImportIntegrationTests: XCTestCase {

    private func makeEnv() -> AppEnvironment {
        AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider())
    }

    private func count<T: PersistentModel>(_ type: T.Type, in env: AppEnvironment) -> Int {
        (try? env.context.fetchCount(FetchDescriptor<T>())) ?? -1
    }

    func testImportRoutesToHealthStoreAndNeverImportsSymptomsOrCycle() {
        let env = makeEnv()
        let today = Date.now.startOfDay

        var snap = HealthSnapshot()
        snap.sleepByDay = [today: 7.5, today.adding(days: -1): 6.0]
        snap.activityAmounts = ["steps": [today: 8000], "exercise": [today: 30]]
        snap.vitals = [
            .init(typeID: "heartRate", unit: "bpm", byDay: [today: 64]),
            .init(typeID: "restingHeartRate", unit: "bpm", byDay: [today: 58]),
        ]

        env.ingestHealthSnapshot(snap)

        // Imported activity -> health store; vitals -> HealthSample; nothing in ActivityLog.
        XCTAssertGreaterThan(count(HealthActivitySample.self, in: env), 0)
        XCTAssertGreaterThan(count(HealthSample.self, in: env), 0)
        XCTAssertEqual(count(ActivityLog.self, in: env), 0)
        // Symptoms and cycle are never imported from Apple Health.
        XCTAssertEqual(count(CheckInSymptom.self, in: env), 0)
        XCTAssertEqual(count(CycleEntry.self, in: env), 0)

        // Idempotent: re-ingesting the same snapshot adds nothing.
        let activity = count(HealthActivitySample.self, in: env)
        let samples = count(HealthSample.self, in: env)
        env.ingestHealthSnapshot(snap)
        XCTAssertEqual(count(HealthActivitySample.self, in: env), activity)
        XCTAssertEqual(count(HealthSample.self, in: env), samples)
    }

    func testMergePrefersHerManualEntryOverTheImport() {
        let env = makeEnv()
        let day = Date.now.startOfDay

        // An import for the day, then a manual entry she typed for the same day.
        env.ingestHealthSnapshot(HealthSnapshot(sleepByDay: [day: 8.0]))
        env.context.insert(ActivityLog(date: day, activityID: "sleep", amount: 6.5, source: .manual, ownerID: "o"))
        try? env.context.save()

        let manual = (try? env.context.fetch(FetchDescriptor<ActivityLog>())) ?? []
        let imported = (try? env.context.fetch(FetchDescriptor<HealthActivitySample>())) ?? []
        XCTAssertEqual(MergedActivity.amount("sleep", on: day, manual: manual, imported: imported), 6.5)
    }
}
