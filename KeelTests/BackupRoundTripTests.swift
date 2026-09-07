import XCTest
import SwiftData
@testable import Keel

/// End-to-end backup round-trip across the two-store setup: seed data, export to the
/// `.keelbackup` payload, then wipe-and-restore into the store and confirm her records
/// (and imported vitals from the health store) come back with relationships intact.
/// Automates what the on-sim backup probe checks by hand.
@MainActor
final class BackupRoundTripTests: XCTestCase {

    func testExportThenRestoreRoundTripsHerData() throws {
        let context = TestStore.makeContext()
        let owner = TestStore.ownerID()
        let day = Date.now.startOfDay

        let sleep = Symptom(name: "Trouble sleeping", category: .sleep, isCustom: false, ownerID: owner)
        context.insert(sleep)
        let checkIn = CheckIn(date: day, mood: .okay, energy: 60, ownerID: owner)
        context.insert(checkIn)
        context.insert(CheckInSymptom(checkIn: checkIn, symptom: sleep, severity: 2, source: .manual, ownerID: owner))
        context.insert(Medication(name: "Oestrogel", dosage: "2 pumps", timing: "Morning", kind: .treatment, ownerID: owner))
        context.insert(CycleEntry(date: day, type: .periodStart, flowLevel: .medium, source: .manual, ownerID: owner))
        context.insert(HealthSample(typeID: "restingHeartRate", day: day, value: 60, unit: "bpm", ownerID: owner))
        try context.save()

        // Export -> encoded payload -> wipe + restore into the same store.
        let data = try BackupService.exportData(context: context)
        let summary = try BackupService.restore(data: data, into: context)

        XCTAssertEqual(summary.checkIns, 1)
        XCTAssertEqual(try context.fetch(FetchDescriptor<CheckIn>()).count, 1)
        XCTAssertEqual(try context.fetch(FetchDescriptor<Medication>()).count, 1)
        XCTAssertEqual(try context.fetch(FetchDescriptor<CycleEntry>()).count, 1)
        XCTAssertEqual(try context.fetch(FetchDescriptor<HealthSample>()).count, 1) // health store round-trips

        // The check-in keeps its symptom link through the round-trip.
        let restored = try context.fetch(FetchDescriptor<CheckIn>()).first
        XCTAssertEqual(restored?.symptoms.count, 1)
        XCTAssertEqual(restored?.symptoms.first?.name, "Trouble sleeping")
    }
}
