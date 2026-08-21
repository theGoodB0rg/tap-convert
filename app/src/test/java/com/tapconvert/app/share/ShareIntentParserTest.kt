package com.tapconvert.app.share

import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.model.MediaCategory
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ShareIntentParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var cacheDir: File

    @Before
    fun setup() {
        cacheDir = tempFolder.newFolder("cache")
    }

    @Test
    fun `null intent returns null`() {
        val result = ShareIntentParser.parse(null, null, cacheDir)
        assertThat(result).isNull()
    }

    @Test
    fun `intent with unsupported action returns null`() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_VIEW

        val result = ShareIntentParser.parse(intent, null, cacheDir)
        assertThat(result).isNull()
    }

    @Test
    fun `ACTION_SEND with valid single image file URI parses correctly`() {
        val imageFile = tempFolder.newFile("photo.jpg").apply {
            writeBytes(ByteArray(1024) { 0x1 })
        }

        val uri = mockk<Uri>()
        every { uri.scheme } returns "file"
        every { uri.path } returns imageFile.absolutePath
        every { uri.lastPathSegment } returns "photo.jpg"

        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_SEND
        every { intent.type } returns "image/jpeg"
        every { intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) } returns uri
        every { intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns uri
        every { intent.data } returns null
        every { intent.clipData } returns null

        val payload = ShareIntentParser.parse(intent, null, cacheDir)

        assertThat(payload).isNotNull()
        assertThat(payload!!.category).isEqualTo(MediaCategory.IMAGE)
        assertThat(payload.fileNames).containsExactly("photo.jpg")
        assertThat(payload.isMultiple).isFalse()
        assertThat(payload.totalSizeBytes).isEqualTo(1024L)
    }

    @Test
    fun `ACTION_SEND_MULTIPLE with multiple video files parses correctly`() {
        val video1 = tempFolder.newFile("clip1.mp4").apply { writeBytes(ByteArray(2048)) }
        val video2 = tempFolder.newFile("clip2.mp4").apply { writeBytes(ByteArray(4096)) }

        val uri1 = mockk<Uri>()
        every { uri1.scheme } returns "file"
        every { uri1.path } returns video1.absolutePath
        every { uri1.lastPathSegment } returns "clip1.mp4"

        val uri2 = mockk<Uri>()
        every { uri2.scheme } returns "file"
        every { uri2.path } returns video2.absolutePath
        every { uri2.lastPathSegment } returns "clip2.mp4"

        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_SEND_MULTIPLE
        every { intent.type } returns "video/mp4"
        every { intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) } returns arrayListOf(uri1, uri2)
        every { intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) } returns arrayListOf(uri1, uri2)
        every { intent.clipData } returns null

        val payload = ShareIntentParser.parse(intent, null, cacheDir)

        assertThat(payload).isNotNull()
        assertThat(payload!!.category).isEqualTo(MediaCategory.VIDEO)
        assertThat(payload.isMultiple).isTrue()
        assertThat(payload.fileNames).containsExactly("clip1.mp4", "clip2.mp4")
        assertThat(payload.totalSizeBytes).isEqualTo(6144L)
    }

    @Test
    fun `detectMimeType resolves based on extension when intent type is generic`() {
        val uri = mockk<Uri>()
        every { uri.lastPathSegment } returns "document.pdf"

        val intent = mockk<Intent>()
        every { intent.type } returns "*/*"

        val mimeType = ShareIntentParser.detectMimeType(intent, listOf(uri))
        assertThat(mimeType).isEqualTo("application/pdf")
    }

    @Test
    fun `determineCategory maps MIME types accurately`() {
        val uri = mockk<Uri>()
        every { uri.lastPathSegment } returns "song.mp3"

        assertThat(ShareIntentParser.determineCategory("image/png", listOf(uri))).isEqualTo(MediaCategory.IMAGE)
        assertThat(ShareIntentParser.determineCategory("video/mp4", listOf(uri))).isEqualTo(MediaCategory.VIDEO)
        assertThat(ShareIntentParser.determineCategory("audio/mpeg", listOf(uri))).isEqualTo(MediaCategory.AUDIO)
        assertThat(ShareIntentParser.determineCategory("application/pdf", listOf(uri))).isEqualTo(MediaCategory.DOCUMENT)
    }
}
