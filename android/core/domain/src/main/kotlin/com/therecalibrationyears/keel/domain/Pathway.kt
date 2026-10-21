package com.therecalibrationyears.keel.domain

/** How she is managing things at the moment (port of iOS `Pathway`, Keel/Models/Enums.swift). */
enum class Pathway(val raw: String, val title: String, val detail: String) {
    NATURAL(
        "natural", "On my own for now",
        "Lifestyle, supplements or other approaches. No prescribed treatment at the moment.",
    ),
    MEDICAL(
        "medical", "Working with a doctor",
        "With a GP or menopause specialist, with or without prescribed treatment.",
    ),
    BOTH("both", "A bit of both", "Combining my own approaches with medical support."),
    FIGURING("figuring", "Still working it out", "Not sure yet what this is, or what to do about it.");

    companion object {
        fun fromRaw(raw: String?): Pathway? = entries.firstOrNull { it.raw == raw }
    }
}
