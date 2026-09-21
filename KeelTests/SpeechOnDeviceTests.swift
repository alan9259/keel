import XCTest
@testable import Keel

/// Dictation only runs when it can stay fully on-device, so the voice audio never
/// leaves the phone. This covers that gate.
final class SpeechOnDeviceTests: XCTestCase {

    func testDictatesOnlyWhenAvailableAndOnDeviceSupported() {
        XCTAssertTrue(SpeechRecognitionService.canDictateOnDevice(isAvailable: true, supportsOnDevice: true))
    }

    func testNoDictationWhenOnDeviceUnsupported() {
        // Would otherwise fall back to Apple's servers — we refuse instead.
        XCTAssertFalse(SpeechRecognitionService.canDictateOnDevice(isAvailable: true, supportsOnDevice: false))
    }

    func testNoDictationWhenUnavailable() {
        XCTAssertFalse(SpeechRecognitionService.canDictateOnDevice(isAvailable: false, supportsOnDevice: true))
    }
}
