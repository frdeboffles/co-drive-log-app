package com.codrivelog.app.backup

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.codrivelog.app.export.ExportManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** File access for backups, separated from [BackupManager] so it can be faked in tests. */
interface BackupFileStore {

    /**
     * Creates [fileName] in the public Downloads folder and fills it with [write].
     *
     * @return The name the file was stored under, or `null` on failure.
     *   It can differ from [fileName], for example `name (1).json` when a
     *   file with that name already exists.
     */
    fun saveToDownloads(fileName: String, write: (OutputStream) -> Unit): String?

    /** Opens a file picked by the user, or returns `null` when it cannot be opened. */
    fun openInput(uri: Uri): InputStream?
}

@Singleton
class MediaStoreBackupFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportManager: ExportManager,
) : BackupFileStore {

    override fun saveToDownloads(fileName: String, write: (OutputStream) -> Unit): String? {
        val uri = exportManager.saveToDownloads(fileName, MIME_TYPE, write) ?: return null
        return storedName(uri) ?: fileName
    }

    override fun openInput(uri: Uri): InputStream? =
        context.contentResolver.openInputStream(uri)

    private fun storedName(uri: Uri): String? =
        context.contentResolver
            .query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    companion object {
        const val MIME_TYPE = "application/json"
    }
}
