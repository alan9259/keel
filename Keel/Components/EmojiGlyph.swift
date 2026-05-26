import SwiftUI

/// A single emoji rendered the one right way: the bundled Twemoji colour font,
/// with any variation selector stripped so glyphs like ⛈️ centre correctly
/// instead of being nudged off by a trailing selector box.
///
/// Defined once and reused everywhere an emoji appears (moods, mood packs,
/// decorative icons), so the fix lives in a single place.
struct EmojiGlyph: View {
    let emoji: String
    var size: CGFloat = 24

    var body: some View {
        Text(emoji.glyphOnly).font(KeelFont.emoji(size))
    }
}
