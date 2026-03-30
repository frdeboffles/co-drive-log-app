package com.codrivelog.app.export

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codrivelog.core.dr2324.Dr2324Document
import com.codrivelog.core.dr2324.Dr2324Page
import com.codrivelog.core.dr2324.Dr2324Row
import com.codrivelog.core.dr2324.StudentProfile
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Dr2324PdfExporterInstrumentedTest {

    @Test
    fun export_writesNonEmptyPdfFile() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val exporter = Dr2324PdfExporter(context)

        val document = Dr2324Document(
            studentProfile = StudentProfile(studentName = "Test Student", permitNumber = "P1234"),
            pages = listOf(
                Dr2324Page(
                    rows = listOf(
                        Dr2324Row(
                            date = "01/01/2026",
                            verifierInitials = "AB",
                            drivingTime = "1h 0m",
                            nightDrivingTime = "0h 15m",
                            comments = "test",
                            totalMinutes = 60,
                            nightMinutes = 15,
                        )
                    ),
                    pageTotalMinutes = 60,
                    pageNightMinutes = 15,
                )
            ),
            grandTotalMinutes = 60,
            grandNightMinutes = 15,
        )

        val outFile = File(context.cacheDir, "dr2324_test_output.pdf")
        outFile.outputStream().use { stream ->
            exporter.export(document, stream)
        }

        assertTrue(outFile.exists())
        assertTrue(outFile.length() > 0L)
    }
}
