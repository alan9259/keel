import Foundation

/// Web links shown in the app. Support is by email (`FeedbackMail.address`).
enum KeelLinks {
    /// The published privacy policy ("Read the full privacy policy" on Your privacy).
    static let privacyPolicy = URL(string: "https://therecalibrationyears.com/keel-privacy")!
    /// Keel's social accounts (Connect with Us). Open in the app if it's installed,
    /// otherwise in Safari.
    static let instagram = URL(string: "https://www.instagram.com/keelperiapp/")!
    static let facebook = URL(string: "https://www.facebook.com/KeelPeriApp")!
}
