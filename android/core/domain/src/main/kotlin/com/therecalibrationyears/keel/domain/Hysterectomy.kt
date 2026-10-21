package com.therecalibrationyears.keel.domain

import java.time.LocalDate

/**
 * Her answer to "Have you had a hysterectomy?" (port of iOS `Hysterectomy`,
 * Keel/Models/Hysterectomy.swift; submission pack 3C). Recorded for her own background and,
 * only if she turns it on, her GP summary. Keel records the answer and changes nothing
 * because of it: no feature, insight or screen depends on it. Raw values match iOS.
 */
enum class Hysterectomy(val raw: String, val label: String, private val summary: String?) {
    NO("no", "No", "no"),
    YES_OVARIES_KEPT("yesOvariesKept", "Yes, both ovaries were kept", "yes, both ovaries kept"),
    YES_ONE_OVARY_REMOVED("yesOneOvaryRemoved", "Yes, one ovary was removed", "yes, one ovary removed"),
    YES_BOTH_OVARIES_REMOVED("yesBothOvariesRemoved", "Yes, both ovaries were removed", "yes, both ovaries removed"),
    YES_NOT_SURE_ABOUT_OVARIES("yesNotSureAboutOvaries", "Yes, I'm not sure about my ovaries", "yes, not sure about ovaries"),
    RATHER_NOT_SAY("ratherNotSay", "I'd rather not say", null),
    ;

    /** A year only makes sense for a "yes". */
    val allowsYear: Boolean get() = this != NO && this != RATHER_NOT_SAY

    /** The GP summary wording, matching her answer. Null for "I'd rather not say", which never appears. */
    val summaryText: String? get() = summary

    companion object {
        const val QUESTION = "Have you had a hysterectomy?"

        fun fromRaw(raw: String?): Hysterectomy? = entries.firstOrNull { it.raw == raw }

        /** "yes, one ovary removed, 2019 (as recorded by her)". */
        fun summaryLine(answer: Hysterectomy?, year: Int?): String? {
            val text = answer?.summaryText ?: return null
            val withYear = (if (answer.allowsYear) year else null)?.let { "$text, $it" } ?: text
            return "$withYear (as recorded by her)"
        }

        /** A plausible year she might enter, or null (blank, partial or nonsense). */
        fun validYear(text: String, today: LocalDate): Int? {
            val year = text.trim().toIntOrNull() ?: return null
            return if (year in 1900..today.year) year else null
        }
    }
}
