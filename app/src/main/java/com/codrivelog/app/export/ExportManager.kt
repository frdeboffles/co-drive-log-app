package com.codrivelog.app.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.codrivelog.app.data.repository.DriveSessionRepository
import com.codrivelog.core.dr2324.Dr2324Document
import com.codrivelog.core.dr2324.Dr2324Mapper
import com.codrivelog.core.dr2324.DriveSession as CoreDriveSession
import com.codrivelog.core.dr2324.StudentProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates PDF and CSV export:
 *
 * 1. Fetches all [com.codrivelog.app.data.model.DriveSession] records from the
 *    repository (single snapshot, not a live flow).
 * 2. Maps them to [DriveLogRow] instances.
 * 3. Writes the file to the public Downloads folder via [MediaStore] (no
 *    `WRITE_EXTERNAL_STORAGE` permission required on API 29+).
 * 4. Returns the [Uri] of the saved file so the caller can open / share it.
 *
 * All file I/O runs on [Dispatchers.IO].
 *
 * @param context    Application context (injected by Hilt).
 * @param repository Source of all drive session records.
 */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DriveSessionRepository,
) {

    private val dateSuffix: String
        get() = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))

    /**
     * Export all sessions as a PDF and save it to Downloads.
     *
     * @param studentName  Student name to embed in the PDF header.
     * @param permitNumber Permit number to embed in the PDF header.
     * @return [Uri] of the first saved file, or `null` on failure.
     */
    suspend fun exportPdf(studentName: String = "", permitNumber: String = ""): Uri? = withContext(Dispatchers.IO) {
        val sessions = fetchCoreSessions()
        if (sessions.isEmpty()) return@withContext null

        val exporter = Dr2324PdfExporter(context)
        val mimeType = "application/pdf"
        val document = Dr2324Mapper.map(
            studentProfile = StudentProfile(
                studentName = studentName.trim(),
                permitNumber = permitNumber.trim(),
            ),
            sessions = sessions,
        )

        val totalSheets = document.pages.size
        val uris = mutableListOf<Uri>()

        document.pages.forEachIndexed { index, page ->
            val fileName = if (totalSheets == 1) {
                "CoDriveLog_${dateSuffix}.pdf"
            } else {
                "CoDriveLog_${dateSuffix}_part${(index + 1).toString().padStart(2, '0')}.pdf"
            }

            val pageDocument = Dr2324Document(
                studentProfile = document.studentProfile,
                pages = listOf(page),
                grandTotalMinutes = page.pageTotalMinutes,
                grandNightMinutes = page.pageNightMinutes,
            )

            val uri = saveToDownloads(fileName, mimeType) { stream ->
                exporter.export(pageDocument, stream)
            }

            if (uri != null) {
                uris += uri
            }
        }

        uris.firstOrNull()
    }

    /**
     * Export all sessions as a CSV and save it to Downloads.
     *
     * @param studentName Student name to embed as a comment at the top.
     * @return [Uri] of the saved file, or `null` on failure.
     */
    suspend fun exportCsv(studentName: String = ""): Uri? = withContext(Dispatchers.IO) {
        val rows = fetchRows()
        if (rows.isEmpty()) return@withContext null

        val fileName = "CoDriveLog_${dateSuffix}.csv"
        val mimeType = "text/csv"

        saveToDownloads(fileName, mimeType) { stream ->
            CsvExporter.write(rows, studentName, stream)
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    /** Fetch all sessions and map to export rows (ascending by date). */
    private suspend fun fetchRows(): List<DriveLogRow> =
        repository.getAll()
            .first()
            .sortedBy { it.date }
            .map { DriveLogRow.from(it) }

    private suspend fun fetchCoreSessions(): List<CoreDriveSession> =
        repository.getAll()
            .first()
            .sortedBy { it.date }
            .map { session ->
                CoreDriveSession(
                    date = session.date,
                    verifierInitials = session.supervisorInitials,
                    totalMinutes = session.totalMinutes,
                    nightMinutes = session.nightMinutes,
                    comments = session.comments.orEmpty(),
                )
            }

    /**
     * Insert an entry into [MediaStore.Downloads] and write content to it.
     *
     * @param fileName  Display name of the file.
     * @param mimeType  MIME type string.
     * @param write     Lambda that receives the open [java.io.OutputStream].
     * @return The [Uri] of the new file, or `null` on failure.
     */
    private fun saveToDownloads(
        fileName: String,
        mimeType: String,
        write: (java.io.OutputStream) -> Unit,
    ): Uri? {
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { stream ->
                write(stream)
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            // Clean up the dangling MediaStore entry on failure
            resolver.delete(uri, null, null)
            null
        }
    }

    /**
     * Fire an [Intent.ACTION_VIEW] for [uri] so the user can open the file
     * in an appropriate app (PDF viewer / spreadsheet app).
     *
     * Must be called from the main thread / Activity context. The returned
     * intent should be started via [android.app.Activity.startActivity] with
     * a chooser.
     */
    fun buildOpenIntent(uri: Uri, mimeType: String): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
