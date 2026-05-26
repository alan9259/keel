import Foundation

/// A selectable set of mood emoji. The active pack (from settings) determines
/// what emoji represent each mood across the app.
struct MoodPack: Identifiable {
    let id: String
    let name: String
    let detail: String
    /// Store price; nil for the packs everyone starts with.
    let price: String?
    let ownedByDefault: Bool
    /// Five emoji in Mood order (worst → best): difficult, low, okay, good, great.
    let emoji: [String]

    func emoji(for mood: Mood) -> String {
        let index = Mood.allCases.firstIndex(of: mood) ?? 0
        return emoji.indices.contains(index) ? emoji[index] : mood.emoji
    }
}

enum MoodPacks {
    static let defaultID = "ocean"

    static let all: [MoodPack] = [
        MoodPack(id: "classic", name: "Classic Faces",
                 detail: "The default set: warm, expressive faces.",
                 price: nil, ownedByDefault: true,
                 emoji: ["😣", "😔", "😐", "🙂", "😊"]),
        MoodPack(id: "botanicals", name: "Botanicals",
                 detail: "Flowers and leaves, from full bloom to wilting.",
                 price: nil, ownedByDefault: true,
                 emoji: ["🥀", "🍂", "🌾", "🌿", "🌸"]),
        MoodPack(id: "ocean", name: "Ocean Tides",
                 detail: "Calm waters to rough seas. Poetic and evocative.",
                 price: nil, ownedByDefault: true,
                 emoji: ["⛈️", "🌀", "🌅", "🐚", "🌊"]),
        MoodPack(id: "weather", name: "Weather",
                 detail: "Sun to storm: a natural metaphor for how you feel.",
                 price: "£1.99", ownedByDefault: false,
                 emoji: ["⛈️", "🌧️", "⛅", "🌤️", "☀️"]),
        MoodPack(id: "moon", name: "Moon Phases",
                 detail: "From full moon to new, in sync with your cycle.",
                 price: "£2.99", ownedByDefault: false,
                 emoji: ["🌑", "🌒", "🌓", "🌔", "🌕"]),
        MoodPack(id: "animals", name: "Spirit Animals",
                 detail: "Gentle animal companions for each mood.",
                 price: "£2.99", ownedByDefault: false,
                 emoji: ["🐚", "🦔", "🐢", "🐦", "🦋"]),
    ]

    static func pack(_ id: String) -> MoodPack {
        all.first { $0.id == id } ?? all.first { $0.id == defaultID } ?? all[0]
    }
}
