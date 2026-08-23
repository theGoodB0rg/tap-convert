package com.tapconvert.feature.media.engine

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.ConversionError
import com.tapconvert.core.model.ConversionQuality
import com.tapconvert.core.model.ConversionRequest
import com.tapconvert.core.model.ConversionResult
import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MimeType
import com.tapconvert.core.model.Preset
import com.tapconvert.core.model.TargetSize
import com.tapconvert.core.testing.FakeAnalyticsTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File

class MediaEngineTest {

    private val fakeAnalytics = FakeAnalyticsTracker()
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "tapconvert_media_test_out")
    private val testSourceFile = File(System.getProperty("java.io.tmpdir"), "test_video_input.mp4")

    @Before
    fun setUp() {
        tempDir.mkdirs()
        testSourceFile.writeBytes(ByteArray(1024) { 0x55 })
    }

    @Test
    fun `compressVideo emits error when source file does not exist and logs failed event`() = runTest {
        val engine = DefaultMediaEngine(analyticsTracker = fakeAnalytics)
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
    fun `compressVideo successfully executes transcoding and returns complete result`() = runTest {
        val fakeTranscoder = object : VideoTranscoder {
            override fun transcode(
                sourceFile: File,
                outputFile: File,
                encodingSpec: BitrateCalculator.VideoEncodingSpec
            ): Flow<AppResult<File>> = flow {
                emit(AppResult.Progress(50, "Halfway done"))
                outputFile.writeBytes(ByteArray(512) { 0x11 })
                emit(AppResult.Success(outputFile))
            }
        }

        val engine = DefaultMediaEngine(transcoder = fakeTranscoder, analyticsTracker = fakeAnalytics)
        val request = ConversionRequest(
            sourceUris = listOf("file://${testSourceFile.absolutePath}"),
            conversionType = ConversionType.VIDEO_COMPRESS,
            targetMimeType = MimeType.Video.MP4,
            preset = Preset.WhatsAppVideo16MB,
            targetSize = TargetSize.fromMegabytes(16)
        )

        engine.compressVideo(request, tempDir).test {
            val p1 = awaitItem() // Analyzing
            assertThat(p1.isProgress).isTrue()

            val p2 = awaitItem() // Preparing
            assertThat(p2.isProgress).isTrue()

            val p3 = awaitItem() // Transcoding (mapped from 50%)
            assertThat(p3.isProgress).isTrue()

            val p4 = awaitItem() // Finalizing
            assertThat(p4.isProgress).isTrue()

            val successItem = awaitItem()
            assertThat(successItem.isSuccess).isTrue()
            val result = (successItem as AppResult.Success).data
            assertThat(result.conversionType).isEqualTo(ConversionType.VIDEO_COMPRESS)
            assertThat(result.outputUris).isNotEmpty()
            assertThat(result.metadata).containsKey("videoBitrateBps")
            assertThat(result.metadata).containsKey("audioBitrateBps")
            assertThat(result.metadata).containsKey("targetMaxDimension")

            awaitComplete()
        }

        assertThat(fakeAnalytics.completedConversions).hasSize(1)
    }

    @Test
    fun `compressVideo triggers 1-pass compensation retry when encoder overshoots target budget`() = runTest {
        var callCount = 0
        val overshootingTranscoder = object : VideoTranscoder {
            override fun transcode(
                sourceFile: File,
                outputFile: File,
                encodingSpec: BitrateCalculator.VideoEncodingSpec
            ): Flow<AppResult<File>> = flow {
                callCount++
                if (callCount == 1) {
                    // First attempt: Overshoots target budget (produces 1MB when target is ~512KB)
                    outputFile.writeBytes(ByteArray(1024 * 1024) { 0xAA.toByte() })
                } else {
                    // Second attempt: Properly compressed within budget (produces 400KB)
                    outputFile.writeBytes(ByteArray(400 * 1024) { 0xBB.toByte() })
                }
                emit(AppResult.Success(outputFile))
            }
        }

        val engine = DefaultMediaEngine(transcoder = overshootingTranscoder, analyticsTracker = fakeAnalytics)
        val source5Mb = File(System.getProperty("java.io.tmpdir"), "source_5mb.mp4").apply {
            writeBytes(ByteArray(5 * 1024 * 1024) { 0x44 })
        }

        val request = ConversionRequest(
            sourceUris = listOf("file://${source5Mb.absolutePath}"),
            conversionType = ConversionType.VIDEO_COMPRESS,
            targetMimeType = MimeType.Video.MP4,
            quality = ConversionQuality.Custom(10) // 10% quality budget = ~512KB target
        )

        engine.compressVideo(request, tempDir).test {
            // Should emit progress items and finally success
            var lastItem: AppResult<ConversionResult>? = null
            while (true) {
                val item = awaitItem()
                lastItem = item
                if (item is AppResult.Success || item is AppResult.Error) break
            }

            assertThat(lastItem).isInstanceOf(AppResult.Success::class.java)
            val data = (lastItem as AppResult.Success).data
            assertThat(data.outputUris).hasSize(1)
            val outFile = File(data.outputUris.first())
            assertThat(outFile.exists()).isTrue()
            assertThat(outFile.length()).isEqualTo(400 * 1024L) // Output from second compensated pass

            awaitComplete()
        }

        // Verify that transcoder was called twice (initial + compensated retry)
        assertThat(callCount).isEqualTo(2)
        assertThat(fakeAnalytics.completedConversions).hasSize(1)
    }

    @Test
    fun `compressVideo cleans up temporary staging files on error`() = runTest {
        val failingTranscoder = object : VideoTranscoder {
            override fun transcode(
                sourceFile: File,
                outputFile: File,
                encodingSpec: BitrateCalculator.VideoEncodingSpec
            ): Flow<AppResult<File>> = flow {
                // Write partial corrupted data then error out
                outputFile.writeBytes(ByteArray(256) { 0x00 })
                emit(AppResult.Error(ConversionError.IOError("Hardware encoder crashed")))
            }
        }

        val engine = DefaultMediaEngine(transcoder = failingTranscoder, analyticsTracker = fakeAnalytics)
        val request = ConversionRequest(
            sourceUris = listOf("file://${testSourceFile.absolutePath}"),
            conversionType = ConversionType.VIDEO_COMPRESS,
            targetMimeType = MimeType.Video.MP4,
            preset = Preset.WhatsAppVideo16MB
        )

        engine.compressVideo(request, tempDir).test {
            var item: AppResult<ConversionResult>? = null
            while (true) {
                item = awaitItem()
                if (item is AppResult.Error) break
            }
            assertThat(item).isInstanceOf(AppResult.Error::class.java)
            awaitComplete()
        }

        // Ensure no leftover .tmp files exist in tempDir
        val tmpFiles = tempDir.listFiles { _, name -> name.endsWith(".tmp") }
        assertThat(tmpFiles).isEmpty()
    }

    @Test
    fun `compressVideo emits error when transcoder fails`() = runTest {
        val failingTranscoder = object : VideoTranscoder {
            override fun transcode(
                sourceFile: File,
                outputFile: File,
                encodingSpec: BitrateCalculator.VideoEncodingSpec
            ): Flow<AppResult<File>> = flow {
                emit(AppResult.Error(ConversionError.IOError("Hardware encoder unavailable")))
            }
        }

        val engine = DefaultMediaEngine(transcoder = failingTranscoder, analyticsTracker = fakeAnalytics)
        val request = ConversionRequest(
            sourceUris = listOf("file://${testSourceFile.absolutePath}"),
            conversionType = ConversionType.VIDEO_COMPRESS,
            targetMimeType = MimeType.Video.MP4,
            preset = Preset.WhatsAppVideo16MB
        )

        engine.compressVideo(request, tempDir).test {
            val p1 = awaitItem()
            assertThat(p1.isProgress).isTrue()
            val p2 = awaitItem()
            assertThat(p2.isProgress).isTrue()

            val errorItem = awaitItem()
            assertThat(errorItem.isError).isTrue()
            val error = (errorItem as AppResult.Error).throwable
            assertThat(error).isInstanceOf(ConversionError.IOError::class.java)

            awaitComplete()
        }

        assertThat(fakeAnalytics.failedConversions).hasSize(1)
    }

    @Test
    fun `extractAudio emits error when source media file does not exist`() = runTest {
        val engine = DefaultMediaEngine(analyticsTracker = fakeAnalytics)
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

    private infix fun String.or(other: String): String = if (this.isNotEmpty()) this else other
}
