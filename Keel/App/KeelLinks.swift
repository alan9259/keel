import Foundation

/// Web links shown in Settings. Both are still to come (Mischa is sending the URLs), so
/// they're nil rather than a guessed address: "Read the full privacy policy" opens the
/// in-app policy until then, and Support appears once its URL is set.
enum KeelLinks {
    /// The published production privacy policy.
    static let privacyPolicy: URL? = nil
    /// The support page.
    static let support: URL? = nil
}
