package com.therecalibrationyears.keel.domain

/** A single support line she can reach right now (port of iOS `SupportContact`). */
data class SupportContact(val name: String, val contact: String, val note: String?)

/** The support set for one country (iOS `SupportRegion`). */
data class SupportRegion(val code: String, val name: String, val emergency: String, val contacts: List<SupportContact>)

/**
 * Verified crisis and support lines (port of iOS `CrisisResources`), kept as data so every
 * screen reads one source. Contact details change: verify each entry against the official
 * source before each release (the iOS list is the reference).
 */
object CrisisResources {
    val australia = SupportRegion(
        code = "AU", name = "Australia", emergency = "000",
        contacts = listOf(
            SupportContact("Lifeline", "13 11 14", "Crisis support · call, text or chat · 24/7"),
            SupportContact("Beyond Blue", "1300 22 4636", "Mental health support · 24/7"),
            SupportContact("13YARN", "13 92 76", "Aboriginal & Torres Strait Islander crisis support · 24/7"),
        ),
    )

    val newZealand = SupportRegion(
        code = "NZ", name = "New Zealand", emergency = "111",
        contacts = listOf(
            SupportContact("Need to talk?", "1737", "Free call or text · 24/7"),
            SupportContact("Healthline", "0800 611 116", "Health advice · 24/7"),
        ),
    )

    val all = listOf(australia, newZealand)

    /**
     * The region matching her phone. Where she physically is matters more than her formatting
     * region (an en-NZ phone in Australia should still get Australian numbers), so the time
     * zone leads and the locale's country is the fallback. Anything else shows both, so no one
     * is left without a number.
     */
    fun matching(localeCountry: String?, zoneId: String): List<SupportRegion> = when {
        zoneId.startsWith("Australia/") -> listOf(australia)
        zoneId == "Pacific/Auckland" || zoneId == "Pacific/Chatham" -> listOf(newZealand)
        localeCountry == "AU" -> listOf(australia)
        localeCountry == "NZ" -> listOf(newZealand)
        else -> all
    }

    /**
     * The footer: names the local emergency number when one region shows; stays general when
     * both do, so no wrong number is implied.
     */
    fun footer(regions: List<SupportRegion>): String {
        val base = "Keel is a companion, not a crisis service. If you or someone else is in immediate danger, "
        if (regions.size == 1) {
            return base + "call ${regions[0].emergency} or go to your nearest emergency department."
        }
        return base + "call your local emergency number or go to your nearest emergency department."
    }

    /** The digits to dial for a contact as written ("13 11 14" -> "131114"); empty if none. */
    fun dialDigits(contact: String): String = contact.filter { it.isDigit() || it == '+' }
}

/** Keel's public links (port of iOS `KeelLinks`). */
object KeelLinks {
    /** The published privacy policy ("Read the full privacy policy" on Your privacy). */
    const val PRIVACY_POLICY = "https://therecalibrationyears.com/keel-privacy" // not-copy: URL
    /** Keel's social accounts (Connect with Us): the app if it's installed, else the browser. */
    const val INSTAGRAM = "https://www.instagram.com/keelperiapp/" // not-copy: URL
    const val FACEBOOK = "https://www.facebook.com/KeelPeriApp" // not-copy: URL
}
