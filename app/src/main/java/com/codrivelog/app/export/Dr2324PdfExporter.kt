package com.codrivelog.app.export

import android.content.Context
import com.codrivelog.core.dr2324.Dr2324Document
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import java.io.OutputStream

class Dr2324PdfExporter(private val context: Context) {

    fun export(document: Dr2324Document, outputStream: OutputStream) {
        PDFBoxResourceLoader.init(context)
        context.assets.open(TEMPLATE_ASSET).use { input ->
            val templateBytes = input.readBytes()
            writeDocument(document, templateBytes, outputStream)
        }
    }

    private fun writeDocument(document: Dr2324Document, templateBytes: ByteArray, outputStream: OutputStream) {
        PDDocument.load(templateBytes).use { pdDocument ->
            val acroForm = pdDocument.documentCatalog.acroForm ?: PDAcroForm(pdDocument)
            pdDocument.documentCatalog.acroForm = acroForm

            while (pdDocument.numberOfPages < document.pages.size * 2) {
                PDDocument.load(templateBytes).use { templateDoc ->
                    pdDocument.importPage(templateDoc.pages[0])
                    pdDocument.importPage(templateDoc.pages[1])
                }
            }

            val filledFields = fillDocument(document, acroForm)
            if (filledFields == 0) {
                drawOverlayFallback(document, pdDocument)
            }

            acroForm.flatten()
            pdDocument.save(outputStream)
        }
    }

    private fun fillDocument(document: Dr2324Document, acroForm: PDAcroForm): Int {
        var filled = 0
        if (setField(acroForm, Dr2324FieldNames.STUDENT_NAME, document.studentProfile.studentName)) filled++
        if (setField(acroForm, Dr2324FieldNames.PERMIT_NUMBER, document.studentProfile.permitNumber)) filled++

        document.pages.forEachIndexed { pageIndex, page ->
            page.rows.forEachIndexed { rowIndex, row ->
                if (setField(acroForm, Dr2324FieldNames.rowDate(pageIndex, rowIndex), row.date)) filled++
                if (setField(acroForm, Dr2324FieldNames.rowVerifierInitials(pageIndex, rowIndex), row.verifierInitials)) filled++
                if (setField(acroForm, Dr2324FieldNames.rowDrivingTime(pageIndex, rowIndex), row.drivingTime)) filled++
                if (setField(acroForm, Dr2324FieldNames.rowNightDrivingTime(pageIndex, rowIndex), row.nightDrivingTime)) filled++
                if (setField(acroForm, Dr2324FieldNames.rowComments(pageIndex, rowIndex), row.comments)) filled++
            }

            if (setField(
                    acroForm,
                    Dr2324FieldNames.pageDrivingTotal(pageIndex),
                    com.codrivelog.core.dr2324.Dr2324Formatters.formatTime(page.pageTotalMinutes)
                )) filled++
            if (setField(
                    acroForm,
                    Dr2324FieldNames.pageNightTotal(pageIndex),
                    com.codrivelog.core.dr2324.Dr2324Formatters.formatTime(page.pageNightMinutes)
                )) filled++
        }

        if (setField(
                acroForm,
                Dr2324FieldNames.GRAND_TOTAL_DRIVING,
                com.codrivelog.core.dr2324.Dr2324Formatters.formatTime(document.grandTotalMinutes)
            )) filled++
        if (setField(
                acroForm,
                Dr2324FieldNames.GRAND_TOTAL_NIGHT,
                com.codrivelog.core.dr2324.Dr2324Formatters.formatTime(document.grandNightMinutes)
            )) filled++

        return filled
    }

    private fun setField(acroForm: PDAcroForm, fieldName: String, value: String): Boolean {
        val field: PDField = acroForm.getField(fieldName) ?: return false
        field.setValue(value)
        return true
    }

    private fun drawOverlayFallback(document: Dr2324Document, pdDocument: PDDocument) {
        document.pages.forEachIndexed { pageIndex, page ->
            val page1Index = pageIndex * 2
            val page2Index = page1Index + 1
            if (page1Index < pdDocument.numberOfPages) {
                drawPage1Overlay(pdDocument, page1Index, document)
            }
            if (page2Index < pdDocument.numberOfPages) {
                drawPage2Overlay(pdDocument, page2Index, page, document)
            }
        }
    }

    private fun drawPage1Overlay(pdDocument: PDDocument, pageIndex: Int, document: Dr2324Document) {
        PDPageContentStream(pdDocument, pdDocument.pages[pageIndex], PDPageContentStream.AppendMode.APPEND, true).use { cs ->
            cs.setFont(PDType1Font.HELVETICA, 10f)
            if (document.studentProfile.studentName.isNotBlank()) {
                drawText(cs, 128f, 667f, document.studentProfile.studentName)
            }
            if (document.studentProfile.permitNumber.isNotBlank()) {
                drawText(cs, 400f, 667f, document.studentProfile.permitNumber)
            }
        }
    }

    private fun drawPage2Overlay(
        pdDocument: PDDocument,
        pageIndex: Int,
        page: com.codrivelog.core.dr2324.Dr2324Page,
        document: Dr2324Document,
    ) {
        PDPageContentStream(pdDocument, pdDocument.pages[pageIndex], PDPageContentStream.AppendMode.APPEND, true).use { cs ->
            cs.setFont(PDType1Font.HELVETICA, 8.5f)

            var rowTopY = 756f
            repeat(com.codrivelog.core.dr2324.Dr2324Mapper.ROWS_PER_PAGE) { i ->
                val row = page.rows.getOrNull(i)
                if (row != null) {
                    drawText(cs, 38f, rowTopY - 14f, row.date)
                    drawText(cs, 173f, rowTopY - 14f, row.verifierInitials)
                    drawText(cs, 308f, rowTopY - 14f, row.drivingTime)
                    drawText(cs, 443f, rowTopY - 14f, row.nightDrivingTime)
                    drawText(cs, 38f, rowTopY - 34f, row.comments)
                }
                rowTopY -= 40f
            }

            drawText(cs, 308f, 237f, com.codrivelog.core.dr2324.Dr2324Formatters.formatTime(page.pageTotalMinutes))
            drawText(cs, 443f, 237f, com.codrivelog.core.dr2324.Dr2324Formatters.formatTime(page.pageNightMinutes))

            cs.setFont(PDType1Font.HELVETICA_BOLD, 10f)
            drawText(cs, 376f, 144f, com.codrivelog.core.dr2324.Dr2324Formatters.formatTime(document.grandTotalMinutes))
            drawText(cs, 376f, 114f, com.codrivelog.core.dr2324.Dr2324Formatters.formatTime(document.grandNightMinutes))
        }
    }

    private fun drawText(contentStream: PDPageContentStream, x: Float, y: Float, text: String) {
        if (text.isBlank()) return
        contentStream.beginText()
        contentStream.newLineAtOffset(x, y)
        contentStream.showText(text)
        contentStream.endText()
    }

    companion object {
        private const val TEMPLATE_ASSET = "DR2324_2022.pdf"
    }
}
