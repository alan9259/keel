package com.therecalibrationyears.keel.gpsummary

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.TypefaceSpan
import androidx.compose.ui.graphics.toArgb
import com.therecalibrationyears.keel.designsystem.KeelTheme
import com.therecalibrationyears.keel.designsystem.R
import com.therecalibrationyears.keel.domain.GPMedTable
import com.therecalibrationyears.keel.domain.GPSummaryCopy
import com.therecalibrationyears.keel.domain.GPSummaryDocument
import com.therecalibrationyears.keel.domain.GPSymptomTable
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/** The faces the PDF uses: Literata for headings, Poppins for everything else. */
class GPSummaryFonts(val serif: Typeface, val sans: Typeface, val sansMedium: Typeface) {
    companion object {
        fun load(context: Context) = GPSummaryFonts(
            serif = context.resources.getFont(R.font.literata),
            sans = context.resources.getFont(R.font.poppins_regular),
            sansMedium = context.resources.getFont(R.font.poppins_medium),
        )
    }
}

/**
 * Draws a `GPSummaryDocument` as an A4 PDF (port of iOS `GPSummaryPDFRenderer`). This is the
 * single source of visual truth: the preview shows the very PDF this produces, so the two
 * can't drift. Literata for headings, Poppins for body, rosewood for rules and headings only
 * (no red). Footer on every page. Identifying metadata is blanked before return.
 *
 * Page 1 is a hard single page: if it would overflow, the symptom table drops from its start
 * cap to 4 rows (the dropped rows fall into "Also recorded", never lost). Priorities, the
 * impact line and the cycle block are never shortened or moved. Units are PDF points.
 */
class GPSummaryPdfRenderer(private val doc: GPSummaryDocument, private val fonts: GPSummaryFonts) {
    // A4 at 72 dpi, portrait.
    private val pageWidth = 595f
    private val pageHeight = 842f
    private val margin = 46f
    private val footerHeight = 56f
    private val contentLeft = margin
    private val contentWidth = pageWidth - margin * 2
    private val contentTop = margin
    private val contentBottom = pageHeight - footerHeight

    // From the light theme on purpose: a printed document stays light (and on the brand
    // palette) whatever colour mode or theme she uses in the app.
    private val rosewood = KeelTheme.Light.accent.toArgb()
    private val charcoal = KeelTheme.Light.text.toArgb()
    private val muted = KeelTheme.Light.muted.toArgb()
    private val hairline = KeelTheme.Light.text.copy(alpha = 0.18f).toArgb()

    /** True if page 1 couldn't fit even at 4 symptom rows (a spec defect). */
    var pageOneOverflowed = false
        private set

    /** True if the page-2 content spilled onto a further page (a spec defect, but data is kept, never clipped). */
    var pageTwoOverflowed = false
        private set

    /** The page being drawn, or null while measuring. */
    private var canvas: Canvas? = null

    /** A self-contained page-2 section: its height, the gap above it, and how to draw it at a given y. */
    private class Block(val height: Float, val spacingBefore: Float, val draw: (Float) -> Unit)

    // Type

    private enum class Face { SERIF, SANS, SANS_MEDIUM }

    // The canvas unit is a PDF point, so hinted text would snap each glyph's advance to a
    // whole point and squash the spacing at these sizes; linear, subpixel text keeps the
    // font's own spacing, as on paper.
    private fun paint(face: Face, size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = when (face) {
            Face.SERIF -> fonts.serif
            Face.SANS -> fonts.sans
            Face.SANS_MEDIUM -> fonts.sansMedium
        }
        // Literata is a variable font; iOS draws its Regular instance.
        if (face == Face.SERIF) fontVariationSettings = "'wght' 400" // not-copy: font axis
        // No ligatures: Android's PDF writer gives a ligature glyph no text, so "affecting"
        // would copy, search and read aloud as "a ecting" on the GP's computer.
        fontFeatureSettings = "'liga' 0, 'clig' 0" // not-copy: font features
        textSize = size
        this.color = color
    }

