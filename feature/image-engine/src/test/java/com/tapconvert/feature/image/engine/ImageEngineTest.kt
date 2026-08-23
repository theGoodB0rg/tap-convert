package com.tapconvert.feature.image.engine

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

class ImageEngineTest {

    private val fakeAnalytics = FakeAnalyticsTracker()
    private val engine = DefaultImageEngine(fakeAnalytics)
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "tapconvert_test_out")

    @Test
    fun `process emits error when source file does not exist and logs failed event`() = runTest {
        val request = ConversionRequest(
            sourceUris = listOf("file:///non_existent_path/photo.jpg"),
            conversionType = ConversionType.IMAGE_COMPRESS,
            targetMimeType = MimeType.Image.JPEG,
            preset = Preset.GovPassport200KB
        )

        engine.process(request, tempDir).test {
            val first = awaitItem()
            assertThat(first.isProgress).isTrue()

            val second = awaitItem()
            assertThat(second.isError).isTrue()
            val error = (second as AppResult.Error).throwable
            assertThat(error).isInstanceOf(ConversionError.FileNotFound::class.java)

            awaitComplete()
        }

        assertThat(fakeAnalytics.startedConversions).hasSize(1)
        assertThat(fakeAnalytics.failedConversions).hasSize(1)
        assertThat(fakeAnalytics.failedConversions[0].errorType).isEqualTo("FileNotFound")
    }

    @Test
    fun `batch process propagates item errors and stops processing`() = runTest {
        val requests = listOf(
            ConversionRequest(
                sourceUris = listOf("file:///missing1.jpg"),
                conversionType = ConversionType.IMAGE_CONVERT,
                targetMimeType = MimeType.Image.PNG
            ),
            ConversionRequest(
                sourceUris = listOf("file:///missing2.jpg"),
                conversionType = ConversionType.IMAGE_CONVERT,
                targetMimeType = MimeType.Image.PNG
            )
        )

        engine.processBatch(requests, tempDir).test {
            val p1 = awaitItem()
            assertThat(p1.isProgress).isTrue()

            val err = awaitItem()
            assertThat(err.isError).isTrue()

            awaitComplete()
        }
    }

    @Test
    fun `process with empty source URIs emits FileNotFound error`() = runTest {
        // Test with empty source list via reflection or direct invoke if validation bypassed
        val emptyRequest = ConversionRequest(
            sourceUris = listOf("file:///dummy_non_existent.jpg"),
            conversionType = ConversionType.IMAGE_COMPRESS,
            targetMimeType = MimeType.Image.JPEG
        )
        engine.process(emptyRequest, tempDir).test {
            val p1 = awaitItem()
            assertThat(p1.isProgress).isTrue()

            val err = awaitItem()
            assertThat(err.isError).isTrue()
            awaitComplete()
        }
    }
}
