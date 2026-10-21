package com.therecalibrationyears.keel.domain

/**
 * Port of iOS `Mood`: a visual scale, worst to best, left to right (the same direction as
 * energy). Not a numbered rating. Raw values are stored.
 */
enum class Mood(val raw: String, val label: String, val subtitle: String, val score: Double, val emoji: String) {
    DIFFICULT("difficult", "Difficult", "Really hard today", 0.0, "⛈️"),
    LOW("low", "Low", "Struggling a bit", 1.0, "🌀"),
    OKAY("okay", "Okay", "Getting through it", 2.0, "🌅"),
    GOOD("good", "Good", "Doing well today", 3.0, "🐚"),
    GREAT("great", "Great", "Feeling really good", 4.0, "🌊");

    companion object {
        /** iOS falls back to Okay for an unknown stored value. */
        fun fromRaw(raw: String?): Mood = entries.firstOrNull { it.raw == raw } ?: OKAY
    }
}

/** A selectable set of mood emoji (port of iOS `MoodPack`), five in Mood order. */
data class MoodPack(
    val id: String,
    val name: String,
    val detail: String,
    val price: String?,
    val ownedByDefault: Boolean,
    val emoji: List<String>,
) {
    fun emojiFor(mood: Mood): String = emoji.getOrNull(mood.ordinal) ?: mood.emoji
}

/** Port of iOS `MoodPacks`. */
object MoodPacks {
    const val DEFAULT_ID = "ocean"

    val all = listOf(
        MoodPack("classic", "Classic Faces", "The default set: warm, expressive faces.", null, true,
            listOf("😣", "😔", "😐", "🙂", "😊")),
        MoodPack("botanicals", "Botanicals", "Flowers and leaves, from full bloom to wilting.", null, true,
            listOf("🥀", "🍂", "🌾", "🌿", "🌸")),
        MoodPack("ocean", "Ocean Tides", "Calm waters to rough seas. Poetic and evocative.", null, true,
            listOf("⛈️", "🌀", "🌅", "🐚", "🌊")),
        MoodPack("weather", "Weather", "Sun to storm: a natural metaphor for how you feel.", "£1.99", false,
            listOf("⛈️", "🌧️", "⛅", "🌤️", "☀️")),
        MoodPack("moon", "Moon Phases", "From full moon to new, in sync with your cycle.", "£2.99", false,
            listOf("🌑", "🌒", "🌓", "🌔", "🌕")),
        MoodPack("animals", "Spirit Animals", "Gentle animal companions for each mood.", "£2.99", false,
            listOf("🐚", "🦔", "🐢", "🐦", "🦋")),
    )

    /** The pack with this id, else the default (iOS `pack(_:)`). */
    fun pack(id: String?): MoodPack = all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_ID }
}
