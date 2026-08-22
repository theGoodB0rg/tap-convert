package com.tapconvert.core.common.thumbnail

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.model.MediaCategory
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File

class DefaultMediaThumbnailProviderTest {

    private lateinit var provider: DefaultMediaThumbnailProvider
    private val mockContext = mockk<Context>(relaxed = true)
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "thumbnail_test_dir")

    @Before
    fun setUp() {
        tempDir.mkdirs()
        provider = DefaultMediaThumbnailProvider(maxCacheSizeBytes = 1024 * 1024)
    }

    @Test
    fun `loadThumbnail with blank path returns Error`() = runTest {
        val result = provider.loadThumbnail(mockContext, "", 160)
        assertThat(result).isInstanceOf(ThumbnailResult.Error::class.java)
    }

    @Test
    fun `loadThumbnail with non-existent video returns FallbackIcon with VIDEO category`() = runTest {
        val nonExistentVideo = "file:///invalid/path/test.mp4"
        val result = provider.loadThumbnail(mockContext, nonExistentVideo, 160)
        assertThat(result).isInstanceOf(ThumbnailResult.FallbackIcon::class.java)
        val fallback = result as ThumbnailResult.FallbackIcon
        assertThat(fallback.category).isEqualTo(MediaCategory.VIDEO)
    }

    @Test
    fun `loadThumbnail with non-existent audio returns FallbackIcon with AUDIO category`() = runTest {
        val nonExistentAudio = "file:///invalid/path/song.mp3"
        val result = provider.loadThumbnail(mockContext, nonExistentAudio, 160)
        assertThat(result).isInstanceOf(ThumbnailResult.FallbackIcon::class.java)
        val fallback = result as ThumbnailResult.FallbackIcon
        assertThat(fallback.category).isEqualTo(MediaCategory.AUDIO)
    }

    @Test
    fun `loadThumbnail with non-existent PDF returns FallbackIcon with DOCUMENT category`() = runTest {
        val nonExistentPdf = "file:///invalid/path/doc.pdf"
        val result = provider.loadThumbnail(mockContext, nonExistentPdf, 160)
        assertThat(result).isInstanceOf(ThumbnailResult.FallbackIcon::class.java)
        val fallback = result as ThumbnailResult.FallbackIcon
        assertThat(fallback.category).isEqualTo(MediaCategory.DOCUMENT)
    }

    @Test
    fun `loadThumbnail with non-existent image returns FallbackIcon with IMAGE category`() = runTest {
        val nonExistentImage = "file:///invalid/path/pic.jpg"
        val result = provider.loadThumbnail(mockContext, nonExistentImage, 160)
        assertThat(result).isInstanceOf(ThumbnailResult.FallbackIcon::class.java)
        val fallback = result as ThumbnailResult.FallbackIcon
        assertThat(fallback.category).isEqualTo(MediaCategory.IMAGE)
    }

    @Test
    fun `clearCache executes cleanly without errors`() {
        provider.clearCache()
    }
}
