/*
 * PdfTextEditEngine.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Real (non-overlay) PDF text editing, backed by PDFBox-Android.
 *
 * android.graphics.pdf.PdfRenderer (used everywhere else in this viewer) can
 * only rasterize pages to bitmaps — it has no API for reading or rewriting
 * the text inside a PDF's content stream. This file is the part of the app
 * that actually can: it uses PDFBox to (1) extract each page's text as
 * positioned lines, and (2) rewrite a specific line's text in place.
 *
 * IMPORTANT — HONEST LIMITATIONS, not bugs to silently work around:
 *
 *  1. FONT FIDELITY: most PDFs embed only the glyph subset they use. When a
 *     line is edited, we cannot safely re-inject arbitrary new characters
 *     into that embedded subset — the subset physically may not contain
 *     them. We instead render edited lines with a standard PDFBox base font
 *     (Helvetica). This means an edited line's font *style* can visibly
 *     differ from the surrounding untouched text. This is a deliberate,
 *     documented tradeoff, not something to hide from the user.
 *
 *  2. LINE GRANULARITY: text is grouped into lines by vertical position, not
 *     sentences or words — PDFs have no native concept of either. Tapping a
 *     line selects the whole line for editing.
 *
 *  3. REDACT-AND-REDRAW: an edit does not "modify" existing glyph operators
 *     in place (that's not practically expressible through PDFBox's content
 *     stream API for arbitrary text). It paints a white rectangle over the
 *     old line's bounding box, then draws the new text at the same origin.
 *     If the original had a non-white background (colored highlight,
 *     shaded row, image behind text) that background is lost under the
 *     redaction box for that line's height. This is called out in-code and
 *     should be called out in the UI (done in PetalPdfViewerScreen).
 *
 *  4. NOT COMPILE-VERIFIED: written without access to an Android SDK/
 *     emulator. Logic follows PDFBox-Android's documented API shape, but
 *     build and runtime behavior must be verified on-device.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.compose.pdf

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File

/** One geometrically-clustered line of text on a page, in PDF user-space coordinates. */
data class PdfTextLine(
    val pageIndex: Int,
    val text: String,
    /** Left edge, in PDF points, measured from the page's left edge. */
    val x: Float,
    /** Baseline Y, in PDF points, measured from the page's BOTTOM (PDF convention). */
    val baselineY: Float,
    /** Approximate visual height of the line, in PDF points. */
    val height: Float,
    /** Approximate total width of the line, in PDF points. */
    val width: Float,
    /** Detected font size in points, used to size the replacement text. */
    val fontSizePt: Float
)

object PdfTextEditEngine {

    private var loaderInitialized = false

    /** Must be called once (safe to call repeatedly) before any other function here. */
    fun ensureInitialized(context: Context) {
        if (!loaderInitialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            loaderInitialized = true
        }
    }

    /**
     * Extracts every text line on [pageIndex] with its position, by running a
     * custom PDFTextStripper that captures each character's TextPosition
     * instead of just concatenated strings, then clustering characters into
     * lines by baseline-Y proximity.
     *
     * Returns an empty list on any failure (e.g. scanned/image-only page with
     * no real text layer — there is nothing to extract in that case, and that
     * is a correct, honest empty result, not an error to paper over).
     */
    fun extractLinesForPage(file: File, pageIndex: Int): List<PdfTextLine> {
        val collected = mutableListOf<PdfTextLine>()
        runCatching {
            PDDocument.load(file).use { document ->
                if (pageIndex !in 0 until document.numberOfPages) return@use

                val positions = mutableListOf<TextPosition>()
                val stripper = object : PDFTextStripper() {
                    override fun processTextPosition(text: TextPosition) {
                        positions.add(text)
                        super.processTextPosition(text)
                    }
                }
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                stripper.sortByPosition = true
                // Trigger extraction — result string itself is unused, we only need
                // the TextPosition callbacks captured above.
                stripper.getText(document)

                collected.addAll(clusterIntoLines(pageIndex, positions))
            }
        }.onFailure { it.printStackTrace() }
        return collected
    }

