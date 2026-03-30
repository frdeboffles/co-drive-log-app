package com.codrivelog.app.export

import android.content.Context
import com.codrivelog.core.dr2324.Dr2324Document
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
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

            fillDocument(document, acroForm)

            acroForm.flatten()
            pdDocument.save(outputStream)
        }
    }

    private fun fillDocument(document: Dr2324Document, acroForm: PDAcroForm) {
        setField(acroForm, Dr2324FieldNames.STUDENT_NAME, document.studentProfile.studentName)
        setField(acroForm, Dr2324FieldNames.PERMIT_NUMBER, document.studentProfile.permitNumber)

        document.pages.forEachIndexed { pageIndex, page ->
            page.rows.forEachIndexed { rowIndex, row ->
                setField(acroForm, Dr2324FieldNames.rowDate(pageIndex, rowIndex), row.date)
                setField(acroForm, Dr2324FieldNames.rowVerifierInitials(pageIndex, rowIndex), row.verifierInitials)
                setField(acroForm, Dr2324FieldNames.rowDrivingTime(pageIndex, rowIndex), row.drivingTime)
                setField(acroForm, Dr2324FieldNames.rowNightDrivingTime(pageIndex, rowIndex), row.nightDrivingTime)
                setField(acroForm, Dr2324FieldNames.rowComments(pageIndex, rowIndex), row.comments)
            }

            setField(acroForm, Dr2324FieldNames.pageDrivingTotal(pageIndex),
                com.codrivelog.core.dr2324.Dr2324Formatters.formatTime(page.pageTotalMinutes))
            setField(acroForm, Dr2324FieldNames.pageNightTotal(pageIndex),
                com.codrivelog.core.dr2324.Dr2324Formatters.formatTime(page.pageNightMinutes))
        }

        setField(acroForm, Dr2324FieldNames.GRAND_TOTAL_DRIVING,
            com.codrivelog.core.dr2324.Dr2324Formatters.formatTime(document.grandTotalMinutes))
        setField(acroForm, Dr2324FieldNames.GRAND_TOTAL_NIGHT,
            com.codrivelog.core.dr2324.Dr2324Formatters.formatTime(document.grandNightMinutes))
    }

    private fun setField(acroForm: PDAcroForm, fieldName: String, value: String) {
        val field: PDField = acroForm.getField(fieldName) ?: return
        field.setValue(value)
    }

    companion object {
        private const val TEMPLATE_ASSET = "DR2324_2022.pdf"
    }
}
