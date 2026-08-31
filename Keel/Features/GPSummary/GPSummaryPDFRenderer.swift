import UIKit
import PDFKit

/// Draws a `GPSummaryDocument` as a two-page A4 PDF. This is the single source of
/// visual truth: the preview displays the very PDF this produces, so the two cannot
/// drift. Literata for headings, Poppins for body, rosewood for rules and headings
/// only (no red). Footer on every page. PDF metadata is stripped before return.
///
/// Page 1 is a hard single page: if it would overflow, the symptom table drops from
/// its start cap to 4 rows (the dropped rows fall into "Also recorded", never lost).
/// Priorities, the impact line and the cycle block are never shortened or moved.
@MainActor
final class GPSummaryPDFRenderer {
    private let doc: GPSummaryDocument

    // A4 at 72 dpi, portrait.
    private let pageSize = CGSize(width: 595, height: 842)
    private let margin: CGFloat = 46
    private let footerHeight: CGFloat = 56
    private var contentLeft: CGFloat { margin }
    private var contentWidth: CGFloat { pageSize.width - margin * 2 }
    private var contentTop: CGFloat { margin }
    private var contentBottom: CGFloat { pageSize.height - footerHeight }

    // Brand palette.
    private let rosewood = UIColor(red: 0x8C / 255, green: 0x4A / 255, blue: 0x45 / 255, alpha: 1)
    private let charcoal = UIColor(red: 0x44 / 255, green: 0x44 / 255, blue: 0x44 / 255, alpha: 1)
    private let muted = UIColor(red: 0x82 / 255, green: 0x7A / 255, blue: 0x70 / 255, alpha: 1)
    private var hairline: UIColor { charcoal.withAlphaComponent(0.18) }

    /// True if page 1 could not be made to fit even at 4 symptom rows (a spec defect).
    private(set) var pageOneOverflowed = false
    /// True if the page-2 content had to spill onto a further page (a spec defect, but
    /// data is kept rather than clipped).
    private(set) var pageTwoOverflowed = false

    /// A self-contained page-2 section: its height, the gap above it, and how to draw
    /// it at a given y. Used to paginate so nothing is silently clipped.
    private struct Block {
        let height: CGFloat
        let spacingBefore: CGFloat
        let draw: (CGFloat) -> Void
    }

    init(document: GPSummaryDocument) { self.doc = document }

    // MARK: Fonts

    private func serif(_ size: CGFloat) -> UIFont {
        UIFont(name: "Literata-Regular", size: size) ?? .systemFont(ofSize: size, weight: .semibold)
    }
    private func sans(_ size: CGFloat, _ weight: UIFont.Weight = .regular) -> UIFont {
        let name: String
        switch weight {
        case .medium, .semibold, .bold: name = "Poppins-Medium"
        case .light, .thin, .ultraLight: name = "Poppins-Light"
        default: name = "Poppins-Regular"
        }
        return UIFont(name: name, size: size) ?? .systemFont(ofSize: size, weight: weight)
    }

    private var titleFont: UIFont { serif(19) }
    private var headingFont: UIFont { serif(12.5) }
    private var bodyFont: UIFont { sans(10) }
    private var bodyMedium: UIFont { sans(10, .medium) }
    private var tableFont: UIFont { sans(9.5) }
    private var tableHeaderFont: UIFont { sans(9.5, .medium) }
    private var captionFont: UIFont { sans(8.5) }
    private var footerFont: UIFont { sans(7.5) }

    // MARK: Public

