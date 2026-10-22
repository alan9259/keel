package com.therecalibrationyears.keel.domain

/** About Keel (port of the iOS `AboutView` text). The wording is the owner's (4 Oct 2026): keep it verbatim. */
object AboutCopy {
    /** The opening (no heading). The first line is the hook and gets a little more weight. */
    val opening = listOf(
        "You're not imagining it.",
        "Perimenopause and menopause affect millions of women, yet for too long the experience has been minimised, misunderstood or simply not talked about.",
        "The symptoms are real. The uncertainty can be too.",
        "Keel was built for this: a calm, grounded companion for the years when your body can stop feeling familiar.",
    )

    val sections: List<Pair<String, List<String>>> = listOf(
        "What we do" to listOf(
            "Keel helps you track mood, energy, symptoms, sleep, cycle and medications, building a picture over time instead of turning your body into a data project.",
            "When you next see your GP, Keel turns that picture into a summary you can take with you, so you walk in ready to use the time you've got.",
        ),
        "Why we do it" to listOf(
            "Because the years of perimenopause and menopause deserve more than a pamphlet and a pat on the back.",
            "You deserve to see what's changing, recognise your own patterns, and feel better equipped to ask questions and advocate for yourself.",
        ),
        "Built from lived experience" to listOf(
            "I created Keel while navigating perimenopause myself, and wishing I'd had something like this when the changes first began. Something to keep everything in one place and make conversations with healthcare professionals a little easier.",
        ),
        "What Keel isn't" to listOf(
            "Keel keeps your record and helps you describe it. It doesn't diagnose, prescribe or replace your doctor. That's deliberate.",
            "Keel helps you see what you've been experiencing and prepare for conversations with the healthcare professionals who can help you decide what happens next.",
        ),
    )

    const val CLOSING = "That's what Keel is here to be: a calm companion to help you find your even keel, whatever that means to you."

    fun versionLine(shortVersion: String) = "Keel · Version $shortVersion · © 2026 TRY Keel Pty Ltd"

    const val EMOJI_CREDIT = "Emoji artwork by Twemoji, © Twitter, Inc and other contributors, licensed under CC-BY 4.0"
}
