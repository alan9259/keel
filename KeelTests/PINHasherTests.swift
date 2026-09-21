import XCTest
@testable import Keel

/// The PIN hashing: deterministic per salt, salt-dependent, and never the plaintext.
final class PINHasherTests: XCTestCase {

    func testSamePINAndSaltHashEqual() {
        let salt = PINHasher.makeSalt()
        XCTAssertEqual(PINHasher.hash(pin: "1234", salt: salt, rounds: 1000),
                       PINHasher.hash(pin: "1234", salt: salt, rounds: 1000))
    }

    func testDifferentPINDiffers() {
        let salt = PINHasher.makeSalt()
        XCTAssertNotEqual(PINHasher.hash(pin: "1234", salt: salt, rounds: 1000),
                          PINHasher.hash(pin: "1235", salt: salt, rounds: 1000))
    }

    func testDifferentSaltDiffersForSamePIN() {
        let a = PINHasher.hash(pin: "1234", salt: PINHasher.makeSalt(), rounds: 1000)
        let b = PINHasher.hash(pin: "1234", salt: PINHasher.makeSalt(), rounds: 1000)
        XCTAssertNotEqual(a, b) // random salts -> different hashes
    }

    func testHashIsNotThePlaintext() {
        let salt = PINHasher.makeSalt()
        let hash = PINHasher.hash(pin: "1234", salt: salt, rounds: 1000)
        XCTAssertNil(String(data: hash, encoding: .utf8).flatMap { $0.contains("1234") ? "x" : nil })
    }

    func testMakeSaltIsRandom16Bytes() {
        XCTAssertEqual(PINHasher.makeSalt().count, 16)
        XCTAssertNotEqual(PINHasher.makeSalt(), PINHasher.makeSalt())
    }

    func testConstantTimeEquals() {
        let salt = PINHasher.makeSalt()
        let h = PINHasher.hash(pin: "1234", salt: salt, rounds: 1000)
        XCTAssertTrue(PINHasher.constantTimeEquals(h, h))
        XCTAssertFalse(PINHasher.constantTimeEquals(h, PINHasher.hash(pin: "9999", salt: salt, rounds: 1000)))
        XCTAssertFalse(PINHasher.constantTimeEquals(h, Data([1, 2, 3])))
    }
}
