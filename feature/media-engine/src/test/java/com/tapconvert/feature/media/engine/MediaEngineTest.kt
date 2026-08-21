package com.tapconvert.feature.media.engine

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.ConversionError
import com.tapconvert.core.model.ConversionRequest
import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MimeType
import com.tapconvert.core.model.Preset
import com.tapconvert.core.testing.FakeAnalyticsTracker
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

class MediaEngineTest {

    private val fakeAnalytics = FakeAnalyticsTracker()
    private val engine = DefaultMediaEngine(fakeAnalytics)
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "tapconvert_media_test_out")

    @Test
    fun `compressVideo emits error when source file does not exist and logs failed event`() = runTest {
        val request = ConversionRequest(
            sourceUris = listOf("file:///non_existent_dir/video.mp4"),
            conversionType = ConversionType.VIDEO_COMPRESS,
            targetMimeType = MimeType.Video.MP4,
            preset = Preset.WhatsAppVideo16MB
        )

        engine.compressVideo(request, tempDir).test {
            val p1 = awaitItem()
            assertThat(p1.isProgress).isTrue()

            val err = awaitItem()
            assertThat(err.isError).isTrue()
            val error = (err as AppResult.Error).throwable
            assertThat(error).isInstanceOf(ConversionError.FileNotFound::class.java)

            awaitComplete()
        }

        assertThat(fakeAnalytics.startedConversions).hasSize(1)
        assertThat(fakeAnalytics.failedConversions).hasSize(1)
        assertThat(fakeAnalytics.failedConversions[0].errorType).isEqualTo("FileNotFound")
    }

    @Test
    fun `extractAudio emits error when source media file does not exist`() = runTest {
        val request = ConversionRequest(
            sourceUris = listOf("file:///non_existent_dir/movie.mp4"),
            conversionType = ConversionType.EXTRACT_AUDIO,
            targetMimeType = MimeType.Audio.MP3,
            preset = Preset.Mp3HighQuality320
        )

        engine.extractAudio(request, tempDir).test {
            val p1 = awaitItem()
            assertThat(p1.isProgress).isTrue()

            val err = awaitItem()
            assertThat(err.isError).isTrue()
            val error = (err as AppResult.Error).throwable
            assertThat(error).isInstanceOf(ConversionError.FileNotFound::class.java)

            awaitComplete()
        }

        assertThat(fakeAnalytics.startedConversions).hasSize(1)
        assertThat(fakeAnalytics.failedConversions).hasSize(1)
    }
}