    /// The finished PDF bytes, metadata stripped.
    func render() -> Data {
        // Choose the largest symptom-row cap (start .. 4) that lets page 1 fit.
        var rowCap = doc.defaultSymptomMaxRows
        while rowCap > 4 && layoutPage1(symptomRows: rowCap, draw: false) > contentBottom {
            rowCap -= 1
        }
        pageOneOverflowed = layoutPage1(symptomRows: rowCap, draw: false) > contentBottom

        // Page 2 onward flows: if the blocks don't fit one page they spill onto a
        // further page rather than being clipped (spec: never drop data silently).
        let blocks = pageTwoBlocks()
        let extraPages = pageCount(for: blocks)
        let totalPages = 1 + extraPages
        pageTwoOverflowed = extraPages > 1
        #if DEBUG
        if pageOneOverflowed { print("GPSummary DEFECT: page 1 overflowed even at 4 symptom rows.") }
        if pageTwoOverflowed { print("GPSummary DEFECT: page 2 content spilled onto page \(totalPages).") }
        #endif

        let format = UIGraphicsPDFRendererFormat()
        format.documentInfo = [:]   // add no author/creator/title of our own
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(origin: .zero, size: pageSize), format: format)
        let raw = renderer.pdfData { ctx in
            ctx.beginPage()
            _ = layoutPage1(symptomRows: rowCap, draw: true)
            drawFooter(page: 1, of: totalPages)

            // Page 2+.
            var page = 2
            var y = contentTop
            ctx.beginPage()
            for block in blocks {
                let needsSpacing = y > contentTop
                let required = (needsSpacing ? block.spacingBefore : 0) + block.height
                if y + required > contentBottom && y > contentTop {
                    drawFooter(page: page, of: totalPages)
                    ctx.beginPage(); page += 1; y = contentTop
                }
                if y > contentTop { y += block.spacingBefore }
                block.draw(y)
                y += block.height
            }
            drawFooter(page: page, of: totalPages)
        }
        return stripMetadata(raw)
    }

    /// How many pages the page-2 blocks need, using the same fit rule as drawing.
    private func pageCount(for blocks: [Block]) -> Int {
        var pages = 1
        var y = contentTop
        for block in blocks {
            let required = (y > contentTop ? block.spacingBefore : 0) + block.height
            if y + required > contentBottom && y > contentTop {
                pages += 1; y = contentTop
            }
            if y > contentTop { y += block.spacingBefore }
            y += block.height
        }
        return pages
    }

    /// Write the PDF to a temp file for the share sheet. Caller deletes it afterwards.
    /// The name carries the date she prepared it (what the recipient sees), not a
    /// random hex suffix.
    func writeTemporaryFile() -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(Self.fileName(for: doc.generatedOn))
        try? render().write(to: url, options: .atomic)
        return url
    }

    /// "GP-Visit-Summary-2026-09-01.pdf". Pure so the naming is unit-testable.
    nonisolated static func fileName(for date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_AU")
        f.dateFormat = "yyyy-MM-dd"
        return "GP-Visit-Summary-\(f.string(from: date)).pdf"
    }

    // MARK: Page 1

    private func layoutPage1(symptomRows: Int, draw: Bool) -> CGFloat {
        var y = contentTop

        // Title + provenance line.
        y += paragraph(attr(GPSummaryCopy.featureName, titleFont, rosewood), y: y, draw: draw)
        y += 1
        y += paragraph(attr(GPSummaryCopy.titleSubtitle, captionFont, muted), y: y, draw: draw)
        y += 4
        y += rule(y: y, draw: draw)
        y += 12

        // About me.
        y += heading(GPSummaryCopy.aboutHeading, y: y, draw: draw)
        if let name = doc.name { y += labelValue(GPSummaryCopy.aboutName, name, y: y, draw: draw) }
        if let age = doc.age { y += labelValue(GPSummaryCopy.aboutAge, "\(age)", y: y, draw: draw) }
        y += labelValue(GPSummaryCopy.aboutPeriod, doc.periodLabel, y: y, draw: draw)
        y += labelValue(GPSummaryCopy.aboutCheckIns, doc.checkInsLabel, y: y, draw: draw)
        y += 14

        // What I most want help with today (omitted entirely when blank).
        if !doc.priorities.isEmpty {
            y += heading(GPSummaryCopy.prioritiesHeading, y: y, draw: draw)
            for line in doc.priorities { y += bullet(line, y: y, draw: draw) }
            y += 14
        }

        // Symptoms I logged most often.
        y += heading(GPSummaryCopy.symptomsHeading, y: y, draw: draw)
        let table = doc.symptomTable(maxRows: symptomRows)
        if table.isEmpty {
            y += paragraph(attr(GPSummaryCopy.noSymptoms, bodyFont, muted), y: y, draw: draw)
        } else {
            y += drawSymptomTable(table, y: y, draw: draw)
            if let note = table.previousUnavailableNote {
                y += 3
                y += paragraph(attr(note, captionFont, muted), y: y, draw: draw)
            }
            if let also = table.alsoRecorded {
                y += 3
                y += paragraph(attr(also, captionFont, muted), y: y, draw: draw)
            }
        }
        y += 14

        // How this is affecting me (only when she selected something).
        if let impact = doc.impactLine {
            y += heading(GPSummaryCopy.impactHeading, y: y, draw: draw)
            y += paragraph(attr(impact, bodyFont, charcoal), y: y, draw: draw)
            y += 2
            y += paragraph(attr(GPSummaryCopy.impactOwnAssessment, captionFont, muted), y: y, draw: draw)
            y += 14
        }

        // Periods and cycle (must always fit on page 1). Omitted if she removed it.
        if doc.includeCycle {
            y += heading(GPSummaryCopy.cycleHeading, y: y, draw: draw)
            let lastStart = doc.cycle.lastPeriodStart.map(dateStyle) ?? GPSummaryCopy.notRecorded
            y += labelValue(GPSummaryCopy.cycleLastStart, lastStart, y: y, draw: draw)
            y += labelValue(GPSummaryCopy.cyclePeriodsRecorded, "\(doc.cycle.periodsRecorded)", y: y, draw: draw)
            y += labelValue(GPSummaryCopy.cycleLengths, doc.cycle.cycleLengthRange, y: y, draw: draw)
            y += labelValue(GPSummaryCopy.cycleFlow, doc.cycle.flow ?? GPSummaryCopy.notRecorded, y: y, draw: draw)
            y += labelValue(GPSummaryCopy.cycleBleeding, doc.cycle.intermenstrualBleeding, y: y, draw: draw)
            if let na = doc.cycle.notApplicable {
                y += labelValue(GPSummaryCopy.cycleNotApplicable, na, y: y, draw: draw)
            }
        }
        return y
    }

    // MARK: Page 2 (paginated)

    /// The page-2 sections as blocks, each measured once, in order. A single block is
    /// always shorter than a page (tables cap at 10 rows), so block-level pagination
    /// never needs to split one.
    private func pageTwoBlocks() -> [Block] {
        var blocks: [Block] = []
        func add(_ spacingBefore: CGFloat, _ render: @escaping (CGFloat, Bool) -> CGFloat) {
            blocks.append(Block(height: render(0, false), spacingBefore: spacingBefore,
                                draw: { y in _ = render(y, true) }))
        }
        add(0) { self.medSection(GPSummaryCopy.mhtHeading, table: self.doc.mht,
                                 columns: [GPSummaryCopy.treatmentColumn, GPSummaryCopy.mhtDoseColumn, GPSummaryCopy.mhtChangedColumn],
                                 y: $0, draw: $1) }
        add(12) { self.medSection(GPSummaryCopy.otherMedsHeading, table: self.doc.otherMeds,
                                  columns: [GPSummaryCopy.nameColumn, GPSummaryCopy.doseColumn, GPSummaryCopy.frequencyColumn],
                                  y: $0, draw: $1) }
        add(12) { self.medSection(GPSummaryCopy.supplementsHeading, table: self.doc.supplements,
                                  columns: [GPSummaryCopy.nameColumn, GPSummaryCopy.doseIfKnownColumn, GPSummaryCopy.frequencyColumn],
                                  y: $0, draw: $1) }
        if !doc.treatmentChanges.isEmpty {
            add(12) { self.bulletBlock(GPSummaryCopy.treatmentChangesHeading, lines: self.doc.treatmentChanges, y: $0, draw: $1) }
        }
        if doc.includeSleep || doc.includeEnergy || doc.includeMood {
            add(12) { self.sleepEnergyMoodBlock(y: $0, draw: $1) }
        }
        if !doc.questions.isEmpty {
            add(12) { self.bulletBlock(GPSummaryCopy.questionsHeading, lines: self.doc.questions, y: $0, draw: $1) }
        }
        return blocks
    }

    private func bulletBlock(_ title: String, lines: [String], y: CGFloat, draw: Bool) -> CGFloat {
        var used = heading(title, y: y, draw: draw)
        for line in lines { used += bullet(line, y: y + used, draw: draw) }
        return used
    }

    private func sleepEnergyMoodBlock(y: CGFloat, draw: Bool) -> CGFloat {
        var used = heading(GPSummaryCopy.sleepEnergyMoodHeading, y: y, draw: draw)
        if doc.includeSleep {
            used += labelValue(GPSummaryCopy.sleepRowLabel, "\(doc.sleepLine) \(GPSummaryCopy.sleepRowSuffix)", y: y + used, draw: draw)
        }
        if doc.includeEnergy {
            used += labelValue(GPSummaryCopy.energyRowLabel, doc.energyLine ?? GPSummaryCopy.notRecorded, y: y + used, draw: draw)
        }
        if doc.includeMood {
            used += labelValue(GPSummaryCopy.moodRowLabel, doc.moodLine ?? GPSummaryCopy.notRecorded, y: y + used, draw: draw)
        }
        return used
    }

    private func medSection(_ title: String, table: GPMedTable, columns: [String], y: CGFloat, draw: Bool) -> CGFloat {
        var used = heading(title, y: y, draw: draw)
        if table.isEmpty {
            used += paragraph(attr(GPSummaryCopy.noneRecorded, bodyFont, muted), y: y + used, draw: draw)
        } else {
            used += drawTable(header: columns, rows: table.rows.map { [$0.col1, $0.col2, $0.col3] },
                              weights: [0.4, 0.32, 0.28], y: y + used, draw: draw)
            if let note = table.overflowNote {
                used += 3
                used += paragraph(attr(note, captionFont, muted), y: y + used, draw: draw)
            }
        }
        return used
    }

    // MARK: Blocks

    private func attr(_ s: String, _ font: UIFont, _ color: UIColor, lineSpacing: CGFloat = 2) -> NSAttributedString {
        let p = NSMutableParagraphStyle()
        p.lineSpacing = lineSpacing
        return NSAttributedString(string: s, attributes: [.font: font, .foregroundColor: color, .paragraphStyle: p])
    }

    /// Draw (or measure) a wrapped paragraph at full content width; returns its height.
    private func paragraph(_ s: NSAttributedString, x: CGFloat? = nil, width: CGFloat? = nil, y: CGFloat, draw: Bool) -> CGFloat {
        let w = width ?? contentWidth
        let originX = x ?? contentLeft
        let h = ceil(s.boundingRect(with: CGSize(width: w, height: .greatestFiniteMagnitude),
                                    options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil).height)
        if draw {
            s.draw(with: CGRect(x: originX, y: y, width: w, height: h),
                   options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
        }
        return h
    }

    private func heading(_ text: String, y: CGFloat, draw: Bool) -> CGFloat {
        var used = paragraph(attr(text, headingFont, rosewood), y: y, draw: draw)
        used += 3
        used += rule(y: y + used, draw: draw)
        used += 6
        return used
    }

    private func labelValue(_ label: String, _ value: String, y: CGFloat, draw: Bool) -> CGFloat {
        let s = NSMutableAttributedString(attributedString: attr("\(label):  ", bodyMedium, charcoal))
        s.append(attr(value, bodyFont, charcoal))
        return paragraph(s, y: y, draw: draw) + 2
    }

    private func bullet(_ text: String, y: CGFloat, draw: Bool) -> CGFloat {
        let indent: CGFloat = 12
        if draw {
            _ = paragraph(attr("\u{2022}", bodyFont, rosewood), x: contentLeft, width: indent, y: y, draw: true)
        }
        return paragraph(attr(text, bodyFont, charcoal), x: contentLeft + indent, width: contentWidth - indent, y: y, draw: draw) + 3
    }

    private func rule(y: CGFloat, color: UIColor? = nil, draw: Bool) -> CGFloat {
        if draw, let cg = UIGraphicsGetCurrentContext() {
            cg.setStrokeColor((color ?? rosewood).cgColor)
            cg.setLineWidth(0.8)
            cg.move(to: CGPoint(x: contentLeft, y: y))
            cg.addLine(to: CGPoint(x: contentLeft + contentWidth, y: y))
            cg.strokePath()
        }
        return 0.8
    }

    // MARK: Tables

    private func drawSymptomTable(_ table: GPSymptomTable, y: CGFloat, draw: Bool) -> CGFloat {
        if table.showsPreviousColumn {
            let header = [GPSummaryCopy.symptomColumn, GPSummaryCopy.thisPeriodColumn, GPSummaryCopy.previousPeriodColumn]
            let rows = table.rows.map { [truncatedName($0.name), $0.thisPeriod, $0.previousPeriod ?? ""] }
            return drawTable(header: header, rows: rows, weights: [0.44, 0.28, 0.28], y: y, draw: draw)
        } else {
            let header = [GPSummaryCopy.symptomColumn, GPSummaryCopy.thisPeriodColumn]
            let rows = table.rows.map { [truncatedName($0.name), $0.thisPeriod] }
            return drawTable(header: header, rows: rows, weights: [0.6, 0.4], y: y, draw: draw)
        }
    }

    private func truncatedName(_ name: String) -> String {
        name.count > 40 ? String(name.prefix(40)) : name
    }

    /// A bordered table. `weights` sum to ~1 across `contentWidth`. Returns its height.
    private func drawTable(header: [String], rows: [[String]], weights: [CGFloat], y: CGFloat, draw: Bool) -> CGFloat {
        let hPad: CGFloat = 6, vPad: CGFloat = 4
        let widths = weights.map { $0 * contentWidth }
        let allRows = [header] + rows
        let fonts = [tableHeaderFont] + Array(repeating: tableFont, count: rows.count)

        // Row heights from the tallest cell in each row.
        var rowHeights: [CGFloat] = []
        for (i, row) in allRows.enumerated() {
            var maxH: CGFloat = 0
            for (c, cell) in row.enumerated() {
                let w = widths[c] - hPad * 2
                let h = ceil(NSAttributedString(string: cell, attributes: [.font: fonts[i]])
                    .boundingRect(with: CGSize(width: w, height: .greatestFiniteMagnitude),
                                  options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil).height)
                maxH = max(maxH, h)
            }
            rowHeights.append(maxH + vPad * 2)
        }
        let total = rowHeights.reduce(0, +)

        if draw, let cg = UIGraphicsGetCurrentContext() {
            // Cell text.
            var ry = y
            for (i, row) in allRows.enumerated() {
                var cx = contentLeft
                for (c, cell) in row.enumerated() {
                    let color = i == 0 ? charcoal : charcoal
                    let s = attr(cell, fonts[i], color)
                    s.draw(with: CGRect(x: cx + hPad, y: ry + vPad, width: widths[c] - hPad * 2, height: rowHeights[i] - vPad * 2),
                           options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
                    cx += widths[c]
                }
                ry += rowHeights[i]
            }
            // Grid.
            cg.setStrokeColor(hairline.cgColor)
            cg.setLineWidth(0.6)
            cg.stroke(CGRect(x: contentLeft, y: y, width: contentWidth, height: total))
            var lineY = y
            for h in rowHeights.dropLast() {
                lineY += h
                cg.move(to: CGPoint(x: contentLeft, y: lineY))
                cg.addLine(to: CGPoint(x: contentLeft + contentWidth, y: lineY))
            }
            var lineX = contentLeft
            for w in widths.dropLast() {
                lineX += w
                cg.move(to: CGPoint(x: lineX, y: y))
                cg.addLine(to: CGPoint(x: lineX, y: y + total))
            }
            cg.strokePath()
        }
        return total
    }

    // MARK: Footer

    private func drawFooter(page: Int, of pages: Int) {
        let top = pageSize.height - footerHeight
        _ = rule(y: top, color: hairline, draw: true)
        let disclaimer = attr(GPSummaryCopy.footer, footerFont, muted, lineSpacing: 1.5)
        _ = paragraph(disclaimer, y: top + 6, draw: true)

        let generated = "\(GPSummaryCopy.generatedPrefix) \(dateStyle(doc.generatedOn))"
        let line = NSMutableAttributedString(attributedString: attr(generated, footerFont, muted))
        let pageStr = attr("Page \(page) of \(pages)", footerFont, muted)
        let baselineY = pageSize.height - 16
        _ = paragraph(line, y: baselineY, draw: true)
        let pageWidth = ceil(pageStr.size().width)
        if UIGraphicsGetCurrentContext() != nil {
            pageStr.draw(with: CGRect(x: contentLeft + contentWidth - pageWidth, y: baselineY, width: pageWidth, height: 12),
                         options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
        }
    }

    // MARK: Formatting + metadata

    private func dateStyle(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_AU")
        f.dateFormat = "d MMMM yyyy"
        return f.string(from: date)
    }

    /// Blank the Info-dictionary strings so no author, creator, producer, title or the
    /// OS/build string Quartz stamps into Producer survives. Quartz and PDFKit both
    /// force a Producer with the OS version on write, so this operates on the bytes:
    /// each value's interior is overwritten with spaces of equal length, which keeps
    /// every cross-reference offset valid (the file Quartz emits is a classic,
    /// uncompressed xref with a plaintext Info dictionary). Nothing is re-encoded.
    private func stripMetadata(_ data: Data) -> Data {
        var bytes = [UInt8](data)
        for key in ["/Producer", "/Creator", "/Author", "/Title", "/Subject"] {
            blankStringValue(after: Array(key.utf8), in: &bytes)
        }
        return Data(bytes)
    }

    /// Overwrite the interior of the `(...)` string literal that follows `key` with
    /// spaces, honouring `\` escapes and balanced parentheses, preserving length.
    private func blankStringValue(after key: [UInt8], in bytes: inout [UInt8]) {
        guard let start = firstRange(of: key, in: bytes) else { return }
        var i = start + key.count
        while i < bytes.count, bytes[i] == 0x20 || bytes[i] == 0x0A || bytes[i] == 0x0D || bytes[i] == 0x09 { i += 1 }
        guard i < bytes.count, bytes[i] == 0x28 else { return }   // '('
        i += 1
        var depth = 1
        while i < bytes.count, depth > 0 {
            let c = bytes[i]
            if c == 0x5C {                    // backslash escape: blank both bytes
                bytes[i] = 0x20
                if i + 1 < bytes.count { bytes[i + 1] = 0x20 }
                i += 2
                continue
            }
            if c == 0x28 { depth += 1 }
            else if c == 0x29 { break }        // closing paren of the literal
            bytes[i] = 0x20
            i += 1
        }
    }

    private func firstRange(of needle: [UInt8], in haystack: [UInt8]) -> Int? {
        guard !needle.isEmpty, haystack.count >= needle.count else { return nil }
        for i in 0...(haystack.count - needle.count) where Array(haystack[i..<i + needle.count]) == needle {
            return i
        }
        return nil
    }
}
