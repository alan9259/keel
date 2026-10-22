package com.therecalibrationyears.keel.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of KeelTests/CrisisResourcesTests.swift: where she physically is wins over her locale. */
class CrisisResourcesTests {
    private fun match(country: String?, zone: String) = CrisisResources.matching(country, zone).map { it.code }

    @Test fun australianTimeZoneShowsAustralia() {
        val r = CrisisResources.matching("AU", "Australia/Melbourne")
        assertEquals(listOf("AU"), r.map { it.code })
        assertEquals("000", r.first().emergency)
    }

    @Test fun newZealandTimeZoneShowsNewZealand() {
        val r = CrisisResources.matching("NZ", "Pacific/Auckland")
        assertEquals(listOf("NZ"), r.map { it.code })
        assertEquals("111", r.first().emergency)
    }

    @Test fun chathamTimeZoneShowsNewZealand() = assertEquals(listOf("NZ"), match("NZ", "Pacific/Chatham"))

    @Test fun timeZoneWinsOverLocale() {
        assertEquals(listOf("AU"), match("NZ", "Australia/Sydney"))
        assertEquals(listOf("NZ"), match("AU", "Pacific/Auckland"))
    }

    @Test fun localeFallbackWhenTimeZoneIsNeither() {
        assertEquals(listOf("AU"), match("AU", "Europe/London"))
        assertEquals(listOf("NZ"), match("NZ", "America/New_York"))
    }

    @Test fun unknownRegionShowsBoth() {
        assertEquals(listOf("AU", "NZ"), match("GB", "Europe/London"))
        assertEquals(listOf("AU", "NZ"), match(null, "UTC"))
    }

    @Test fun everyContactHasANoteAndNumber() {
        CrisisResources.all.forEach { region ->
            assertTrue(region.emergency.isNotEmpty())
            region.contacts.forEach { c ->
                assertTrue("${region.code} ${c.name}", c.contact.isNotEmpty())
                assertTrue("${region.code} ${c.name} should describe itself", c.note != null)
                assertTrue("${region.code} ${c.name} must be diallable", CrisisResources.dialDigits(c.contact).isNotEmpty())
            }
        }
    }

    /** The iOS footer: the local number for one region, general wording for both. */
    @Test fun footerNamesTheNumberOnlyForOneRegion() {
        assertTrue(CrisisResources.footer(listOf(CrisisResources.australia)).contains("call 000 or go to"))
        assertTrue(CrisisResources.footer(listOf(CrisisResources.newZealand)).contains("call 111 or go to"))
        assertTrue(CrisisResources.footer(CrisisResources.all).contains("call your local emergency number"))
    }

    @Test fun dialDigitsKeepOnlyWhatThePhoneDials() {
        assertEquals("131114", CrisisResources.dialDigits("13 11 14"))
        assertEquals("0800611116", CrisisResources.dialDigits("0800 611 116"))
        assertEquals("+61131114", CrisisResources.dialDigits("+61 13 11 14"))
    }

    @Test fun thePolicyLinkIsTheCompanySite() =
        assertEquals("https://therecalibrationyears.com/keel-privacy", KeelLinks.PRIVACY_POLICY)
}

/** Port of the age half of KeelTests/ProfileTests.swift, plus the Profile form's save rules. */
class ProfileFormTests {
    private val today = LocalDate.of(2026, 10, 22)

    @Test fun ageAndBirthYearAreInverse() {
        assertEquals(1977, ProfileForm.birthYear(fromAge = 49, today = today))
        assertEquals(49, ProfileForm.age(fromBirthYear = 1977, today = today))
    }

    @Test fun derivedAgeUsesStoredBirthYear() {
        val form = ProfileForm.from("Mischa", null, 1977, null, null, null, null, null, today)
        assertEquals("49", form.age)
        assertEquals("", ProfileForm.from("Mischa", null, null, null, null, null, null, null, today).age)
    }

    /** iOS shows the skipped-name placeholder as blank, so she isn't greeted by it in her own field. */
    @Test fun thePlaceholderNameLoadsBlank() {
        assertEquals("", ProfileForm.from("there", null, null, null, null, null, null, null, today).firstName)
        assertEquals("Mischa", ProfileForm.from("Mischa", null, null, null, null, null, null, null, today).firstName)
    }

    @Test fun savingTrimsAndTurnsBlanksIntoNone() {
        val saved = ProfileForm(firstName = "  Mischa ", lastName = "  ", age = " 49 ", mobile = " 0400 000 000 ", email = "")
            .saved(today)
        assertEquals("Mischa", saved.firstName)
        assertNull(saved.lastName)
        assertEquals(1977, saved.birthYear)
        assertEquals("0400 000 000", saved.mobile)
        assertNull(saved.email)
    }

    @Test fun anImplausibleAgeIsIgnored() {
        listOf("0", "130", "-3", "forty", "").forEach { assertNull(it, ProfileForm(age = it).saved(today).birthYear) }
        assertEquals(today.year - 129, ProfileForm(age = "129").saved(today).birthYear)
        assertEquals(today.year - 1, ProfileForm(age = "1").saved(today).birthYear)
    }

    @Test fun herBackgroundIsSavedWithAPlausibleYearOnly() {
        val saved = ProfileForm(hysterectomy = Hysterectomy.YES_OVARIES_KEPT, hysterectomyYear = "2019", periodsNote = "after a hysterectomy").saved(today)
        assertEquals(Hysterectomy.YES_OVARIES_KEPT, saved.hysterectomy)
        assertEquals(2019, saved.hysterectomyYear)
        assertEquals("after a hysterectomy", saved.periodsNote)
        assertNull(ProfileForm(hysterectomy = Hysterectomy.YES_OVARIES_KEPT, hysterectomyYear = "2031").saved(today).hysterectomyYear)
        assertFalse(ProfileForm().saved(today).hysterectomy != null)
    }
}

/** Port of AboutCopyTests and SocialLinkTests (KeelTests/PrivacySummaryTests.swift). */
class AboutCopyTests {
    private val allText = AboutCopy.opening + AboutCopy.sections.flatMap { listOf(it.first) + it.second } +
        listOf(AboutCopy.CLOSING, AboutCopy.versionLine("1.0.0"), AboutCopy.EMOJI_CREDIT)

    @Test fun noDashes() = allText.forEach { assertFalse(it, it.contains("—") || it.contains("–")) }

    @Test fun opensAndClosesWithTheOwnersLines() {
        assertEquals("You're not imagining it.", AboutCopy.opening.first())
        assertEquals(listOf("What we do", "Why we do it", "Built from lived experience", "What Keel isn't"), AboutCopy.sections.map { it.first })
        assertTrue(AboutCopy.CLOSING.endsWith("whatever that means to you."))
    }

    /** No claim that Keel notices patterns or makes sense of things for her (V1 keeps the record). */
    @Test fun noInterpretationClaims() = allText.forEach {
        assertFalse(it, it.contains("notices patterns"))
        assertFalse(it, it.contains("make sense of"))
    }

    @Test fun versionLineUsesTheRealVersion() =
        assertEquals("Keel · Version 1.2.0 · © 2026 TRY Keel Pty Ltd", AboutCopy.versionLine("1.2.0"))

    @Test fun socialLinks() {
        assertEquals("https://www.instagram.com/keelperiapp/", KeelLinks.INSTAGRAM)
        assertEquals("https://www.facebook.com/KeelPeriApp", KeelLinks.FACEBOOK)
    }
}
