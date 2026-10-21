package com.therecalibrationyears.keel.copy

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The iOS app is the reference for every word Keel shows. Any user-facing string in
 * shipping Android code must exist verbatim in the iOS sources, or be a platform
 * substitution listed in `platform-substitutions.json` (owner sign-off before release),
 * or be an Android-only literal in `android-only-literals.txt`. A literal on a line marked
 * `// not-copy: <reason>` is not user-facing (a log tag, an exception message).
 */
class CopyParityTest {

    private val ios by lazy { CopySources.iosLiterals.map { it.text }.toSet() }

    @Test fun everyAndroidStringIsIosCopyOrAnApprovedSubstitution() {
        val substituted = CopySources.substitutions.map { it.android }.toSet()
        val androidOnly = CopySources.listResource("android-only-literals.txt")
        val drift = CopySources.androidShippingLiterals.filter {
            looksLikeCopy(it.text) &&
                CopySources.NOT_COPY_MARKER !in it.sourceLine &&
                it.text !in ios && it.text !in substituted && it.text !in androidOnly
        }
        assertTrue(
            "These Android strings don't match any iOS string. Copy the iOS wording exactly " +
                "(no raw strings for copy), or add a platform substitution for the owner to " +
                "approve:\n" + drift.joinToString("\n"),
            drift.isEmpty(),
        )
    }

    @Test fun substitutionsStillMatchTheirIosOriginal() {
        val stale = CopySources.substitutions.filter { it.ios !in ios }
        assertTrue(
            "The iOS wording these substitutions replace has changed; update them:\n" +
                stale.joinToString("\n") { "\"${it.ios}\" -> \"${it.android}\"" },
            stale.isEmpty(),
        )
    }

    /**
     * Not a gate until release (M8): lists the iOS copy not yet used on Android, by file,
     * in build/reports/copy/ios-not-yet-on-android.txt, so each milestone can see what is left.
     */
    @Test fun reportIosCopyNotYetOnAndroid() {
        val android = CopySources.androidShippingLiterals.map { it.text }.toSet() +
            CopySources.substitutions.map { it.ios }
        val missing = CopySources.iosLiterals
            .filter { looksLikeCopy(it.text) && it.text !in android }
            .distinctBy { it.file to it.text }
        val report = File(CopySources.repoRoot, "android/core/copy/build/reports/copy/ios-not-yet-on-android.txt")
        report.parentFile.mkdirs()
        report.writeText(
            missing.groupBy { it.file }.entries.joinToString("\n\n") { (file, lits) ->
                "$file (${lits.size})\n" + lits.joinToString("\n") { "  \"${it.text.replace("\n", "\\n")}\"" }
            },
        )
        val total = CopySources.iosLiterals.filter { looksLikeCopy(it.text) }.distinctBy { it.text }.size
        println("Copy parity: ${total - missing.distinctBy { it.text }.size} of $total iOS strings on Android; report at $report")
    }
}
