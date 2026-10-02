import XCTest
import SwiftData
@testable import Keel

/// Submission pack Part A checks that are logic, not layout.
@MainActor
final class SubmissionPackATests: XCTestCase {

    // MARK: A3 Apple Health read types

    /// Only types Keel shows or uses are requested. Heart rate, respiratory rate, blood
    /// oxygen and body temperature show as Activities tiles; basal body temperature was
    /// requested but never shown, so it isn't.
    func testReadsOnlyTheTypesKeelShows() {
        XCTAssertEqual(Set(HealthKitService.readVitalTypeIDs), [
            "heartRate", "restingHeartRate", "hrv", "respiratoryRate", "oxygenSaturation",
            "bloodPressureSystolic", "bloodPressureDiastolic", "bodyMass",
            "bodyTemperature", "wristTemperature", "activeEnergy", "flights", "distance",
        ])
        XCTAssertFalse(HealthKitService.readVitalTypeIDs.contains("basalBodyTemperature"))
        XCTAssertEqual(Set(HealthKitService.readActivityIDs), ["steps", "exercise"]) // + sleep (category)
        XCTAssertTrue(Set(HealthKitService.readVitalTypeIDs).isDisjoint(with: HealthIngestor.discontinuedVitalTypeIDs))
    }

    /// Every vital the sync controls offer is one Keel still reads (no dead toggles).
    func testSyncControlsOnlyCoverReadTypes() {
        let offered = Set(HealthSyncCatalog.all.flatMap(\.vitalTypeIDs))
        XCTAssertTrue(offered.isSubset(of: Set(HealthKitService.readVitalTypeIDs)))
    }

    func testPreviouslyImportedUnusedTypesArePurged() {
        let context = TestStore.makeContext()
        let ingestor = HealthIngestor(context: context, ownerID: TestStore.ownerID,
                                      symptoms: SymptomRepository(context: context, ownerID: TestStore.ownerID))
        let day = Date.now.startOfDay
        for t in ["basalBodyTemperature", "oxygenSaturation", "restingHeartRate"] {
            context.insert(HealthSample(typeID: t, day: day, value: 1, unit: "", ownerID: "o"))
        }
        try? context.save()
        ingestor.purgeDiscontinuedHealthImports()
        let left = Set(((try? context.fetch(FetchDescriptor<HealthSample>())) ?? []).map(\.typeID))
        XCTAssertEqual(left, ["oxygenSaturation", "restingHeartRate"])   // only the unread type goes
    }

    // MARK: B1 voice entry falls back to typing, never to the network

    func testUnavailableVoiceOffersTyping() {
        XCTAssertEqual(SpeechRecognitionService.unavailableMessage(for: .unavailable),
                       "Voice notes aren't available on this phone. You can type instead.")
        XCTAssertNotNil(SpeechRecognitionService.unavailableMessage(for: .denied))
        XCTAssertNil(SpeechRecognitionService.unavailableMessage(for: .idle))
        XCTAssertNil(SpeechRecognitionService.unavailableMessage(for: .recording))
    }

    // MARK: A4 no sign-in

    func testNoSignInWithAppleEntitlement() throws {
        let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("Config/Keel.entitlements")
        let plist = try XCTUnwrap(NSDictionary(contentsOf: url))
        XCTAssertNil(plist["com.apple.developer.applesignin"])
        XCTAssertEqual(plist["com.apple.developer.healthkit"] as? Bool, true)
    }

    // MARK: A7 export compliance

    func testDeclaresNoNonExemptEncryption() {
        XCTAssertEqual(Bundle.main.object(forInfoDictionaryKey: "ITSAppUsesNonExemptEncryption") as? Bool, false)
    }
}
