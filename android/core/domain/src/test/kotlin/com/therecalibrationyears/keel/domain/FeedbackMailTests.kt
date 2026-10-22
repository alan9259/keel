package com.therecalibrationyears.keel.domain

import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of KeelTests/FeedbackMailTests.swift: the draft is a pure `mailto:` URI. */
class FeedbackMailTests {
    private fun uri(kind: FeedbackMail.Kind) = FeedbackMail.uri(kind, version = "1.2 (47)", os = "Android 16 (API 36)", device = "Google Pixel 8")
    private fun decoded(kind: FeedbackMail.Kind) = URLDecoder.decode(uri(kind).replace("+", "%2B"), "UTF-8")

    @Test fun schemeAndRecipient() {
        val u = uri(FeedbackMail.Kind.FEEDBACK)
        assertEquals("mailto", URI(u).scheme)
        // The address sits before the query, unencoded, so any mail app resolves it.
        assertTrue(u, u.startsWith("mailto:keel@therecalibrationyears.com?"))
    }

    @Test fun subjectsDifferPerKind() {
        assertTrue(uri(FeedbackMail.Kind.FEEDBACK).contains("subject=Keel%20feedback"))
        assertTrue(uri(FeedbackMail.Kind.FEATURE_REQUEST).contains("subject=Keel%20feature%20request"))
        assertTrue(uri(FeedbackMail.Kind.SUPPORT).contains("subject=Keel%20support"))
    }

    /** Support (Your privacy) goes to the same inbox the privacy summary names. */
    @Test fun supportGoesToTheAddressInThePrivacySummary() {
        assertTrue(uri(FeedbackMail.Kind.SUPPORT).startsWith("mailto:keel@therecalibrationyears.com?"))
        assertTrue(decoded(FeedbackMail.Kind.SUPPORT).contains("Here's what I need help with:"))
    }

    @Test fun bodyCarriesOpenerAndRealContext() {
        val text = decoded(FeedbackMail.Kind.FEEDBACK)
        assertTrue(text, text.contains("Here's what's on my mind:"))
        assertTrue(text, text.contains("Sent from Keel 1.2 (47) · Android 16 (API 36) · Google Pixel 8"))
        assertTrue(text, text.contains("You can delete this line."))
    }

    @Test fun featureRequestOpenerDiffers() {
        assertTrue(decoded(FeedbackMail.Kind.FEATURE_REQUEST).contains("I'd love Keel to be able to do"))
    }

    /** Nothing that would break a mailto query is left raw inside the values. */
    @Test fun draftIsFullyPercentEncoded() {
        val afterQuery = uri(FeedbackMail.Kind.FEEDBACK).substringAfter("?")
        assertFalse("raw space leaked into the query", afterQuery.contains(" "))
        assertFalse("raw newline leaked into the query", afterQuery.contains("\n"))
        assertFalse("raw apostrophe leaked into the query", afterQuery.contains("'"))
        assertTrue(afterQuery.contains("%20"))
        assertTrue(afterQuery.contains("%0A"))
        // Only the one & and two = we placed ourselves are literal.
        assertEquals(1, afterQuery.count { it == '&' })
        assertEquals(2, afterQuery.count { it == '=' })
    }

    /** The copy fallback address and the drafted recipient are the same inbox. */
    @Test fun fallbackAddressMatchesRecipient() {
        assertEquals("keel@therecalibrationyears.com", FeedbackMail.ADDRESS)
        assertTrue(uri(FeedbackMail.Kind.FEEDBACK).contains(FeedbackMail.ADDRESS))
    }

    @Test fun nonAsciiIsEncodedAsUtf8() {
        assertEquals("caf%C3%A9%20%C2%B7%20a%26b%3Dc", FeedbackMail.encode("café · a&b=c"))
    }
}

/** Port of KeelTests/ShareInviteTests.swift. */
class ShareInviteTests {
    @Test fun messageIsWarmAndFollowsHouseStyle() {
        assertTrue(ShareInvite.MESSAGE.isNotEmpty())
        assertTrue(ShareInvite.MESSAGE.contains("Keel"))
        assertFalse(ShareInvite.MESSAGE.contains("—"))
        assertFalse(ShareInvite.MESSAGE.contains("–"))
    }

    /** iOS links its App Store listing; Android links Google Play (plan D24). */
    @Test fun linkIsAPlayStoreHttpsUrl() {
        val link = URI(ShareInvite.STORE_URL)
        assertEquals("https", link.scheme)
        assertEquals("play.google.com", link.host)
        assertEquals("id=com.therecalibrationyears.keel", link.query)
    }

    @Test fun theSharedTextCarriesTheNoteThenTheLink() {
        assertEquals(listOf(ShareInvite.MESSAGE, ShareInvite.STORE_URL), ShareInvite.text.split("\n"))
    }
}
