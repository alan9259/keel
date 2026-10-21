package com.therecalibrationyears.keel.copy

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DESIGN_PRINCIPLES.md §3 and §4 applied to every user-facing Android string: no em or en
 * dashes, nothing from the wince list, Australian/New Zealand spelling, and no Apple
 * product names (an Android user has none of them).
 */
class CopyLintTest {

    /** Shipping Android copy plus the Android side of every substitution. */
    private val copy: List<Pair<String, String>> by lazy {
        CopySources.androidShippingLiterals
            .filter { looksLikeCopy(it.text) && CopySources.NOT_COPY_MARKER !in it.sourceLine }
            .map { it.toString() to it.text } +
            CopySources.substitutions.map { "platform-substitutions.json" to it.android }
    }

    private val allowed by lazy { CopySources.listResource("lint-allowlist.txt") }

    private fun violations(rule: Regex) = copy.filter { (_, text) -> text !in allowed && rule.containsMatchIn(text) }

    private fun assertNone(what: String, rule: Regex) {
        val found = violations(rule)
        assertTrue("$what:\n" + found.joinToString("\n") { it.first }, found.isEmpty())
    }

    @Test fun noEmOrEnDashes() {
        // Checked on every literal, not only those that look like copy.
        val found = CopySources.androidShippingLiterals.filter { '—' in it.text || '–' in it.text }
        assertTrue("Use a comma, colon or full stop instead of a dash:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test fun nothingFromTheWinceList() = assertNone("Wince list (DESIGN_PRINCIPLES.md §4)", WINCE)

    @Test fun australianSpelling() = assertNone("US spelling (DESIGN_PRINCIPLES.md §3)", US_SPELLING)

    @Test fun noApplePlatformTerms() = assertNone("Apple-only term in Android copy (see port plan §3.8)", APPLE_TERMS)

    companion object {
        val WINCE = Regex(
            """\b(honestly|to be honest|midlife|hot flash(es)?|bio-optimi[sz]e|your best self|journey to wellness|track harder|perfect your)\b""" +
                """|\bit['’]s not [^.,;:]+, it['’]s\b""",
            RegexOption.IGNORE_CASE,
        )
        val US_SPELLING = Regex(
            """\b(color(s|ed|ful|ing)?|favor(ite|ites|ed)?|center(s|ed)?|estrogen|behavior|gray|mom|drugstore|""" +
                """(personal|recogn|organ|real|priorit|minim|maxim|summar|apolog|emphas|categor|custom|normal|optim|visual|author|memor|final|util|anal)(ize|izes|ized|izing|ization|yze|yzes|yzed))\b""",
            RegexOption.IGNORE_CASE,
        )
        val APPLE_TERMS = Regex("""\b(Apple|iPhone|iPad|iCloud|Face ID|Touch ID|App Store|HealthKit|Siri|iOS)\b""")
    }
}
