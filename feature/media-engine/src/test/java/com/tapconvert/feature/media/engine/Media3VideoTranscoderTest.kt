package com.tapconvert.feature.media.engine

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.TargetSize
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File

class Media3VideoTranscoderTest {

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "media3_transcoder_test")
    private val sourceFile = File(tempDir, "input_test.mp4")
    private val outputFile = File(tempDir, "output_test.mp4")

    @Before
    fun setUp() {
        tempDir.mkdirs()
        sourceFile.writeBytes(ByteArray(2048) { 0x33 })
        if (outputFile.exists()) {
            outputFile.delete()
        }
    }

    @Test
    fun `transcode with null context falls back to file copy and succeeds`() = runTest {
        val transcoder = Media3VideoTranscoder(context = null)
        val spec = BitrateCalculator.calculateTargetBitrate(
            targetSize = TargetSize.fromMegabytes(16),
            durationSeconds = 30.0
        )

        transcoder.transcode(sourceFile, outputFile, spec).test {
            val p1 = awaitItem()
            assertThat(p1.isProgress).isTrue()
            assertThat((p1 as AppResult.Progress).percentage).isEqualTo(100)

            val successItem = awaitItem()
            assertThat(successItem.isSuccess).isTrue()
            val resultFile = (successItem as AppResult.Success).data
            assertThat(resultFile.exists()).isTrue()
            assertThat(resultFile.length()).isEqualTo(sourceFile.length())

            awaitComplete()
        }
    }

    @Test
    fun `cancel can be called safely without active session`() {
        val transcoder = Media3VideoTranscoder(context = null)
        transcoder.cancel() // Should not throw
    }

    @Test
    fun `transcode with injected dispatcher executes successfully`() = runTest {
        val transcoder = Media3VideoTranscoder(
            context = null,
            mainDispatcher = coroutineContext[kotlinx.coroutines.CoroutineDispatcher] ?: kotlinx.coroutines.Dispatchers.Unconfined,
            looper = null
        )
        val spec = BitrateCalculator.calculateTargetBitrate(
            targetSize = TargetSize.fromMegabytes(8),
            durationSeconds = 15.0
        )

        transcoder.transcode(sourceFile, outputFile, spec).test {
            val progress = awaitItem()
            assertThat(progress.isProgress).isTrue()

            val success = awaitItem()
            assertThat(success.isSuccess).isTrue()
            assertThat((success as AppResult.Success).data.exists()).isTrue()

            awaitComplete()
        }
    }

    @Test
    fun `transcode with non-existent source file emits Error`() = runTest {
        val nonExistentSource = File(tempDir, "missing_${System.currentTimeMillis()}.mp4")
        val transcoder = Media3VideoTranscoder(context = null)
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
    fun `transcode delegates to fallback transcoder when media3 fails`() = runTest {
        var fallbackCalled = false
        val customFallback = object : VideoTranscoder {
            override fun transcode(
                sourceFile: File,
                outputFile: File,
                encodingSpec: BitrateCalculator.VideoEncodingSpec
            ): kotlinx.coroutines.flow.Flow<AppResult<File>> = kotlinx.coroutines.flow.flow {
                fallbackCalled = true
                outputFile.writeBytes(ByteArray(100) { 0x77 })
                emit(AppResult.Success(outputFile))
            }
        }

        val dummyContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationContext(): android.content.Context = this
        }
        val transcoder = Media3VideoTranscoder(
            context = dummyContext,
            mainDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            looper = null,
            fallbackTranscoder = customFallback
        )
        val spec = BitrateCalculator.calculateTargetBitrate(
            targetSize = TargetSize.fromMegabytes(16),
            durationSeconds = 30.0
        )

        transcoder.transcode(sourceFile, outputFile, spec).test {
            val item = awaitItem()
            assertThat(item.isSuccess).isTrue()
            awaitComplete()
        }
        assertThat(fallbackCalled).isTrue()
    }
}
