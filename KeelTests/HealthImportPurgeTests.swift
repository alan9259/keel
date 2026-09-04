import XCTest
import SwiftData
@testable import Keel

/// Keel no longer imports symptoms or menstrual flow from Apple Health. The one-time
/// purge must remove exactly the discontinued imports (HealthKit symptom links, cycle
/// entries, and `symptom.*` archive samples) and leave everything she logged herself,
/// plus imported vitals, untouched.
@MainActor
final class HealthImportPurgeTests: XCTestCase {

    func testPurgeRemovesOnlyDiscontinuedHealthImports() throws {
        let context = TestStore.makeContext()
        let owner = TestStore.ownerID()
        let day = Date.now.startOfDay

        // A check-in with one manual and one HealthKit symptom link.
        let checkIn = CheckIn(date: day, mood: .okay, energy: 60, ownerID: owner)
        let hot = Symptom(name: "Hot flushes", category: .body, isCustom: false, ownerID: owner)
        let anxious = Symptom(name: "Anxious", category: .mood, isCustom: false, ownerID: owner)
        context.insert(checkIn); context.insert(hot); context.insert(anxious)
        context.insert(CheckInSymptom(checkIn: checkIn, symptom: hot, severity: 2, source: .healthKit, ownerID: owner))
        context.insert(CheckInSymptom(checkIn: checkIn, symptom: anxious, severity: 1, source: .manual, ownerID: owner))

        // A manual and a HealthKit cycle entry.
        context.insert(CycleEntry(date: day.adding(days: -1), type: .periodStart, flowLevel: .medium, source: .manual, ownerID: owner))
        context.insert(CycleEntry(date: day.adding(days: -2), type: .periodStart, flowLevel: .light, source: .healthKit, ownerID: owner))

        // An archived symptom sample (discontinued) and a vitals sample (kept).
        context.insert(HealthSample(typeID: "symptom.hot_flushes", day: day, value: 2, unit: "severity", ownerID: owner))
        context.insert(HealthSample(typeID: "restingHeartRate", day: day, value: 62, unit: "bpm", ownerID: owner))
        try context.save()

        let ingestor = HealthIngestor(
            context: context, ownerID: TestStore.ownerID,
            symptoms: SymptomRepository(context: context, ownerID: TestStore.ownerID))

        XCTAssertEqual(ingestor.purgeDiscontinuedHealthImports(), 3) // HK link + HK cycle + symptom.* sample

        // Manual data survives; HealthKit symptom/flow imports are gone; vitals kept.
        let links = try context.fetch(FetchDescriptor<CheckInSymptom>())
        XCTAssertEqual(links.count, 1)
        XCTAssertEqual(links.first?.source, .manual)

        let cycles = try context.fetch(FetchDescriptor<CycleEntry>())
        XCTAssertEqual(cycles.count, 1)
        XCTAssertEqual(cycles.first?.source, .manual)

        let samples = try context.fetch(FetchDescriptor<HealthSample>())
        XCTAssertEqual(samples.count, 1)
        XCTAssertEqual(samples.first?.typeID, "restingHeartRate")

        // Idempotent.
        XCTAssertEqual(ingestor.purgeDiscontinuedHealthImports(), 0)
    }
}
