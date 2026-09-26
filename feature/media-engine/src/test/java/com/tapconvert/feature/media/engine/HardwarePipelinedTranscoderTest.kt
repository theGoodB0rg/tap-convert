package com.tapconvert.feature.media.engine

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.TargetSize
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File

class HardwarePipelinedTranscoderTest {

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "pipelined_transcoder_test")
    private val sourceFile = File(tempDir, "input_test.mp4")
    private val outputFile = File(tempDir, "output_test.mp4")

    @Before
    fun setUp() {
        tempDir.mkdirs()
        sourceFile.writeBytes(ByteArray(2048) { 0x44 })
        if (outputFile.exists()) {
            outputFile.delete()
        }
    }

    @Test
    fun `cancel can be called safely without active session`() {
        val transcoder = HardwarePipelinedTranscoder()
        transcoder.cancel() // Should not throw
    }

    @Test
    fun `transcode with non-existent source file emits Error`() = runTest {
        val nonExistentSource = File(tempDir, "missing_${System.currentTimeMillis()}.mp4")
        val transcoder = HardwarePipelinedTranscoder()
        val spec = BitrateCalculator.calculateTargetBitrate(
            targetSize = TargetSize.fromMegabytes(16),
            durationSeconds = 30.0
        )

        transcoder.transcode(nonExistentSource, outputFile, spec).test {
            val item = awaitItem()
            assertThat(item.isError).isTrue()
            awaitComplete()
        }
    }

    @Test
    fun `transcode delegates to fallback transcoder in JVM test environment`() = runTest {
        var fallbackCalled = false
        val customFallback = object : VideoTranscoder {
            override fun transcode(
                sourceFile: File,
                outputFile: File,
                encodingSpec: BitrateCalculator.VideoEncodingSpec
            ) = flow {
                fallbackCalled = true
                outputFile.writeBytes(ByteArray(100) { 0x88.toByte() })
                emit(AppResult.Success(outputFile))
            }
        }

        val transcoder = HardwarePipelinedTranscoder(fallbackTranscoder = customFallback)
        val spec = BitrateCalculator.calculateTargetBitrate(
            targetSize = TargetSize.fromMegabytes(16),
            durationSeconds = 30.0
        )

        transcoder.transcode(sourceFile, outputFile, spec).test {
            var lastItem: AppResult<File>? = null
            while (true) {
                val item = awaitItem()
                lastItem = item
                if (item is AppResult.Success || item is AppResult.Error) break
            }
            assertThat(lastItem).isNotNull()
            assertThat(lastItem).isInstanceOf(AppResult.Success::class.java)
            awaitComplete()
        }
        assertThat(fallbackCalled).isTrue()
    }
}