    private fun title() = paint(Face.SERIF, 19f, rosewood)
    private fun heading() = paint(Face.SERIF, 12.5f, rosewood)
    private fun body(color: Int = charcoal) = paint(Face.SANS, 10f, color)
    private fun table() = paint(Face.SANS, 9.5f, charcoal)
    private fun tableHeader() = paint(Face.SANS_MEDIUM, 9.5f, charcoal)
    private fun caption() = paint(Face.SANS, 8.5f, muted)
    private fun footerPaint() = paint(Face.SANS, 7.5f, muted)

    // Public

    /** The finished PDF bytes, identifying metadata blanked. */
    fun render(): ByteArray {
        // Choose the largest symptom-row cap (start .. 4) that lets page 1 fit.
        var rowCap = doc.defaultSymptomMaxRows
        while (rowCap > 4 && layoutPage1(rowCap, draw = false) > contentBottom) rowCap -= 1
        pageOneOverflowed = layoutPage1(rowCap, draw = false) > contentBottom

        // Page 2 onward flows: blocks that don't fit one page spill onto a further page
        // rather than being clipped (spec: never drop data silently).
        val blocks = pageTwoBlocks()
        val extraPages = pageCount(blocks)
        val totalPages = 1 + extraPages
        pageTwoOverflowed = extraPages > 1

        val pdf = PdfDocument()
        try {
            var number = 1
            fun begin(): PdfDocument.Page =
                pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), number).create()).also { canvas = it.canvas }

            var page = begin()
            layoutPage1(rowCap, draw = true)
            drawFooter(1, totalPages)
            pdf.finishPage(page)

            // Page 2+.
            number = 2
            page = begin()
            var y = contentTop
            for (block in blocks) {
                val required = (if (y > contentTop) block.spacingBefore else 0f) + block.height
                if (y + required > contentBottom && y > contentTop) {
                    drawFooter(number, totalPages)
                    pdf.finishPage(page)
                    number += 1
                    page = begin()
                    y = contentTop
                }
                if (y > contentTop) y += block.spacingBefore
                block.draw(y)
                y += block.height
            }
            drawFooter(number, totalPages)
            pdf.finishPage(page)

            val out = ByteArrayOutputStream()
            pdf.writeTo(out)
            return stripMetadata(out.toByteArray())
        } finally {
            canvas = null
            pdf.close()
        }
    }

    /** How many pages the page-2 blocks need, using the same fit rule as drawing. */
    private fun pageCount(blocks: List<Block>): Int {
        var pages = 1
        var y = contentTop
        for (block in blocks) {
            val required = (if (y > contentTop) block.spacingBefore else 0f) + block.height
            if (y + required > contentBottom && y > contentTop) {
                pages += 1
                y = contentTop
            }
            if (y > contentTop) y += block.spacingBefore
            y += block.height
        }
        return pages
    }

    // Page 1

    private fun layoutPage1(symptomRows: Int, draw: Boolean): Float {
        var y = contentTop

        // Title + provenance line.
        y += paragraph(GPSummaryCopy.featureName, title(), y = y, draw = draw)
        y += 1
        y += paragraph(GPSummaryCopy.titleSubtitle, caption(), y = y, draw = draw)
        y += 4
        y += rule(y, draw = draw)
        y += 12

        // About me.
        y += heading(GPSummaryCopy.aboutHeading, y, draw)
        doc.name?.let { y += labelValue(GPSummaryCopy.aboutName, it, y, draw) }
        doc.age?.let { y += labelValue(GPSummaryCopy.aboutAge, "$it", y, draw) }
        doc.hysterectomyLine?.let { y += labelValue(GPSummaryCopy.aboutHysterectomy, it, y, draw) }
        y += labelValue(GPSummaryCopy.aboutPeriod, doc.periodLabel, y, draw)
        y += labelValue(GPSummaryCopy.aboutCheckIns, doc.checkInsLabel, y, draw)
        y += 14

        // What I most want help with today (left out entirely when blank).
        if (doc.priorities.isNotEmpty()) {
            y += heading(GPSummaryCopy.prioritiesHeading, y, draw)
            for (line in doc.priorities) y += bullet(line, y, draw)
            y += 14
        }

        // Symptoms I logged most often.
        y += heading(GPSummaryCopy.symptomsHeading, y, draw)
        val table = doc.symptomTable(symptomRows)
        if (table.isEmpty) {
            y += paragraph(GPSummaryCopy.noSymptoms, body(muted), y = y, draw = draw)
        } else {
            y += drawSymptomTable(table, y, draw)
            table.previousUnavailableNote?.let {
                y += 3
                y += paragraph(it, caption(), y = y, draw = draw)
            }
            table.alsoRecorded?.let {
                y += 3
                y += paragraph(it, caption(), y = y, draw = draw)
            }
        }
        y += 14

        // How this is affecting me (only when she selected something).
        doc.impactLine?.let { impact ->
            y += heading(GPSummaryCopy.impactHeading, y, draw)
            y += paragraph(impact, body(), y = y, draw = draw)
            y += 2
            y += paragraph(GPSummaryCopy.impactOwnAssessment, caption(), y = y, draw = draw)
            y += 14
        }

        // Periods and cycle (must always fit on page 1); left out if she removed it.
        if (doc.includeCycle) {
            y += heading(GPSummaryCopy.cycleHeading, y, draw)
            val lastStart = doc.cycle.lastPeriodStart?.let(::dateStyle) ?: GPSummaryCopy.notRecorded
            y += labelValue(GPSummaryCopy.cycleLastStart, lastStart, y, draw)
            y += labelValue(GPSummaryCopy.cyclePeriodsRecorded, "${doc.cycle.periodsRecorded}", y, draw)
            y += labelValue(GPSummaryCopy.cycleLengths, doc.cycle.cycleLengthRange, y, draw)
            y += labelValue(GPSummaryCopy.cycleFlow, doc.cycle.flow ?: GPSummaryCopy.notRecorded, y, draw)
            y += labelValue(GPSummaryCopy.cycleBleeding, doc.cycle.intermenstrualBleeding, y, draw)
            doc.cycle.notApplicable?.let { y += labelValue(GPSummaryCopy.cycleNotApplicable, it, y, draw) }
        }
        return y
    }

    // Page 2 (paginated)

    /**
     * The page-2 sections as blocks, each measured once, in order. A single block is always
     * shorter than a page (tables cap at 10 rows), so pagination never splits one.
     */
    private fun pageTwoBlocks(): List<Block> {
        val blocks = mutableListOf<Block>()
        fun add(spacingBefore: Float, render: (Float, Boolean) -> Float) {
            blocks += Block(render(0f, false), spacingBefore) { y -> render(y, true) }
        }
        add(0f) { y, d ->
            medSection(GPSummaryCopy.mhtHeading, doc.mht, listOf(GPSummaryCopy.treatmentColumn, GPSummaryCopy.mhtDoseColumn, GPSummaryCopy.mhtChangedColumn), y, d)
        }
        add(12f) { y, d ->
            medSection(GPSummaryCopy.otherMedsHeading, doc.otherMeds, listOf(GPSummaryCopy.nameColumn, GPSummaryCopy.doseColumn, GPSummaryCopy.frequencyColumn), y, d)
        }
        add(12f) { y, d ->
            medSection(GPSummaryCopy.supplementsHeading, doc.supplements, listOf(GPSummaryCopy.nameColumn, GPSummaryCopy.doseIfKnownColumn, GPSummaryCopy.frequencyColumn), y, d)
        }
        if (doc.treatmentChanges.isNotEmpty()) add(12f) { y, d -> bulletBlock(GPSummaryCopy.treatmentChangesHeading, doc.treatmentChanges, y, d) }
        if (doc.includeSleep || doc.includeEnergy || doc.includeMood || showsAlcohol) add(12f) { y, d -> sleepEnergyMoodBlock(y, d) }
        if (doc.questions.isNotEmpty()) add(12f) { y, d -> bulletBlock(GPSummaryCopy.questionsHeading, doc.questions, y, d) }
        return blocks
    }

    private fun bulletBlock(title: String, lines: List<String>, y: Float, draw: Boolean): Float {
        var used = heading(title, y, draw)
        for (line in lines) used += bullet(line, y + used, draw)
        return used
    }

    private fun sleepEnergyMoodBlock(y: Float, draw: Boolean): Float {
        var used = heading(GPSummaryCopy.sleepEnergyMoodHeading, y, draw)
        if (doc.includeSleep) used += labelValue(GPSummaryCopy.sleepRowLabel, "${doc.sleepLine} ${GPSummaryCopy.sleepRowSuffix}", y + used, draw)
        if (doc.includeEnergy) used += labelValue(GPSummaryCopy.energyRowLabel, doc.energyLine ?: GPSummaryCopy.notRecorded, y + used, draw)
        if (doc.includeMood) used += labelValue(GPSummaryCopy.moodRowLabel, doc.moodLine ?: GPSummaryCopy.notRecorded, y + used, draw)
        val alcohol = doc.alcoholLine
        if (showsAlcohol && alcohol != null) used += labelValue(GPSummaryCopy.alcoholRowLabel, alcohol, y + used, draw)
        return used
    }

    /** Alcohol prints only when she recorded some and kept the row in. */
    private val showsAlcohol: Boolean get() = doc.includeAlcohol && doc.alcoholLine != null

    private fun medSection(title: String, table: GPMedTable, columns: List<String>, y: Float, draw: Boolean): Float {
        var used = heading(title, y, draw)
        if (table.isEmpty) {
            used += paragraph(GPSummaryCopy.noneRecorded, body(muted), y = y + used, draw = draw)
        } else {
            used += drawTable(columns, table.rows.map { listOf(it.col1, it.col2, it.col3) }, listOf(0.4f, 0.32f, 0.28f), y + used, draw)
            table.overflowNote?.let {
                used += 3
                used += paragraph(it, caption(), y = y + used, draw = draw)
            }
        }
        return used
    }

    // Blocks

    /**
     * Lay out [text] at [width]. Lines are spaced as on iOS: each face's natural line height
     * (Android leaves out the font's line gap, so it's added back) plus [lineSpacing].
     */
    private fun layout(text: CharSequence, paint: TextPaint, width: Float, lineSpacing: Float = 2f): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, maxOf(width.toInt(), 1))
            .setLineSpacing(paint.fontMetrics.leading + lineSpacing, 1f)
            .setIncludePad(false)
            .build()

    /**
     * A laid-out block's height as iOS measures it: every line at the face's full line height
     * (its line gap included, even on the last line, which Android's layout leaves out) and
     * [lineSpacing] between lines.
     */
    private fun height(l: StaticLayout, paint: TextPaint, lineSpacing: Float = 2f): Float {
        val m = paint.fontMetrics
        return l.lineCount * (m.descent - m.ascent + m.leading) + (l.lineCount - 1) * lineSpacing
    }

    /**
     * Draw a laid-out block line by line with `drawText`. Not `Layout.draw`: that skips lines
     * outside the canvas's clip bounds, and Android 9's PDF canvas reports a broken clip (empty
     * off the main thread), which left the PDF with rules but no text. Each run of a line is
     * drawn in its own face (the bold label of a "Label: value" row).
     */
    private fun Canvas.drawLayout(l: StaticLayout, x: Float, y: Float) {
        val text = l.text
        val spanned = text as? Spanned
        for (line in 0 until l.lineCount) {
            val start = l.getLineStart(line)
            val end = l.getLineVisibleEnd(line)
            val baseline = y + l.getLineBaseline(line)
            val runs = mutableListOf<IntRange>()
            var at = start
            while (at < end) {
                val next = spanned?.nextSpanTransition(at, end, TypefaceSpan::class.java) ?: end
                runs += at until next
                at = next
            }
            for (run in runs) {
                val face = spanned?.getSpans(run.first, run.last + 1, TypefaceSpan::class.java)?.lastOrNull()?.typeface
                val paint = if (face == null) l.paint else TextPaint(l.paint).apply { typeface = face }
                // A one-run line starts at the line's own left edge (right-to-left text included).
                val runX = if (runs.size == 1) l.getLineLeft(line) else l.getPrimaryHorizontal(run.first)
                drawText(text, run.first, run.last + 1, x + runX, baseline, paint)
            }
        }
    }

    /** Draw (or measure) a wrapped paragraph; returns its height. */
    private fun paragraph(
        text: CharSequence, paint: TextPaint, x: Float = contentLeft, width: Float = contentWidth, y: Float, draw: Boolean, lineSpacing: Float = 2f,
    ): Float {
        val l = layout(text, paint, width, lineSpacing)
        if (draw) canvas?.drawLayout(l, x, y)
        return ceil(height(l, paint, lineSpacing))
    }

    private fun heading(text: String, y: Float, draw: Boolean): Float {
        var used = paragraph(text, heading(), y = y, draw = draw)
        used += 3
        used += rule(y + used, draw = draw)
        used += 6
        return used
    }

    private fun labelValue(label: String, value: String, y: Float, draw: Boolean): Float {
        val s = SpannableStringBuilder("$label:  ").apply {
            setSpan(TypefaceSpan(fonts.sansMedium), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(value)
        }
        return paragraph(s, body(), y = y, draw = draw) + 2
    }

    private fun bullet(text: String, y: Float, draw: Boolean): Float {
        val indent = 12f
        if (draw) paragraph("•", body(rosewood), x = contentLeft, width = indent, y = y, draw = true)
        return paragraph(text, body(), x = contentLeft + indent, width = contentWidth - indent, y = y, draw = draw) + 3
    }

    private fun rule(y: Float, color: Int = rosewood, draw: Boolean): Float {
        if (draw) canvas?.drawLine(contentLeft, y, contentLeft + contentWidth, y, stroke(color, 0.8f))
        return 0.8f
    }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        this.color = color
    }

    // Tables

    private fun drawSymptomTable(table: GPSymptomTable, y: Float, draw: Boolean): Float =
        if (table.showsPreviousColumn) {
            drawTable(
                listOf(GPSummaryCopy.symptomColumn, GPSummaryCopy.thisPeriodColumn, GPSummaryCopy.previousPeriodColumn),
                table.rows.map { listOf(truncatedName(it.name), it.thisPeriod, it.previousPeriod.orEmpty()) },
                listOf(0.44f, 0.28f, 0.28f), y, draw,
            )
        } else {
            drawTable(
                listOf(GPSummaryCopy.symptomColumn, GPSummaryCopy.thisPeriodColumn),
                table.rows.map { listOf(truncatedName(it.name), it.thisPeriod) },
                listOf(0.6f, 0.4f), y, draw,
            )
        }

    /** At most 40 characters (whole characters, so an emoji is never cut in half). */
    private fun truncatedName(name: String): String =
        if (name.codePointCount(0, name.length) > 40) name.substring(0, name.offsetByCodePoints(0, 40)) else name

    /** A bordered table. [weights] sum to about 1 across the content width. Returns its height. */
    private fun drawTable(header: List<String>, rows: List<List<String>>, weights: List<Float>, y: Float, draw: Boolean): Float {
        val hPad = 6f
        val vPad = 4f
        val widths = weights.map { it * contentWidth }
        val allRows = listOf(header) + rows
        val paints = listOf(tableHeader()) + List(rows.size) { table() }

        // Each row is as tall as its tallest cell.
        val layouts = allRows.mapIndexed { i, row -> row.mapIndexed { c, cell -> layout(cell, paints[i], widths[c] - hPad * 2) } }
        val rowHeights = layouts.mapIndexed { i, row -> ceil(row.maxOfOrNull { height(it, paints[i]) } ?: 0f) + vPad * 2 }
        val total = rowHeights.sum()

        val c = canvas
        if (draw && c != null) {
            // Cell text.
            var ry = y
            layouts.forEachIndexed { i, row ->
                var cx = contentLeft
                row.forEachIndexed { col, l ->
                    c.drawLayout(l, cx + hPad, ry + vPad)
                    cx += widths[col]
                }
                ry += rowHeights[i]
            }
            // Grid.
            val grid = stroke(hairline, 0.6f)
            c.drawRect(contentLeft, y, contentLeft + contentWidth, y + total, grid)
            var lineY = y
            for (h in rowHeights.dropLast(1)) {
                lineY += h
                c.drawLine(contentLeft, lineY, contentLeft + contentWidth, lineY, grid)
            }
            var lineX = contentLeft
            for (w in widths.dropLast(1)) {
                lineX += w
                c.drawLine(lineX, y, lineX, y + total, grid)
            }
        }
        return total
    }

    // Footer

    private fun drawFooter(page: Int, pages: Int) {
        val top = pageHeight - footerHeight
        rule(top, color = hairline, draw = true)
        paragraph(GPSummaryCopy.footer, footerPaint(), y = top + 6, draw = true, lineSpacing = 1.5f)

        val baselineY = pageHeight - 16
        paragraph("${GPSummaryCopy.generatedPrefix} ${dateStyle(doc.generatedOn)}", footerPaint(), y = baselineY, draw = true)
        val pageText = "Page $page of $pages"
        val pagePaint = footerPaint()
        val pageWidth = ceil(pagePaint.measureText(pageText))
        paragraph(pageText, pagePaint, x = contentLeft + contentWidth - pageWidth, width = pageWidth + 1, y = baselineY, draw = true)
    }

    // Formatting and metadata

    private fun dateStyle(day: LocalDate): String = DAY_MONTH_YEAR.format(day)

    companion object {
        /** Fixed English (AU), so month names are spelled out and the PDF reads the same on any phone. */
        private val DAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("en-AU")) // not-copy: date pattern

        /** "GP-Visit-Summary-2026-09-01.pdf": the day she prepared it, not a random suffix. */
        fun fileName(day: LocalDate): String = "GP-Visit-Summary-${DateTimeFormatter.ISO_LOCAL_DATE.format(day)}.pdf" // not-copy: file name

        /** The Info-dictionary keys whose values are blanked: nothing identifies the phone, app or author. */
        private val METADATA_KEYS = listOf("/Producer", "/Creator", "/Author", "/Title", "/Subject", "/Keywords", "/CreationDate", "/ModDate") // not-copy: PDF keys

        /**
         * Blank every Info-dictionary string so no producer (Android stamps its PDF library and
         * version), creator, author, title or timestamp survives. Works on the bytes: each
         * value's interior is overwritten with spaces of equal length, which keeps every
         * cross-reference offset valid. The dictionary is plain text; nothing is re-encoded.
         */
        internal fun stripMetadata(data: ByteArray): ByteArray {
            val bytes = data.copyOf()
            for (key in METADATA_KEYS) {
                val needle = key.toByteArray(Charsets.US_ASCII)
                var from = 0
                while (true) {
                    val at = indexOf(bytes, needle, from)
                    if (at < 0) break
                    from = blankStringValue(bytes, at + needle.size)
                }
            }
            return bytes
        }

        /** Overwrite the string after a key (a `(literal)` or a `<hex>` string) with spaces; returns where to search on. */
        private fun blankStringValue(bytes: ByteArray, start: Int): Int {
            var i = start
            while (i < bytes.size && bytes[i].toInt().toChar() in " \n\r\t") i++
            if (i >= bytes.size) return i
            when (bytes[i].toInt().toChar()) {
                '(' -> {
                    i++
                    var depth = 1
                    while (i < bytes.size && depth > 0) {
                        when (bytes[i].toInt().toChar()) {
                            '\\' -> { // an escape: blank both bytes
                                bytes[i] = SPACE
                                if (i + 1 < bytes.size) bytes[i + 1] = SPACE
                                i += 2
                                continue
                            }
                            '(' -> depth++
                            ')' -> { depth--; if (depth == 0) break }
                        }
                        bytes[i] = SPACE
                        i++
                    }
                }
                '<' -> if (i + 1 < bytes.size && bytes[i + 1].toInt().toChar() != '<') { // a hex string, not a dictionary
                    i++
                    while (i < bytes.size && bytes[i].toInt().toChar() != '>') {
                        bytes[i] = SPACE // whitespace inside a hex string is ignored, so it reads as empty
                        i++
                    }
                }
            }
            return i
        }

        private const val SPACE: Byte = 0x20

        private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int): Int {
            outer@ for (i in from..haystack.size - needle.size) {
                for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
                return i
            }
            return -1
        }
    }
}
