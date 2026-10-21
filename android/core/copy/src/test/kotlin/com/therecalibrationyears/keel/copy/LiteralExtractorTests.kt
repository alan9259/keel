package com.therecalibrationyears.keel.copy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The copy tests are only as good as these extractors, so pin their edge cases. */
class LiteralExtractorTests {

    private fun swift(src: String) = SwiftLiterals.extract(src).map { it.text }
    private fun kotlin(src: String) = KotlinLiterals.extract(src).map { it.text }

    @Test fun swiftEscapesAndInterpolation() {
        assertEquals(
            listOf("day", "days", "Logged {} {}.\n’"),
            swift("""Text("Logged \(n) \(n == 1 ? "day" : "days").\n\u{2019}")"""),
        )
    }

    @Test fun swiftCommentsAreSkippedButUrlsInStringsAreNot() {
        assertEquals(
            listOf("https://therecalibrationyears.com/keel-privacy"),
            swift(
                """
                // Text("not this")
                /* Text("nor /* nested */ this") */
                let url = "https://therecalibrationyears.com/keel-privacy"
                """.trimIndent(),
            ),
        )
    }

    @Test fun swiftMultilineDedentsAndJoinsContinuations() {
        val src = "    let s = \"\"\"\n        Line one\n        joined \\\n        here\n        \"\"\"\n"
        assertEquals(listOf("Line one\njoined here"), swift(src))
    }

    @Test fun swiftDebugBranchesAndPreviewsAreSkipped() {
        val src = """
            #if DEBUG
            let a = "debug only"
            #else
            let b = "release"
            #endif
            #if !DEBUG
            let c = "also release"
            #else
            let d = "also debug"
            #endif
            #Preview("Sample") { Text("Sample text") }
            let e = "after preview"
        """.trimIndent()
        assertEquals(listOf("release", "also release", "after preview"), swift(src))
    }

    @Test fun swiftRawStringKeepsBackslashes() {
        assertEquals(listOf("""\s*\(e\.g\."""), swift("""x.range(of: #"\s*\(e\.g\."#)"""))
    }

    @Test fun swiftLineNumbersSurviveStripping() {
        val lits = SwiftLiterals.extract("#if DEBUG\nlet a = \"x\"\n#endif\nlet b = \"Here\"")
        assertEquals(4, lits.single().line)
    }

    @Test fun kotlinTemplatesEscapesAndNestedStrings() {
        assertEquals(
            listOf("day", "days", "Logged {} {}. \$2.99 ’"),
            kotlin("""Text("Logged ${'$'}n ${'$'}{if (n == 1) "day" else "days"}. \${'$'}2.99 ’")"""),
        )
    }

    @Test fun kotlinCharLiteralsAndCommentsAreNotStrings() {
        assertEquals(
            listOf("real"),
            kotlin(
                """
                val q = '"'
                val e = '\''
                // "comment"
                /* "block /* nested */ comment" */
                val s = "real"
                """.trimIndent(),
            ),
        )
    }

    @Test fun kotlinRawStringsAreFlagged() {
        val lit = KotlinLiterals.extract("val q = \"\"\"SELECT * FROM checkIn\"\"\"").single()
        assertTrue(lit.raw)
        assertEquals("SELECT * FROM checkIn", lit.text)
    }

    @Test fun copyHeuristic() {
        assertTrue(looksLikeCopy("Back"))
        assertTrue(looksLikeCopy("Find your even keel."))
        assertTrue(looksLikeCopy("{} days logged"))
        assertFalse(looksLikeCopy("earthy"))
        assertFalse(looksLikeCopy("keel.colourMode"))
        assertFalse(looksLikeCopy("UNUSED_PARAMETER"))
        assertFalse(looksLikeCopy(" {}"))
        assertFalse(looksLikeCopy("£1.99"))
        assertFalse(looksLikeCopy("M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8z"))
        assertFalse(looksLikeCopy("SELECT * FROM medication WHERE deletedAt IS NULL"))
    }
}
