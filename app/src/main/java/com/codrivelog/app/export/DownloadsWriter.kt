package com.codrivelog.app.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException
import java.io.OutputStream

/**
 * Writes a file to the public Downloads folder through MediaStore. No
 * storage permission is needed on Android 10 and later.
 *
 * A plain function, not part of [ExportManager], so the database migration
 * can use it without depending on the repositories.
 */
object DownloadsWriter {

    /**
     * Creates [fileName] in Downloads and fills it with [write].
     *
     * @return The [Uri] of the new file, or `null` on failure; a partly
     *   written file is deleted.
     */
    fun save(
        context: Context,
        fileName: String,
        mimeType: String,
        write: (OutputStream) -> Unit,
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
            // No stream means nothing was written: treat it as a failure, so
            // no empty file is left behind and the caller sees null.
            val stream = resolver.openOutputStream(uri) ?: throw IOException("No output stream for $uri")
            stream.use(write)
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
}
