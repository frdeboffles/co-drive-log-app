package com.codrivelog.app.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/**
 * Generates a PDF that mirrors the layout of the Colorado DR 2324
 * "Drive Time Log Sheet".
 *
 * Layout summary (US Letter, portrait):
 * ```
 * ┌─────────────────────────────────────────────────┐
 * │  Colorado Division of Motor Vehicles  DR 2324   │
 * │  DRIVE TIME LOG SHEET                           │
 * │  Student Name: ________________________________  │
 * ├──────────┬────────────┬──────────────┬──────────┬────────────┤
 * │  Date    │Driving Time│Night Driving │Supervisor│ Comments  │
 * │          │            │              │ Initials │           │
 * ├──────────┼────────────┼──────────────┼──────────┼────────────┤
 * │ MM/dd/yy │  Xh Ym     │  Xh Ym       │   AB     │           │
 * │  …       │  …         │  …           │   …      │   …       │
 * ├──────────┴────────────┴──────────────┴──────────┴────────────┤
 * │  GRAND TOTAL DRIVING TIME:  Xh Ym                           │
 * │  GRAND TOTAL NIGHT DRIVING TIME:  Xh Ym                     │
 * └─────────────────────────────────────────────────────────────┘
 * ```
 *
 * Multiple pages are generated automatically when rows overflow the
 * printable area. The header and column labels repeat on each page.
 *
 * All coordinate math is in **points** at 72 dpi (standard PDF unit).
 * US Letter = 612 × 792 pts.
 *
 * This class is intentionally free of Activity / Context dependencies so
 * the data-to-layout mapping logic can be unit-tested on the JVM.
 * The only Android type used is [PdfDocument] (and its nested classes),
 * which are stubbed in Robolectric / unit-test environments.
 */
object PdfExporter {

    // ── Page dimensions (US Letter, portrait) ───────────────────────────────
    const val PAGE_WIDTH  = 612
    const val PAGE_HEIGHT = 792

    // ── Margins ──────────────────────────────────────────────────────────────
    private const val MARGIN_LEFT   = 36f
    private const val MARGIN_RIGHT  = 36f
    private const val MARGIN_TOP    = 36f
    private const val MARGIN_BOTTOM = 36f

    private val PRINTABLE_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT   // 540

    // ── Column widths (must sum to PRINTABLE_WIDTH = 540) ───────────────────
    // Date | Driving Time | Night Driving | Supervisor Initials | Comments
    private val COL_WIDTHS = floatArrayOf(70f, 80f, 90f, 90f, 210f)

    /** Header labels matching DR 2324 column headings. */
    val COL_HEADERS = arrayOf(
        "Date",
        "Driving\nTime",
        "Night\nDriving",
        "Supervisor\nInitials",
        "Comments",
    )

    // ── Row / text metrics ───────────────────────────────────────────────────
    private const val HEADER_BLOCK_HEIGHT = 80f   // space before the table starts
    private const val COL_HEADER_HEIGHT   = 28f   // two-line column header row
    private const val DATA_ROW_HEIGHT     = 18f
    private const val FOOTER_HEIGHT       = 36f   // two grand-total lines
    private const val TEXT_SIZE_TITLE     = 13f
    private const val TEXT_SIZE_SUBTITLE  = 10f
    private const val TEXT_SIZE_COL_HEAD  = 8f
    private const val TEXT_SIZE_DATA      = 9f
    private const val TEXT_SIZE_FOOTER    = 10f
    private const val LINE_PADDING        = 4f    // internal cell padding from top

