import Foundation
import Security

/// Minimal Keychain string store.
///
/// Used for the account identifier so it survives an app reinstall: Keychain
/// items outlive the app's container, so an anonymous user who deletes and
/// reinstalls keeps the same id and her synced data comes back. The item is
/// device-local (`ThisDeviceOnly`), so it is not carried to other devices, and it
/// is a random id, not personal data.
enum Keychain {
    private static let service = "com.therecalibrationyears.keel"

    static func string(for key: String) -> String? {
        var query = baseQuery(key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    @discardableResult
    static func set(_ value: String, for key: String) -> Bool {
        let attributes: [String: Any] = [
            kSecValueData as String: Data(value.utf8),
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        let status = SecItemUpdate(baseQuery(key) as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var insert = baseQuery(key)
            insert.merge(attributes) { _, new in new }
            return SecItemAdd(insert as CFDictionary, nil) == errSecSuccess
        }
        return status == errSecSuccess
    }

    static func remove(_ key: String) {
        SecItemDelete(baseQuery(key) as CFDictionary)
    }

    #if DEBUG
    /// Returns the raw OSStatus of a write/read round-trip, to distinguish a code
    /// bug from the unsigned-Simulator "missing entitlement" case (-34018).
    static func diagnose() -> String {
        let key = "keel.diag"
        remove(key)
        let addStatus = SecItemAdd((baseQuery(key).merging([
            kSecValueData as String: Data("x".utf8),
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]) { _, new in new }) as CFDictionary, nil)
        let readBack = string(for: key)
        remove(key)
        return "addStatus=\(addStatus) readBack=\(readBack ?? "nil")"
    }
    #endif

    private static func baseQuery(_ key: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
    }
}
