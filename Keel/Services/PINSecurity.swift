import Foundation
import CryptoKit

/// Hashing for the app-lock PIN. The PIN is never stored in the clear: we keep a
/// random salt and a slow, salted hash, so the stored value can't be reversed and
/// two people with the same PIN don't share a hash. Pure, so it's unit-testable.
enum PINHasher {
    /// Iterated SHA-256 to add brute-force cost (a 4-digit PIN is a tiny keyspace, so
    /// the real defences are the Keychain and the attempt lockout, but this helps).
    static let rounds = 100_000

    static func makeSalt() -> Data {
        SymmetricKey(size: .bits128).withUnsafeBytes { Data($0) }
    }

    static func hash(pin: String, salt: Data, rounds: Int = rounds) -> Data {
        var data = salt + Data(pin.utf8)
        for _ in 0..<max(rounds, 1) { data = Data(SHA256.hash(data: data)) }
        return data
    }

    /// Constant-time compare so verifying can't leak the hash through timing.
    static func constantTimeEquals(_ a: Data, _ b: Data) -> Bool {
        guard a.count == b.count else { return false }
        var diff: UInt8 = 0
        for i in a.indices { diff |= a[i] ^ b[i] }
        return diff == 0
    }
}

/// Where the PIN's salt and hash live. Behind a protocol so `AppLockService` is
/// testable with an in-memory store instead of the real Keychain.
protocol PINSecureStore {
    var hasPIN: Bool { get }
    func save(salt: Data, hash: Data)
    func load() -> (salt: Data, hash: Data)?
    func clear()
}

/// Keychain-backed store. Device-local (the underlying `Keychain` uses
/// `ThisDeviceOnly`), so the PIN never leaves this device.
struct KeychainPINStore: PINSecureStore {
    private let saltKey = "keel.pin.salt"
    private let hashKey = "keel.pin.hash"

    var hasPIN: Bool { Keychain.string(for: hashKey) != nil }

    func save(salt: Data, hash: Data) {
        Keychain.set(salt.base64EncodedString(), for: saltKey)
        Keychain.set(hash.base64EncodedString(), for: hashKey)
    }

    func load() -> (salt: Data, hash: Data)? {
        guard let saltB64 = Keychain.string(for: saltKey),
              let hashB64 = Keychain.string(for: hashKey),
              let salt = Data(base64Encoded: saltB64),
              let hash = Data(base64Encoded: hashB64) else { return nil }
        return (salt, hash)
    }

    func clear() {
        Keychain.remove(saltKey)
        Keychain.remove(hashKey)
    }
}
