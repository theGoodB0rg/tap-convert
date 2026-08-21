package com.tapconvert.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConversionResultTest {

    @Test
    fun `calculates compression ratio, bytes saved and percentage saved accurately`() {
        val result = ConversionResult(
            requestId = "req-123",
            conversionType = ConversionType.IMAGE_COMPRESS,
            outputUris = listOf("file:///out.jpg"),
            originalSizeBytes = 10_000_000L, // 10MB
            outputSizeBytes = 2_000_000L,   // 2MB (80% savings)
            durationMs = 120L
        )

        assertThat(result.compressionRatio).isEqualTo(0.2f)
        assertThat(result.bytesSaved).isEqualTo(8_000_000L)
        assertThat(result.percentageSaved).isEqualTo(80)
    }

    @Test
    fun `handles zero original size gracefully without division by zero`() {
        val result = ConversionResult(
            requestId = "req-zero",
            conversionType = ConversionType.IMAGE_COMPRESS,
            outputUris = listOf("file:///out.jpg"),
            originalSizeBytes = 0L,
            outputSizeBytes = 0L,
            durationMs = 10L
        )

        assertThat(result.compressionRatio).isEqualTo(1.0f)
        assertThat(result.bytesSaved).isEqualTo(0L)
        assertThat(result.percentageSaved).isEqualTo(0)
    }
}
