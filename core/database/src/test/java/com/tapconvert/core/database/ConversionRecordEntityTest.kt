package com.tapconvert.core.database

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.database.entity.ConversionRecordEntity
import com.tapconvert.core.model.ConversionResult
import com.tapconvert.core.model.ConversionType
import org.junit.Test

class ConversionRecordEntityTest {

    @Test
    fun `savings percentage calculated accurately`() {
        val record = ConversionRecordEntity(
            conversionType = ConversionType.IMAGE_COMPRESS.name,
            inputUris = listOf("file:///photo.jpg"),
            outputUris = listOf("file:///photo_compressed.jpg"),
            originalSizeBytes = 10_000_000L,
            outputSizeBytes = 2_000_000L,
            savedBytes = 8_000_000L,
            durationMs = 250L
        )

        assertThat(record.savingsPercentage).isWithin(0.01f).of(80.0f)
    }

    @Test
    fun `fromDomain maps domain result correctly`() {
        val result = ConversionResult(
            requestId = "req_123",
            conversionType = ConversionType.PDF_TO_IMAGES,
            outputUris = listOf("file:///p1.jpg", "file:///p2.jpg"),
            originalSizeBytes = 5_000_000L,
            outputSizeBytes = 3_000_000L,
            durationMs = 450L,
            metadata = mapOf("pageCount" to "2")
        )

        val entity = ConversionRecordEntity.fromDomain(
            result = result,
            inputUris = listOf("file:///doc.pdf"),
            presetId = "pdf_preset",
            isFavorited = true
        )

        assertThat(entity.id).isEqualTo("req_123")
        assertThat(entity.conversionType).isEqualTo(ConversionType.PDF_TO_IMAGES.name)
        assertThat(entity.savedBytes).isEqualTo(2_000_000L)
        assertThat(entity.isFavorited).isTrue()
        assertThat(entity.metadata["pageCount"]).isEqualTo("2")
    }
}
