package com.therecalibrationyears.keel.domain

import java.time.LocalDate

/**
 * The Profile form (port of iOS `ProfileView` load and save): her details and My background as
 * she types them, and the values they save as. Pure, so the parsing is tested.
 */
data class ProfileForm(
    val firstName: String = "",
    val lastName: String = "",
    val age: String = "",
    val mobile: String = "",
    val email: String = "",
    val hysterectomy: Hysterectomy? = null,
    val hysterectomyYear: String = "",
    val periodsNote: String = "",
) {
    /** What Save changes writes. */
    data class Saved(
        val firstName: String,
        val lastName: String?,
        val birthYear: Int?,
        val mobile: String?,
        val email: String?,
        val hysterectomy: Hysterectomy?,
        val hysterectomyYear: Int?,
        val periodsNote: String,
    )

    /**
     * Trimmed, blanks as none. Age is stored as a year of birth so it never drifts; an age
     * outside 1 to 129 is ignored. A hysterectomy year must be plausible (1900 to this year).
     */
    fun saved(today: LocalDate) = Saved(
        firstName = firstName.trim(),
        lastName = lastName.trim().ifEmpty { null },
        birthYear = age.trim().toIntOrNull()?.takeIf { it in 1..129 }?.let { birthYear(fromAge = it, today = today) },
        mobile = mobile.trim().ifEmpty { null },
        email = email.trim().ifEmpty { null },
        hysterectomy = hysterectomy,
        hysterectomyYear = Hysterectomy.validYear(hysterectomyYear, today),
        periodsNote = periodsNote,
    )

    companion object {
        /** The placeholder a skipped name is stored as; shown as blank in her own name field. */
        private const val PLACEHOLDER_NAME = "there" // not-copy: UserRepository.DEFAULT_FIRST_NAME

        fun birthYear(fromAge: Int, today: LocalDate): Int = today.year - fromAge
        fun age(fromBirthYear: Int, today: LocalDate): Int = today.year - fromBirthYear

        /** The form as her saved profile fills it. */
        fun from(
            firstName: String?, lastName: String?, birthYear: Int?, mobile: String?, email: String?,
            hysterectomy: Hysterectomy?, hysterectomyYear: Int?, periodsNote: String?, today: LocalDate,
        ) = ProfileForm(
            firstName = firstName?.takeIf { it != PLACEHOLDER_NAME }.orEmpty(),
            lastName = lastName.orEmpty(),
            age = birthYear?.let { age(it, today).toString() }.orEmpty(),
            mobile = mobile.orEmpty(),
            email = email.orEmpty(),
            hysterectomy = hysterectomy,
            hysterectomyYear = hysterectomyYear?.toString().orEmpty(),
            periodsNote = periodsNote.orEmpty(),
        )
    }
}
