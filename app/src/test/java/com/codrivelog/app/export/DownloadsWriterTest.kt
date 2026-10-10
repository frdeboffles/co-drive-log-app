package com.codrivelog.app.export

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DownloadsWriterTest {

    private val resolver = mockk<ContentResolver>()
    private val context = mockk<Context> { every { contentResolver } returns resolver }
    private val entry = mockk<Uri>()

    @BeforeEach
    fun setUp() {
        // Android framework classes are stubs in JVM tests.
        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } just runs
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } just runs
        every { anyConstructed<ContentValues>().clear() } just runs
        mockkStatic(MediaStore.Downloads::class)
        every { MediaStore.Downloads.getContentUri(any()) } returns mockk()
        every { resolver.insert(any(), any()) } returns entry
        every { resolver.delete(entry, null, null) } returns 1
        // Stubbed so a write that skips the stream would complete and return the entry.
        every { resolver.update(entry, any(), null, null) } returns 1
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `no output stream is a failure that leaves no empty file`() {
        // Review: an automatic backup must not leave an empty file named
        // like a backup while reporting success.
        every { resolver.openOutputStream(entry) } returns null
        var written = false

        val result = DownloadsWriter.save(context, "backup.json", "application/json") { written = true }

        assertNull(result)
        assertFalse(written)
        verify { resolver.delete(entry, null, null) }
    }
}
