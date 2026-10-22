package com.therecalibrationyears.keel.domain

/**
 * The privacy summary shown in the app (Settings > Your privacy, and About), in the layout of
 * the iOS `PrivacySummary`: last updated, "The short version", then the at-a-glance table. The
 * wording is the owner's legal text, kept verbatim, never reworded here.
 */
data class PrivacySummary(
    val lastUpdated: String,
    val shortVersion: List<String>,
    /** "Your information", then the three columns. */
    val columns: List<String>,
    val atAGlance: List<Row>,
) {
    data class Row(val what: String, val kept: String, val access: String, val retention: String)

    /** One row read as a sentence with its headings (iOS `accessibilityText`). */
    fun spoken(row: Row): String =
        "${row.what}. ${columns[1]}: ${row.kept}. ${columns[2]}: ${row.access}. ${columns[3]}: ${row.retention}."

    companion object {
        const val TITLE = "Your privacy"

        /**
         * The Android wording. Null until the owner supplies it (plan D3): the iOS text describes
         * iPhone storage, iCloud and computer backups, Apple's AI features and Apple Health, and
         * several of those statements aren't true of Keel on Android, so it can't be reused.
         * The screen shows a stand-in until then; release is blocked on it.
         */
        val android: PrivacySummary? = null
    }
}