    /**
     * Groups characters into lines using baseline-Y proximity (characters
     * within ~40% of the tallest glyph's height on that row are the same
     * line), then within each line sorts left-to-right and concatenates.
     */
    private fun clusterIntoLines(pageIndex: Int, positions: List<TextPosition>): List<PdfTextLine> {
        if (positions.isEmpty()) return emptyList()

        // Group by rounded baseline Y first pass.
        val rows = mutableListOf<MutableList<TextPosition>>()
        val sortedByY = positions.sortedBy { it.yDirAdj }
        for (pos in sortedByY) {
            val lastRow = rows.lastOrNull()
            val rowAnchorY = lastRow?.firstOrNull()?.yDirAdj
            val threshold = (pos.heightDir.takeIf { it > 0f } ?: 10f) * 0.6f
            if (lastRow != null && rowAnchorY != null && kotlin.math.abs(pos.yDirAdj - rowAnchorY) <= threshold) {
                lastRow.add(pos)
            } else {
                rows.add(mutableListOf(pos))
            }
        }

        return rows.mapNotNull { row ->
            if (row.isEmpty()) return@mapNotNull null
            val sortedRow = row.sortedBy { it.xDirAdj }
            val text = buildString { sortedRow.forEach { append(it.unicode ?: "") } }.trim()
            if (text.isEmpty()) return@mapNotNull null

            val minX = sortedRow.minOf { it.xDirAdj }
            val maxX = sortedRow.maxOf { it.xDirAdj + it.widthDirAdj }
            val avgHeight = sortedRow.map { it.heightDir }.average().toFloat().takeIf { it > 0f } ?: 10f
            val avgFontSize = sortedRow.map { it.fontSizeInPt }.average().toFloat().takeIf { it > 0f } ?: 12f
            val baseline = sortedRow.first().yDirAdj

            PdfTextLine(
                pageIndex = pageIndex,
                text = text,
                x = minX,
                baselineY = baseline,
                height = avgHeight,
                width = (maxX - minX).coerceAtLeast(1f),
                fontSizePt = avgFontSize
            )
        }
    }

    /**
     * Rewrites [pageIndex] of the PDF at [file] in place: redacts the region
     * described by [line] with a white rectangle, then draws [newText] at the
     * same origin/size using a standard base font (see class doc — original
     * embedded font is not reused). Saves back to [file] on success.
     *
     * Returns true on success, false on any failure (file left untouched).
     */
    fun replaceLineText(file: File, line: PdfTextLine, newText: String): Boolean {
        return runCatching {
            PDDocument.load(file).use { document ->
                val page: PDPage = document.getPage(line.pageIndex)
                val mediaBox = page.mediaBox

                PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { stream ->
                    // Redact: white rectangle over the old line's bounding box,
                    // padded slightly so old glyph edges/descenders are covered.
                    val padY = line.height * 0.35f
                    stream.setNonStrokingColor(255, 255, 255)
                    stream.addRect(
                        line.x - 1f,
                        line.baselineY - padY,
                        line.width + 2f,
                        line.height + padY * 2f
                    )
                    stream.fill()

                    // Draw replacement text at the same origin.
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, line.fontSizePt)
                    stream.setNonStrokingColor(0, 0, 0)
                    stream.newLineAtOffset(line.x, line.baselineY)
                    // PDType1Font.HELVETICA only supports WinAnsi-encodable characters;
                    // strip anything outside that range rather than crash the save.
                    val safeText = newText.filter { it.code in 32..255 }
                    stream.showText(safeText)
                    stream.endText()
                }

                document.save(file)
            }
            true
        }.onFailure { it.printStackTrace() }.getOrDefault(false)
    }
}