    // ── Paints ───────────────────────────────────────────────────────────────
    private val paintTitle = Paint().apply {
        textSize = TEXT_SIZE_TITLE
        typeface = Typeface.DEFAULT_BOLD
        color    = Color.BLACK
        isAntiAlias = true
    }
    private val paintSubtitle = Paint().apply {
        textSize = TEXT_SIZE_SUBTITLE
        typeface = Typeface.DEFAULT
        color    = Color.BLACK
        isAntiAlias = true
    }
    private val paintColHead = Paint().apply {
        textSize = TEXT_SIZE_COL_HEAD
        typeface = Typeface.DEFAULT_BOLD
        color    = Color.BLACK
        isAntiAlias = true
    }
    private val paintData = Paint().apply {
        textSize = TEXT_SIZE_DATA
        typeface = Typeface.DEFAULT
        color    = Color.BLACK
        isAntiAlias = true
    }
    private val paintFooter = Paint().apply {
        textSize = TEXT_SIZE_FOOTER
        typeface = Typeface.DEFAULT_BOLD
        color    = Color.BLACK
        isAntiAlias = true
    }
    private val paintGrid = Paint().apply {
        style    = Paint.Style.STROKE
        color    = Color.BLACK
        strokeWidth = 0.75f
    }
    private val paintGridThick = Paint().apply {
        style    = Paint.Style.STROKE
        color    = Color.BLACK
        strokeWidth = 1.5f
    }
    private val paintColHeadBg = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E8E8E8")
    }

    /**
     * Build a [PdfDocument] from [rows] and write it to [outputStream].
     *
     * The stream is flushed but **not** closed.
     *
     * @param rows         Session rows to render.
     * @param studentName  Printed in the "Student Name:" field.
     * @param outputStream Destination stream.
     */
    fun write(
        rows: List<DriveLogRow>,
        studentName: String = "",
        outputStream: OutputStream,
    ) {
        val doc = buildDocument(rows, studentName)
        doc.writeTo(outputStream)
        outputStream.flush()
        doc.close()
    }

    /**
     * Build and return a [PdfDocument] without writing it anywhere.
     * Exposed for testing — callers are responsible for closing the document.
     */
    fun buildDocument(
        rows: List<DriveLogRow>,
        studentName: String = "",
    ): PdfDocument {
        val doc = PdfDocument()

        // Calculate rows per page
        val usableHeight   = PAGE_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM -
                             HEADER_BLOCK_HEIGHT - COL_HEADER_HEIGHT - FOOTER_HEIGHT
        val rowsPerPage    = (usableHeight / DATA_ROW_HEIGHT).toInt().coerceAtLeast(1)
        val pageCount      = if (rows.isEmpty()) 1
                             else (rows.size + rowsPerPage - 1) / rowsPerPage

        val totalMinutes = rows.sumOf { it.totalMinutes }
        val nightMinutes = rows.sumOf { it.nightMinutes }

        for (pageIndex in 0 until pageCount) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1)
                .create()
            val page   = doc.startPage(pageInfo)
            val canvas = page.canvas

            var y = MARGIN_TOP

            // ── Page header ──────────────────────────────────────────────
            y = drawPageHeader(canvas, y, studentName, pageIndex, pageCount)

            // ── Column header row ─────────────────────────────────────────
            y = drawColumnHeaders(canvas, y)

            // ── Data rows for this page ───────────────────────────────────
            val fromIndex = pageIndex * rowsPerPage
            val toIndex   = minOf(fromIndex + rowsPerPage, rows.size)
            val pageRows  = rows.subList(fromIndex, toIndex)

            for (row in pageRows) {
                y = drawDataRow(canvas, y, row)
            }

            // ── Footer (grand totals) on last page only ───────────────────
            if (pageIndex == pageCount - 1) {
                // draw a closing border line above footer
                canvas.drawLine(
                    MARGIN_LEFT, y,
                    MARGIN_LEFT + PRINTABLE_WIDTH, y,
                    paintGridThick,
                )
                y += 6f
                drawFooter(canvas, y, totalMinutes, nightMinutes)
            }

            doc.finishPage(page)
        }

        return doc
    }

    // ── Drawing helpers ──────────────────────────────────────────────────────

    /**
     * Draw the DR 2324 header block and return the Y coordinate immediately
     * below it.
     */
    private fun drawPageHeader(
        canvas: Canvas,
        startY: Float,
        studentName: String,
        pageIndex: Int,
        pageCount: Int,
    ): Float {
        var y = startY

        // Form number (right-aligned)
        val formLabel = "DR 2324 (Rev. 01/22)"
        canvas.drawText(
            formLabel,
            MARGIN_LEFT + PRINTABLE_WIDTH - paintSubtitle.measureText(formLabel),
            y + 10f,
            paintSubtitle,
        )

        // Agency line
        canvas.drawText(
            "Colorado Division of Motor Vehicles",
            MARGIN_LEFT,
            y + 10f,
            paintSubtitle,
        )

        y += 20f

        // Title
        val title = "DRIVE TIME LOG SHEET"
        canvas.drawText(
            title,
            MARGIN_LEFT + (PRINTABLE_WIDTH - paintTitle.measureText(title)) / 2f,
            y,
            paintTitle,
        )

        y += 16f

        // Student name line
        val nameLabel = "Student Name: "
        canvas.drawText(nameLabel, MARGIN_LEFT, y, paintSubtitle)
        val nameValueX = MARGIN_LEFT + paintSubtitle.measureText(nameLabel)
        val nameLine = if (studentName.isNotBlank()) studentName else "_________________________"
        canvas.drawText(nameLine, nameValueX, y, paintSubtitle)

        // Page number
        if (pageCount > 1) {
            val pageLabel = "Page ${pageIndex + 1} of $pageCount"
            canvas.drawText(
                pageLabel,
                MARGIN_LEFT + PRINTABLE_WIDTH - paintSubtitle.measureText(pageLabel),
                y,
                paintSubtitle,
            )
        }

        y += 10f

        // Top border of table
        canvas.drawLine(MARGIN_LEFT, y, MARGIN_LEFT + PRINTABLE_WIDTH, y, paintGridThick)

        return y
    }

    /** Draw the shaded column-header row; return Y below it. */
    private fun drawColumnHeaders(canvas: Canvas, startY: Float): Float {
        val rowBottom = startY + COL_HEADER_HEIGHT
        var x = MARGIN_LEFT

        for (i in COL_WIDTHS.indices) {
            val colRight = x + COL_WIDTHS[i]

            // Background fill
            canvas.drawRect(x, startY, colRight, rowBottom, paintColHeadBg)

            // Header text (may contain \n for two lines)
            val lines = COL_HEADERS[i].split("\n")
            val lineH = TEXT_SIZE_COL_HEAD + 3f
            val textBlockH = lines.size * lineH
            var ty = startY + (COL_HEADER_HEIGHT - textBlockH) / 2f + TEXT_SIZE_COL_HEAD

            for (line in lines) {
                val tw = paintColHead.measureText(line)
                canvas.drawText(line, x + (COL_WIDTHS[i] - tw) / 2f, ty, paintColHead)
                ty += lineH
            }

            // Right border
            canvas.drawLine(colRight, startY, colRight, rowBottom, paintGrid)
            x = colRight
        }

        // Left border
        canvas.drawLine(MARGIN_LEFT, startY, MARGIN_LEFT, rowBottom, paintGrid)

        // Bottom border of header row (thicker)
        canvas.drawLine(MARGIN_LEFT, rowBottom, MARGIN_LEFT + PRINTABLE_WIDTH, rowBottom, paintGridThick)

        return rowBottom
    }

    /** Draw one data row; return Y below it. */
    private fun drawDataRow(canvas: Canvas, startY: Float, row: DriveLogRow): Float {
        val rowBottom = startY + DATA_ROW_HEIGHT
        var x = MARGIN_LEFT
        val textY = startY + DATA_ROW_HEIGHT - LINE_PADDING

        val fields = arrayOf(
            row.date,
            row.drivingTime,
            row.nightDriving,
            row.supervisorInitials,
            row.comments,
        )

        for (i in COL_WIDTHS.indices) {
            val colRight = x + COL_WIDTHS[i]

            // Clip text to column width with padding
            val text = fields[i]
            val maxW = COL_WIDTHS[i] - 4f
            val tw = paintData.measureText(text)
            val drawText = if (tw <= maxW) text
                           else truncateToWidth(text, maxW, paintData)

            canvas.drawText(drawText, x + 2f, textY, paintData)

            // Right border
            canvas.drawLine(colRight, startY, colRight, rowBottom, paintGrid)
            x = colRight
        }

        // Left border
        canvas.drawLine(MARGIN_LEFT, startY, MARGIN_LEFT, rowBottom, paintGrid)

        // Bottom border
        canvas.drawLine(MARGIN_LEFT, rowBottom, MARGIN_LEFT + PRINTABLE_WIDTH, rowBottom, paintGrid)

        return rowBottom
    }

    /** Draw grand-total footer lines. */
    private fun drawFooter(canvas: Canvas, startY: Float, totalMinutes: Int, nightMinutes: Int) {
        val lineHeight = TEXT_SIZE_FOOTER + 6f

        canvas.drawText(
            "GRAND TOTAL DRIVING TIME:  ${DriveLogRow.formatMinutes(totalMinutes)}",
            MARGIN_LEFT,
            startY + lineHeight,
            paintFooter,
        )
        canvas.drawText(
            "GRAND TOTAL NIGHT DRIVING TIME:  ${DriveLogRow.formatMinutes(nightMinutes)}",
            MARGIN_LEFT,
            startY + lineHeight * 2f,
            paintFooter,
        )
    }

    /**
     * Truncate [text] to at most [maxWidth] points wide (measured by [paint])
     * appending "…" to indicate overflow.
     */
    private fun truncateToWidth(text: String, maxWidth: Float, paint: Paint): String {
        val ellipsis = "…"
        val ellipsisW = paint.measureText(ellipsis)
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end)) + ellipsisW > maxWidth) {
            end--
        }
        return text.substring(0, end) + ellipsis
    }
}
