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

    // MARK: Apple Health connection is per-device, not restored from the archive

    /// Builds a backup payload from a store whose profile was connected to Apple Health.
    private func connectedBackupData() throws -> Data {
        let source = TestStore.makeContext()
        source.insert(UserProfile(firstName: "Mischa", healthKitAuthorized: true, ownerID: TestStore.ownerID()))
        try source.save()
        return try BackupService.exportData(context: source)
    }

    /// Regression: restoring a backup made while connected onto a fresh install used to
    /// switch syncing on, so the next foreground sync raised the HealthKit prompt
    /// without her tapping Connect. A device that isn't connected stays disconnected.
    func testRestoreDoesNotConnectAppleHealthOnAFreshDevice() throws {
        let fresh = TestStore.makeContext()
        try BackupService.restore(data: try connectedBackupData(), into: fresh)

        let profile = try fresh.fetch(FetchDescriptor<UserProfile>()).first
        XCTAssertEqual(profile?.firstName, "Mischa")          // her profile still restores
        XCTAssertEqual(profile?.healthKitAuthorized, false)   // but not the device's Health grant
    }

    /// Restoring on a device that is already connected keeps it connected.
    func testRestoreKeepsThisDevicesExistingConnection() throws {
        let device = TestStore.makeContext()
        device.insert(UserProfile(firstName: "Mischa", healthKitAuthorized: true, ownerID: TestStore.ownerID()))
        try device.save()

        let source = TestStore.makeContext()
        source.insert(UserProfile(firstName: "Mischa", healthKitAuthorized: false, ownerID: TestStore.ownerID()))
        try source.save()
        try BackupService.restore(data: try BackupService.exportData(context: source), into: device)

        XCTAssertEqual(try device.fetch(FetchDescriptor<UserProfile>()).first?.healthKitAuthorized, true)
    }
}
