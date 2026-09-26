package com.tapconvert.app.ui.preview

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.database.entity.ConversionRecordEntity
import com.tapconvert.core.model.ConversionResult
import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MediaCategory
import org.junit.Test
import java.io.File

class PreviewMediaItemTest {

    @Test
    fun `fromResult creates video preview item accurately`() {
        val result = ConversionResult(
            requestId = "req-1",
            conversionType = ConversionType.VIDEO_COMPRESS,
            outputUris = listOf("file:///sdcard/Download/compressed_video.mp4"),
            originalSizeBytes = 10_000_000L,
            outputSizeBytes = 4_000_000L,
            durationMs = 1500L
        )

        val record = ConversionRecordEntity(
            id = "req-1",
            conversionType = "VIDEO_COMPRESS",
            inputUris = listOf("file:///sdcard/Download/original_video.mp4"),
            outputUris = result.outputUris,
            originalSizeBytes = 10_000_000L,
            outputSizeBytes = 4_000_000L,
            savedBytes = 6_000_000L,
            durationMs = 1500L
        )

        val item = PreviewMediaItem.fromResult(result, record)
        assertThat(item).isNotNull()
        assertThat(item!!.category).isEqualTo(MediaCategory.VIDEO)
        assertThat(item.fileName).isEqualTo("compressed_video.mp4")
        assertThat(item.percentageSaved).isEqualTo(60)
        assertThat(item.originalUriString).isEqualTo("file:///sdcard/Download/original_video.mp4")
    }

    @Test
    fun `fromRecord creates document preview item for pdf correctly`() {
        val record = ConversionRecordEntity(
            id = "req-pdf",
            conversionType = "IMAGES_TO_PDF",
            inputUris = listOf("file:///sdcard/Download/img1.jpg", "file:///sdcard/Download/img2.jpg"),
            outputUris = listOf("file:///sdcard/Download/assembled.pdf"),
            originalSizeBytes = 2_000_000L,
            outputSizeBytes = 1_200_000L,
            savedBytes = 800_000L,
            durationMs = 800L
        )

        val item = PreviewMediaItem.fromRecord(record)
        assertThat(item).isNotNull()
        assertThat(item!!.category).isEqualTo(MediaCategory.DOCUMENT)
        assertThat(item.fileName).isEqualTo("assembled.pdf")
    }
}
