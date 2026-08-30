import Foundation

/// Builds the `mailto:` URL behind "Share feedback" / "Request a feature".
///
/// Kept pure and `nonisolated` so the decision (recipient, subject, body) is unit
/// tested, and so the UI just hands the result to SwiftUI's `openURL`. `openURL`
/// routes a `mailto:` to her DEFAULT mail app, whichever she uses (Apple Mail,
/// Gmail, Outlook, Proton, …), rather than forcing Apple Mail the way an in-app
/// `MFMailComposeViewController` would. If she has no mail app set up, `openURL`
/// reports it did not open and the caller offers the address to copy instead.
enum FeedbackMail {
    /// The one inbox we read. Also shown, and offered as a copy fallback, so she is
    /// never stuck if no mail client is configured.
    static let address = "keel@therecalibrationyears.com"

    enum Kind {
        case feedback
        case featureRequest

        var subject: String {
            switch self {
            case .feedback: "Keel feedback"
            case .featureRequest: "Keel feature request"
            }
        }

        /// A gentle opener so the drafted email isn't a blank page. She writes
        /// under it; the diagnostics footer sits below, clearly deletable.
        var opener: String {
            switch self {
            case .feedback: "Here's what's on my mind:"
            case .featureRequest: "Here's something I'd love Keel to be able to do:"
            }
        }
    }

    /// A ready-to-send draft: recipient, subject, opener, and a short context
    /// footer. Version/OS/device are passed in (real values from `DeviceContext`)
    /// so this stays pure and testable; they help us reproduce what she sees, and
    /// the draft tells her she can delete them.
    static func url(kind: Kind, version: String, os: String, device: String) -> URL? {
        let body = """
        \(kind.opener)


        Sent from Keel \(version) · \(os) · \(device). You can delete this line.
        """
        guard let subject = encode(kind.subject), let encodedBody = encode(body) else { return nil }
        return URL(string: "mailto:\(address)?subject=\(subject)&body=\(encodedBody)")
    }

    /// Percent-encode a mailto query value. Only RFC 3986 unreserved characters are
    /// left as-is, so spaces, newlines, colons, apostrophes and the `&`/`=` that
    /// delimit query parts are all encoded and can't corrupt the draft.
    private static func encode(_ value: String) -> String? {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return value.addingPercentEncoding(withAllowedCharacters: allowed)
    }
}
