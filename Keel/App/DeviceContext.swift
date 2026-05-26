import Foundation

/// Non-identifying environment context. We record this to tailor content (e.g.
/// AU vs NZ support lines), schedule reminders in her timezone, and help support.
///
/// It describes the app's environment, not the person: there is no advertising
/// identifier, no cross-app tracking, and no device fingerprinting used to
/// identify who she is. She can be anonymous and still get a sensible experience.
enum DeviceContext {
    static var region: String? { Locale.current.region?.identifier }
    static var localeID: String { Locale.current.identifier }
    static var timeZoneID: String { TimeZone.current.identifier }

    static var appVersion: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(short) (\(build))"
    }

    /// e.g. "iPhone17,1". The Simulator's uname reports the host arch, so prefer
    /// the modelled device identifier when running there.
    static var deviceModel: String {
        if let sim = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] {
            return sim
        }
        var system = utsname()
        uname(&system)
        return withUnsafeBytes(of: &system.machine) { raw in
            String(decoding: raw.prefix { $0 != 0 }, as: UTF8.self)
        }
    }

    static var osVersion: String {
        let v = ProcessInfo.processInfo.operatingSystemVersion
        return "iOS \(v.majorVersion).\(v.minorVersion).\(v.patchVersion)"
    }
}
