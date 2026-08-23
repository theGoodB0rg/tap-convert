package com.tapconvert.feature.media.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.ConversionQuality
import com.tapconvert.core.model.ConversionRequest
import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MimeType
import com.tapconvert.core.model.Preset
import com.tapconvert.core.model.TargetSize
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class DeviceVideoCompressionTest {

    private lateinit var context: Context
    private lateinit var outputDir: File
    private lateinit var testVideo1080p: File
    private lateinit var testVideo720p: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        outputDir = File(context.cacheDir, "test_output").apply { mkdirs() }
        testVideo1080p = File("/sdcard/Movies/test_1080p.mp4")
        testVideo720p = File("/sdcard/Movies/test_720p.mp4")
    }

    @Test
    fun testNativeTranscoderDirectly() = runBlocking {
        println("=== testNativeTranscoderDirectly START ===")
        assertThat(testVideo1080p.exists()).isTrue()
        val outputFile = File(outputDir, "native_output_1080p.mp4")
        if (outputFile.exists()) outputFile.delete()

        val transcoder = NativeVideoTranscoder()
        val spec = BitrateCalculator.calculateTargetBitrate(
            targetSize = TargetSize.fromMegabytes(16),
            durationSeconds = 3.0,
            sourceSizeBytes = testVideo1080p.length(),
            sourceHeight = 1080
        )

        transcoder.transcode(testVideo1080p, outputFile, spec).test(timeout = 30.seconds) {
            var completed = false
            while (!completed) {
                val item = awaitItem()
                println("NativeTranscoder item: $item")
                when (item) {
                    is AppResult.Progress -> println("Progress: ${item.percentage}%")
                    is AppResult.Success -> {
                        assertThat(item.data.exists()).isTrue()
                        assertThat(item.data.length()).isGreaterThan(0L)
                        completed = true
                    }
                    is AppResult.Error -> throw AssertionError("Native transcode failed: ${item.message}", item.throwable)
                }
            }
            awaitComplete()
            println("=== testNativeTranscoderDirectly SUCCESS ===")
        }
    }

    @Test
    fun testMediaEngineCompressVideoWithPreset() = runBlocking {
        println("=== testMediaEngineCompressVideoWithPreset START ===")
        assertThat(testVideo1080p.exists()).isTrue()

        val mediaEngine = DefaultMediaEngine.create(context)
        val request = ConversionRequest(
            sourceUris = listOf("file://${testVideo1080p.absolutePath}"),
            conversionType = ConversionType.VIDEO_COMPRESS,
            targetMimeType = MimeType.Video.MP4,
            preset = Preset.WhatsAppVideo16MB,
            targetSize = TargetSize.fromMegabytes(16)
        )

        mediaEngine.compressVideo(request, outputDir).test(timeout = 60.seconds) {
            var finalFile: File? = null
            while (true) {
                val item = awaitItem()
                println("MediaEngine item: $item")
                when (item) {
                    is AppResult.Progress -> println("Compress progress: ${item.percentage}% - ${item.currentStep}")
                    is AppResult.Success -> {
                        val outputs = item.data.outputUris
                        assertThat(outputs).isNotEmpty()
                        finalFile = File(outputs.first())
                        break
                    }
                    is AppResult.Error -> throw AssertionError("MediaEngine compress failed: ${item.message}", item.throwable)
                }
            }
            awaitComplete()

            assertThat(finalFile).isNotNull()
            assertThat(finalFile!!.exists()).isTrue()
            assertThat(finalFile.length()).isGreaterThan(0L)
            println("=== testMediaEngineCompressVideoWithPreset SUCCESS, output size: ${finalFile.length()} bytes ===")
        }
    }

    @Test
    fun testMediaEngineCompressVideoWithQualitySlider() = runBlocking {
        println("=== testMediaEngineCompressVideoWithQualitySlider START ===")
        assertThat(testVideo1080p.exists()).isTrue()

        val mediaEngine = DefaultMediaEngine.create(context)
        val request = ConversionRequest(
            sourceUris = listOf("file://${testVideo1080p.absolutePath}"),
            conversionType = ConversionType.VIDEO_COMPRESS,
            targetMimeType = MimeType.Video.MP4,
            quality = ConversionQuality.Medium
        )

        mediaEngine.compressVideo(request, outputDir).test(timeout = 60.seconds) {
            var finalFile: File? = null
            while (true) {
                val item = awaitItem()
                when (item) {
                    is AppResult.Progress -> println("Quality compress progress: ${item.percentage}%")
                    is AppResult.Success -> {
                        val outputs = item.data.outputUris
                        assertThat(outputs).isNotEmpty()
                        finalFile = File(outputs.first())
                        break
                    }
                    is AppResult.Error -> throw AssertionError("Quality compress failed: ${item.message}", item.throwable)
                }
            }
            awaitComplete()

            assertThat(finalFile).isNotNull()
            assertThat(finalFile!!.exists()).isTrue()
            assertThat(finalFile.length()).isGreaterThan(0L)
            println("=== testMediaEngineCompressVideoWithQualitySlider SUCCESS, output size: ${finalFile.length()} bytes ===")
        }
    }
}
