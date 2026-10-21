package com.therecalibrationyears.keel.gpsummary

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** The metadata blanking works on bytes, so it's checked here without a device. */
class GPSummaryMetadataTests {
    private fun strip(s: String) = String(GPSummaryPdfRenderer.stripMetadata(s.toByteArray(Charsets.ISO_8859_1)), Charsets.ISO_8859_1)

    @Test fun everyInfoStringIsBlankedAndTheLengthKept() {
        val info = "<< /Producer (Skia/PDF m137) /Creator (Keel \\(Android\\)) /Title <FEFF0041> /CreationDate (D:20261021090000+11'00') >>"
        val out = strip(info)
        assertEquals(info.length, out.length) // every cross-reference offset stays valid
        fun blank(n: Int) = " ".repeat(n)
        val producer = "Skia/PDF m137".length
        val creator = "Keel \\(Android\\)".length
        val created = "D:20261021090000+11'00'".length
        assertEquals("<< /Producer (${blank(producer)}) /Creator (${blank(creator)}) /Title <${blank(8)}> /CreationDate (${blank(created)}) >>", out)
    }

    @Test fun aDictionaryAfterAKeyIsLeftAlone() {
        val dict = "/Title << /Kind /X >>"
        assertEquals(dict, strip(dict))
    }

    @Test fun fileNameIsTheDateSheMadeIt() {
        assertEquals("GP-Visit-Summary-2026-09-01.pdf", GPSummaryPdfRenderer.fileName(LocalDate.of(2026, 9, 1)))
    }
}
