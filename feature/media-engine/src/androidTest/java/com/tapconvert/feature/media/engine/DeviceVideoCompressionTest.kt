package com.tapconvert.feature.media.engine

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.ConversionQuality
import com.tapconvert.core.model.ConversionRequest
import com.tapconvert.core.model.ConversionResult
import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MimeType
import com.tapconvert.core.model.Preset
import com.tapconvert.core.model.TargetSize
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assume.assumeTrue
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
        outputDir = File(context.cacheDir, "test_output").apply {
            deleteRecursively()
            mkdirs()
        }
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
            targetSize = Preset.WhatsAppVideo16MB.targetSize
        )

        mediaEngine.compressVideo(request, outputDir).test(timeout = 60.seconds) {
            var finalFile: File? = null
            val progress = mutableListOf<Int>()
            while (true) {
                val item = awaitItem()
                println("MediaEngine item: $item")
                when (item) {
                    is AppResult.Progress -> {
                        progress += item.percentage
                        println("Compress progress: ${item.percentage}% - ${item.currentStep}")
                    }
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
            assertThat(finalFile.length()).isAtMost(testVideo1080p.length())
            assertThat(finalFile.length()).isAtMost(TargetSize.fromMegabytes(16).bytes)
            assertThat(progress.zipWithNext().all { (previous, current) -> current >= previous }).isTrue()
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
            val progress = mutableListOf<Int>()
            while (true) {
                val item = awaitItem()
                when (item) {
                    is AppResult.Progress -> {
                        progress += item.percentage
                        println("Quality compress progress: ${item.percentage}%")
                    }
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
            assertThat(finalFile.length()).isAtMost(testVideo1080p.length())
            assertThat(progress.zipWithNext().all { (previous, current) -> current >= previous }).isTrue()
            println("=== testMediaEngineCompressVideoWithQualitySlider SUCCESS, output size: ${finalFile.length()} bytes ===")
        }
    }

    @Test
    fun testHardwarePipelinedTranscoderDirectly() = runBlocking {
        println("=== testHardwarePipelinedTranscoderDirectly START ===")
        assertThat(testVideo1080p.exists()).isTrue()
        val outputFile = File(outputDir, "pipelined_output_1080p.mp4")
        if (outputFile.exists()) outputFile.delete()

        val transcoder = HardwarePipelinedTranscoder()
        val spec = BitrateCalculator.calculateTargetBitrate(
            targetSize = TargetSize.fromMegabytes(16),
            durationSeconds = 3.0,
            sourceSizeBytes = testVideo1080p.length(),
            sourceHeight = 1080
        )

        val startTime = System.currentTimeMillis()
        transcoder.transcode(testVideo1080p, outputFile, spec).test(timeout = 30.seconds) {
            var completed = false
            while (!completed) {
                val item = awaitItem()
                when (item) {
                    is AppResult.Progress -> println("Pipelined Progress: ${item.percentage}%")
                    is AppResult.Success -> {
                        val durationMs = System.currentTimeMillis() - startTime
                        println("Pipelined transcode completed in ${durationMs}ms!")
                        assertThat(item.data.exists()).isTrue()
                        assertThat(item.data.length()).isGreaterThan(0L)
                        completed = true
                    }
                    is AppResult.Error -> throw AssertionError("Pipelined transcode failed: ${item.message}", item.throwable)
                }
            }
            awaitComplete()
            println("=== testHardwarePipelinedTranscoderDirectly SUCCESS ===")
        }
    }

    @Test
    fun testStressLongVideoCompression() = runBlocking {
        val longVideo = File("/sdcard/Download/nigeria-60s.mp4")
        if (!longVideo.exists()) {
            println("Skipping stress test: /sdcard/Download/nigeria-60s.mp4 not found on device")
            return@runBlocking
        }

        println("=== testStressLongVideoCompression START (${longVideo.length() / 1024 / 1024}MB) ===")
        val mediaEngine = DefaultMediaEngine.create(context)
        val request = ConversionRequest(
            sourceUris = listOf("file://${longVideo.absolutePath}"),
            conversionType = ConversionType.VIDEO_COMPRESS,
            targetMimeType = MimeType.Video.MP4,
            preset = Preset.WhatsAppVideo16MB,
            targetSize = Preset.WhatsAppVideo16MB.targetSize
        )

        val startTime = System.currentTimeMillis()
        mediaEngine.compressVideo(request, outputDir).test(timeout = 120.seconds) {
            var finalFile: File? = null
            var terminalError: AppResult.Error? = null
            while (true) {
                val item = awaitItem()
                when (item) {
                    is AppResult.Progress -> println("Stress Progress: ${item.percentage}% - ${item.currentStep}")
                    is AppResult.Success -> {
                        val outputs = item.data.outputUris
                        assertThat(outputs).isNotEmpty()
                        finalFile = File(outputs.first())
                        break
                    }
                    is AppResult.Error -> {
                        terminalError = item
                        break
                    }
                }
            }
            awaitComplete()

            val durationMs = System.currentTimeMillis() - startTime
            if (terminalError != null) {
                assertThat(outputDir.listFiles { _, name -> name.endsWith(".tmp") }).isEmpty()
                println("=== stress compression safely refused in ${durationMs}ms: ${terminalError!!.message} ===")
            } else {
                assertThat(finalFile).isNotNull()
                assertThat(finalFile!!.exists()).isTrue()
                assertThat(finalFile!!.length()).isGreaterThan(0L)
                assertThat(finalFile!!.length()).isAtMost(longVideo.length())
                assertThat(finalFile!!.length()).isAtMost(TargetSize.fromMegabytes(16).bytes)
                println("=== testStressLongVideoCompression SUCCESS in ${durationMs}ms, output size: ${finalFile!!.length()} bytes ===")
            }
        }
    }

    @Test
    fun testLargeVideoNeverInflatesAndMeetsRequestedBudget() = runBlocking {
        val requestedFixture = InstrumentationRegistry.getArguments().getString("phoneVideoName")
        val requestedTargetMb = InstrumentationRegistry.getArguments().getString("phoneTargetMb")
            ?.toLongOrNull()
            ?.takeIf { it in 1L..512L }
            ?: 16L
        val largeVideo = listOfNotNull(
            requestedFixture?.let { File(context.cacheDir, it) },
            File("/sdcard/Download/queen-amina-story.mp4"),
            File("/sdcard/Download/Starship_Troopers_test.mp4"),
            File(context.cacheDir, "Starship_Troopers_test.mp4")
        ).firstOrNull { it.exists() }
        assumeTrue("No large phone fixture staged; provide -e phoneVideoName <name>", largeVideo != null)
        requireNotNull(largeVideo)
        assertThat(largeVideo.length()).isAtLeast(60L * 1024L * 1024L)
        val sourceInfo = requireNotNull(MediaMetadataRetrieverHelper.extractMediaInfo(largeVideo))

        val targetSize = TargetSize.fromMegabytes(requestedTargetMb)
        println(
            "=== testLargeVideoNeverInflatesAndMeetsRequestedBudget START " +
                "(${largeVideo.length()} bytes, target=${targetSize.formatted()}) ==="
        )
        val mediaEngine = DefaultMediaEngine.create(context)
        val request = ConversionRequest(
            sourceUris = listOf("file://${largeVideo.absolutePath}"),
            conversionType = ConversionType.VIDEO_COMPRESS,
            targetMimeType = MimeType.Video.MP4,
            preset = if (requestedTargetMb == 16L) Preset.WhatsAppVideo16MB else null,
            targetSize = targetSize
        )

        val progress = mutableListOf<Int>()
        val startTime = System.currentTimeMillis()
        mediaEngine.compressVideo(request, outputDir).test(timeout = 180.seconds) {
            var finalFile: File? = null
            var terminalError: AppResult.Error? = null
            while (true) {
                when (val item = awaitItem()) {
                    is AppResult.Progress -> {
                        progress += item.percentage
                        println("Large stress progress: ${item.percentage}% - ${item.currentStep}")
                    }
                    is AppResult.Success -> {
                        finalFile = File(item.data.outputUris.first())
                        break
                    }
                    is AppResult.Error -> {
                        terminalError = item
                        break
                    }
                }
            }
            awaitComplete()

            if (terminalError != null) {
                assertThat(outputDir.listFiles { _, name -> name.endsWith(".tmp") }).isEmpty()
                println(
                    "=== large compression safely refused in ${System.currentTimeMillis() - startTime}ms: " +
                        terminalError!!.message + " ==="
                )
            } else {
                assertThat(finalFile).isNotNull()
                assertThat(finalFile!!.length()).isGreaterThan(0L)
                assertThat(finalFile!!.length()).isAtMost(largeVideo.length())
                assertThat(finalFile!!.length()).isAtMost(targetSize.maxAllowedBytes)
                val outputInfo = requireNotNull(MediaMetadataRetrieverHelper.extractMediaInfo(finalFile!!))
                assertThat(outputInfo.durationMs)
                    .isAtLeast((sourceInfo.durationMs * 0.98).toLong())
                assertThat(outputInfo.durationMs)
                    .isAtMost((sourceInfo.durationMs * 1.02).toLong())
                if (sourceInfo.hasAudio) {
                    assertThat(outputInfo.hasAudio).isTrue()
                }
                assertThat(progress.zipWithNext().all { (previous, current) -> current >= previous }).isTrue()
                println(
                    "=== testLargeVideoNeverInflatesAndMeetsRequestedBudget SUCCESS " +
                        "in ${System.currentTimeMillis() - startTime}ms, output=${finalFile!!.length()} bytes ==="
                )
            }
        }
    }

    @Test
    fun testPhysicalPhoneLargeVideoActuallyCompressesWithin16MbBudget() = runBlocking {
        grantMediaReadPermissionForInstrumentation()
        val fixtureName = InstrumentationRegistry.getArguments()
            .getString("phoneVideoName")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9._-]+")) }
            ?: "yoruba_history.mp4"
        val phoneVideo = File(context.cacheDir, fixtureName)
        if (!phoneVideo.exists()) {
            println("Skipping physical-phone success test: ${phoneVideo.absolutePath} not found")
            return@runBlocking
        }

        assertThat(phoneVideo.length()).isAtLeast(20L * 1024L * 1024L)
        println("=== physical phone success test START (${phoneVideo.length()} bytes) ===")

        val mediaEngine = DefaultMediaEngine.create(context)
        val request = ConversionRequest(
            sourceUris = listOf("file://${phoneVideo.absolutePath}"),
            conversionType = ConversionType.VIDEO_COMPRESS,
            targetMimeType = MimeType.Video.MP4,
            preset = Preset.WhatsAppVideo16MB,
            targetSize = TargetSize.fromMegabytes(16)
        )
        val progress = mutableListOf<Int>()
        val startedAt = System.currentTimeMillis()

        mediaEngine.compressVideo(request, outputDir).test(timeout = 300.seconds) {
            var terminal: AppResult<ConversionResult>? = null
            while (terminal == null) {
                when (val item = awaitItem()) {
                    is AppResult.Progress -> progress += item.percentage
                    is AppResult.Success, is AppResult.Error -> terminal = item
                }
            }
            awaitComplete()

            // This is intentionally stricter than the safety tests: a real phone
            // must demonstrate a successful, bounded compression result.
            assertThat(terminal).isInstanceOf(AppResult.Success::class.java)
            val result = (terminal as AppResult.Success).data
            assertThat(result.outputUris).hasSize(1)
            val output = File(result.outputUris.single())
            assertThat(output.exists()).isTrue()
            assertThat(output.length()).isGreaterThan(0L)
            assertThat(output.length()).isAtMost(Preset.WhatsAppVideo16MB.targetSize!!.maxAllowedBytes)
            assertThat(output.length()).isAtMost(phoneVideo.length())
            assertThat(progress).isNotEmpty()
            assertThat(progress.zipWithNext().all { (previous, current) -> current >= previous }).isTrue()
            assertThat(outputDir.listFiles { _, name -> name.endsWith(".tmp") }).isEmpty()
            println(
                "=== physical phone success test PASS in ${System.currentTimeMillis() - startedAt}ms " +
                    "input=${phoneVideo.length()} output=${output.length()} ==="
            )
        }
    }

    @Test
    fun testPhysicalPhoneNormalVideoRetainsAudioAndDuration() = runBlocking {
        val phoneVideo = File(context.cacheDir, "yoruba_history.mp4")
        if (!phoneVideo.exists()) {
            println("Skipping media-integrity test: ${phoneVideo.absolutePath} not found")
            return@runBlocking
        }
        val sourceInfo = requireNotNull(MediaMetadataRetrieverHelper.extractMediaInfo(phoneVideo))
        assertThat(sourceInfo.hasAudio).isTrue()

        val mediaEngine = DefaultMediaEngine.create(context)
        val request = ConversionRequest(
            sourceUris = listOf("file://${phoneVideo.absolutePath}"),
            conversionType = ConversionType.VIDEO_COMPRESS,
            targetMimeType = MimeType.Video.MP4,
            preset = Preset.WhatsAppVideo16MB,
            targetSize = TargetSize.fromMegabytes(16)
        )

        mediaEngine.compressVideo(request, outputDir).test(timeout = 300.seconds) {
            var terminal: AppResult<ConversionResult>? = null
            while (terminal == null) {
                when (val item = awaitItem()) {
                    is AppResult.Progress -> Unit
                    is AppResult.Success, is AppResult.Error -> terminal = item
                }
            }
            awaitComplete()
            assertThat(terminal).isInstanceOf(AppResult.Success::class.java)
            val output = File((terminal as AppResult.Success).data.outputUris.single())
            val outputInfo = requireNotNull(MediaMetadataRetrieverHelper.extractMediaInfo(output))
            assertThat(outputInfo.hasAudio).isTrue()
            assertThat(outputInfo.durationMs).isAtLeast((sourceInfo.durationMs * 0.98).toLong())
            assertThat(outputInfo.durationMs).isAtMost((sourceInfo.durationMs * 1.02).toLong())
            assertThat(output.length()).isAtMost(Preset.WhatsAppVideo16MB.targetSize!!.maxAllowedBytes)
            assertThat(outputDir.listFiles { _, name -> name.endsWith(".tmp") }).isEmpty()
            println(
                "=== media integrity PASS duration=${sourceInfo.durationMs}->${outputInfo.durationMs} " +
                    "audio=${sourceInfo.hasAudio}->${outputInfo.hasAudio} bytes=${output.length()} ==="
            )
        }
    }

    @Test
    fun testPhysicalPhoneAudioReencodeMuxRetainsAudio() = runBlocking {
        val phoneVideo = File(context.cacheDir, "yoruba_history.mp4")
        if (!phoneVideo.exists()) {
            println("Skipping audio re-encode test: ${phoneVideo.absolutePath} not found")
            return@runBlocking
        }
        val sourceInfo = requireNotNull(MediaMetadataRetrieverHelper.extractMediaInfo(phoneVideo))
        assertThat(sourceInfo.hasAudio).isTrue()
        val request = ConversionRequest(
            sourceUris = listOf("file://${phoneVideo.absolutePath}"),
            conversionType = ConversionType.VIDEO_COMPRESS,
            targetMimeType = MimeType.Video.MP4,
            targetSize = TargetSize.fromMegabytes(4)
        )
        DefaultMediaEngine.create(context).compressVideo(request, outputDir).test(timeout = 240.seconds) {
            var terminal: AppResult<ConversionResult>? = null
            while (terminal == null) {
                terminal = when (val item = awaitItem()) {
                    is AppResult.Success -> item
                    is AppResult.Error -> item
                    is AppResult.Progress -> null
                }
            }
            awaitComplete()
            assertThat(terminal).isInstanceOf(AppResult.Success::class.java)
            val output = File((terminal as AppResult.Success).data.outputUris.single())
            val outputInfo = requireNotNull(MediaMetadataRetrieverHelper.extractMediaInfo(output))
            assertThat(outputInfo.hasAudio).isTrue()
            assertThat(outputInfo.durationMs).isAtLeast((sourceInfo.durationMs * 0.98).toLong())
            assertThat(outputInfo.durationMs).isAtMost((sourceInfo.durationMs * 1.02).toLong())
            assertThat(output.length()).isAtMost(request.targetSize!!.maxAllowedBytes)
            println("=== audio re-encode mux PASS bytes=${output.length()} duration=${outputInfo.durationMs} ===")
        }
    }

    private fun grantMediaReadPermissionForInstrumentation() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            "android.permission.READ_MEDIA_VIDEO"
        } else {
            "android.permission.READ_EXTERNAL_STORAGE"
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageNames = setOf(
            instrumentation.context.packageName,
            instrumentation.targetContext.packageName
        )
        packageNames.forEach { packageName ->
            runCatching {
                instrumentation.uiAutomation
                    .executeShellCommand("pm grant $packageName $permission")
                    .close()
            }
        }
    }
}
