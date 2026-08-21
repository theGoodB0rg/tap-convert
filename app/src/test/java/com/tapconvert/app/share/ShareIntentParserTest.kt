package com.tapconvert.app.share

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.model.MediaCategory
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
        val intent = Intent(Intent.ACTION_VIEW)
        val result = ShareIntentParser.parse(intent, null, cacheDir)
        assertThat(result).isNull()
    }

    @Test
    fun `determineCategory maps MIME types accurately`() {
        assertThat(ShareIntentParser.determineCategory("image/png")).isEqualTo(MediaCategory.IMAGE)
        assertThat(ShareIntentParser.determineCategory("image/jpeg")).isEqualTo(MediaCategory.IMAGE)
        assertThat(ShareIntentParser.determineCategory("image/webp")).isEqualTo(MediaCategory.IMAGE)
        assertThat(ShareIntentParser.determineCategory("video/mp4")).isEqualTo(MediaCategory.VIDEO)
        assertThat(ShareIntentParser.determineCategory("video/quicktime")).isEqualTo(MediaCategory.VIDEO)
        assertThat(ShareIntentParser.determineCategory("audio/mpeg")).isEqualTo(MediaCategory.AUDIO)
        assertThat(ShareIntentParser.determineCategory("audio/mp4")).isEqualTo(MediaCategory.AUDIO)
        assertThat(ShareIntentParser.determineCategory("application/pdf")).isEqualTo(MediaCategory.DOCUMENT)
    }

    @Test
    fun `detectMimeType extracts specific mime type from intent or falls back to wildcard`() {
        val intent = Intent(Intent.ACTION_SEND)
        val mimeType = ShareIntentParser.detectMimeType(intent, emptyList())
        assertThat(mimeType).isEqualTo("*/*")
    }
}
