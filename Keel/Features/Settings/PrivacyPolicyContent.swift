import Foundation

/// The privacy policy body. The prose lives in `Resources/privacy-policy.md`, bundled
/// with the app, so it can be maintained as a plain document rather than a Swift string
/// (and reused elsewhere). This type just loads and exposes it; `PrivacyPolicyView`
/// renders it. Keep the file verbatim: it is legal text, not to be fabricated or reworded.
enum PrivacyPolicyContent {
    static var lines: [String] { markdown.components(separatedBy: "\n") }

    /// Loaded once from the bundled markdown. The file is a required resource, so a
    /// miss is a packaging bug we want to catch in development rather than ship blank.
    static let markdown: String = {
        guard let url = Bundle.main.url(forResource: "privacy-policy", withExtension: "md"),
              let text = try? String(contentsOf: url, encoding: .utf8) else {
            assertionFailure("privacy-policy.md is missing from the app bundle")
            return ""
        }
        return text.trimmingCharacters(in: .newlines)
    }()
}
