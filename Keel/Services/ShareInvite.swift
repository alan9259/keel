import Foundation

/// The content behind "Tell your friends": a short recommendation plus the app's
/// public link, handed to the iOS share sheet. Kept pure so the copy and the link
/// are unit-testable and there is exactly one place to set the URL at launch.
enum ShareInvite {
    /// Keel's public App Store listing.
    // FAKE: App Store listing URL — remove before public launch (Keel isn't on the App Store yet; swap in the real listing id before shipping the share feature)
    static let appStoreURL = URL(string: "https://apps.apple.com/app/id000000000")!

    /// A warm, first-person recommendation. Most share destinations let her edit it
    /// before sending, so this is a friendly default, not a fixed script.
    static let message = "I've been using Keel, a gentle companion for perimenopause and menopause. Thought you might find it helpful too."

    /// Items for a `UIActivityViewController`: the note, then the link.
    static var activityItems: [Any] { [message, appStoreURL] }
}
