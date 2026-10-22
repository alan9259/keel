package com.therecalibrationyears.keel.domain

/**
 * The `mailto:` draft behind "Share feedback", "Request a feature" and Support (port of iOS
 * `FeedbackMail`). Pure, so the recipient, subject and body are tested; the screen hands the
 * result to her own mail app. If she has none, the screen offers the address to copy instead.
 */
object FeedbackMail {
    /** The one inbox we read. Also shown, and offered to copy, so she is never stuck. */
    const val ADDRESS = "keel@therecalibrationyears.com"

    enum class Kind(val subject: String, val opener: String) {
        FEEDBACK("Keel feedback", "Here's what's on my mind:"),
        FEATURE_REQUEST("Keel feature request", "Here's something I'd love Keel to be able to do:"),
        /** "Support" on the Your privacy screen. */
        SUPPORT("Keel support", "Here's what I need help with:"),
    }

    /**
     * A draft's body: a gentle opener so it isn't a blank page, room for her words, and a
     * short context footer (real values passed in) that tells her she can delete it.
     */
    fun body(kind: Kind, version: String, os: String, device: String): String =
        "${kind.opener}\n\n\nSent from Keel $version · $os · $device. You can delete this line."

    /** The ready-to-send draft: recipient, then the encoded subject and body. */
    fun uri(kind: Kind, version: String, os: String, device: String): String =
        "mailto:$ADDRESS?subject=${encode(kind.subject)}&body=${encode(body(kind, version, os, device))}" // not-copy: mailto URI

    /**
     * Percent-encode a mailto query value. Only RFC 3986 unreserved characters stay as they
     * are, so spaces, newlines, apostrophes and the `&`/`=` between query parts are all
     * encoded and can't corrupt the draft. (iOS also leaves non-ASCII letters; encoding them
     * too is the stricter form of the same rule.)
     */
    internal fun encode(value: String): String = buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val c = (byte.toInt() and 0xFF).toChar()
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-._~") append(c)
            else append('%').append("%02X".format(byte.toInt() and 0xFF))
        }
    }
}

/**
 * "Tell your friends" (port of iOS `ShareInvite`): a short recommendation plus Keel's public
 * link, handed to the share sheet. One place to set the link at launch.
 */
object ShareInvite {
    // FAKE: Google Play listing URL — remove before public launch (plan D24: Keel isn't on Play yet; the link works only once the listing for this applicationId is live)
    const val STORE_URL = "https://play.google.com/store/apps/details?id=com.therecalibrationyears.keel" // not-copy: store link

    /** A warm, first-person note. Most share targets let her edit it before sending. */
    const val MESSAGE = "I've been using Keel, a gentle companion for perimenopause and menopause. Thought you might find it helpful too."

    /** What is shared: the note, then the link (iOS shares them as two items). */
    val text: String get() = MESSAGE + "\n" + STORE_URL
}
