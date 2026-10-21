package com.therecalibrationyears.keel.gpsummary

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.therecalibrationyears.keel.domain.GPBleeding
import com.therecalibrationyears.keel.domain.GPCycleBlock
import com.therecalibrationyears.keel.domain.GPMedRow
import com.therecalibrationyears.keel.domain.GPMedTable
import com.therecalibrationyears.keel.domain.GPSummaryCopy
import com.therecalibrationyears.keel.domain.GPSummaryDocument
import com.therecalibrationyears.keel.domain.GPSymptomStat
import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Port of KeelTests/GPSummaryPDFRendererTests.swift (and the file-name test from
 * GPSummaryServiceTests.swift): the PDF's structural contract, which needs a device because
 * Android's PDF writer and text layout are native. Exactly two A4 pages for a normal load,
 * spill rather than clip, page 1 always fits, and no identifying metadata.
 */
@RunWith(AndroidJUnit4::class)
class GPSummaryPdfRendererTests {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fonts = GPSummaryFonts.load(context)

    private fun minimalDocument() = GPSummaryDocument(
        name = null, age = null,
        periodLabel = "7 June to 29 August 2026",
        checkInsLabel = "0 of 84 days",
        priorities = emptyList(),
        symptomStats = emptyList(),
        checkInDaysThisPeriod = 0,
        checkInDaysPreviousPeriod = 0,
        previousWindowDayCount = 84,
        defaultSymptomMaxRows = 6,
        impactLine = null,
        cycle = GPCycleBlock(null, 0, GPSummaryCopy.notEnoughPeriodsForRange, null, GPBleeding.NOT_RECORDED.label, null),
        mht = GPMedTable(emptyList(), null),
        otherMeds = GPMedTable(emptyList(), null),
        supplements = GPMedTable(emptyList(), null),
        treatmentChanges = emptyList(),
        sleepLine = "0 of 0", energyLine = null, moodLine = null,
        questions = emptyList(),
        generatedOn = LocalDate.of(2023, 11, 14),
    )

    private fun medTable(n: Int) = GPMedTable((1..n).map { GPMedRow("Item $it", "${it}mg", "Every day") }, null)

    private fun symptoms(n: Int, top: Int) = (1..n).map { GPSymptomStat("Symptom $it", false, top - it, 0, 2.0, Instant.EPOCH) }

    /** Page count and each page's size in points, read back with Android's own PDF reader. */
    private fun pages(bytes: ByteArray): List<Pair<Int, Int>> {
        val file = File.createTempFile("gp-test", ".pdf", context.cacheDir).apply { writeBytes(bytes) }
        try {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { r -> (0 until r.pageCount).map { i -> r.openPage(i).use { it.width to it.height } } }
            }
        } finally {
            file.delete()
        }
    }

    @Test fun rendersTwoA4PagesWithNoIdentifyingMetadata() {
        val bytes = GPSummaryPdfRenderer(minimalDocument(), fonts).render()
        assertEquals("the summary is a hard two pages", listOf(595 to 842, 595 to 842), pages(bytes))

        // No producer, creator, author, title or timestamp survives (blanks are spaces only).
        val text = String(bytes, Charsets.ISO_8859_1)
        for (key in listOf("/Producer", "/Creator", "/Author", "/Title", "/Subject", "/CreationDate", "/ModDate")) {
            Regex("${Regex.escape(key)}\\s*\\(([^)]*)\\)").findAll(text).forEach {
                assertTrue("$key must carry no identifier, got '${it.groupValues[1]}'", it.groupValues[1].isBlank())
            }
            Regex("${Regex.escape(key)}\\s*<([^>]*)>").findAll(text).forEach {
                assertTrue("$key must carry no identifier, got '${it.groupValues[1]}'", it.groupValues[1].isBlank())
            }
        }
        assertFalse("Android's PDF library name must not survive", "Skia" in text)
    }

    /** Acceptance criterion: 8 symptoms, 4 medications, 6 supplements, 12 weeks → 2 pages. */
    @Test fun acceptanceLoadStaysTwoPages() {
        val doc = minimalDocument().copy(
            symptomStats = symptoms(8, 30),
            checkInDaysThisPeriod = 60,
            mht = medTable(2),
            otherMeds = medTable(2),
            supplements = medTable(6),
            treatmentChanges = listOf("14 July 2026: changed oestradiol gel dose", "2 August 2026: stopped magnesium"),
            questions = listOf("Should I adjust my dose?", "Is a blood test worth doing?"),
        )
        val renderer = GPSummaryPdfRenderer(doc, fonts)
        val bytes = renderer.render()
        assertFalse(renderer.pageOneOverflowed)
        assertFalse("the acceptance load must fit two pages", renderer.pageTwoOverflowed)
        assertEquals(2, pages(bytes).size)
    }

    /** A user near every cap spills onto a further page rather than losing data. */
    @Test fun heavyPageTwoSpillsInsteadOfClipping() {
        val doc = minimalDocument().copy(
            mht = medTable(10),
            otherMeds = medTable(10),
            supplements = medTable(10),
            treatmentChanges = (1..8).map { "Change number $it on a date" },
            questions = listOf("One", "Two", "Three"),
        )
        val renderer = GPSummaryPdfRenderer(doc, fonts)
        val bytes = renderer.render()
        assertTrue("over-cap content must be detected as a spill", renderer.pageTwoOverflowed)
        assertTrue("data spills to page 3, never clipped", pages(bytes).size >= 3)
    }

    @Test fun rendersARichDocumentWithoutOverflowingPageOne() {
        val doc = minimalDocument().copy(
            priorities = listOf("One", "Two", "Three"), // triggers the 5-row cap
            defaultSymptomMaxRows = 5,
            symptomStats = symptoms(8, 20),
            checkInDaysThisPeriod = 42,
            impactLine = "Affecting: sleep. Overall impact: Significant.",
        )
        val renderer = GPSummaryPdfRenderer(doc, fonts)
        renderer.render()
        assertFalse("page 1 must fit even with priorities, impact and a full table", renderer.pageOneOverflowed)
    }

    /**
     * Regression (Android 9): the PDF canvas reports a broken clip there (empty off the main
     * thread, 116 points tall on it), and `Layout.draw` skips every line outside it, so the
     * summary came out with its rules and grid but no text. The footer sits at the bottom of
     * the page, so its words must be there, drawn off the main thread as the app does.
     */
    @Test fun textIsDrawnAllTheWayDownThePageOffTheMainThread() {
        var bytes = ByteArray(0)
        val worker = Thread { bytes = GPSummaryPdfRenderer(minimalDocument(), fonts).render() }
        worker.start()
        worker.join()
        val file = File.createTempFile("gp-test", ".pdf", context.cacheDir).apply { writeBytes(bytes) }
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { r ->
                    r.openPage(0).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        // The disclaimer's two lines, below the footer rule (y 786) and above the page edge.
                        val inked = (792 until 840).sumOf { y -> (46 until 549).count { x -> bitmap.getPixel(x, y) != android.graphics.Color.WHITE } }
                        assertTrue("the footer text must be drawn, found $inked inked pixels", inked > 500)
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    /** Port of GPSummaryServiceTests.testPDFFileNameIsDateBasedNotHex. */
    @Test fun fileNameIsTheDateSheMadeIt() {
        assertEquals("GP-Visit-Summary-2025-09-01.pdf", GPSummaryPdfRenderer.fileName(LocalDate.of(2025, 9, 1)))
    }
}
