package com.tapconvert.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ConversionRequestTest {

    @Test
    fun `empty source URIs throw IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            ConversionRequest(
                sourceUris = emptyList(),
                conversionType = ConversionType.IMAGE_COMPRESS,
                targetMimeType = MimeType.Image.JPEG
            )
        }
    }

    @Test
    fun `fromPreset inherits all preset configuration parameters`() {
        val request = ConversionRequest.fromPreset(
            sourceUris = listOf("file:///test.mp4"),
            preset = Preset.WhatsAppVideo16MB,
            customOutputFileName = "whatsapp_out.mp4"
        )

        assertThat(request.conversionType).isEqualTo(ConversionType.VIDEO_COMPRESS)
        assertThat(request.targetMimeType).isEqualTo(MimeType.Video.MP4)
        assertThat(request.targetSize).isEqualTo(TargetSize.fromMegabytes(16))
        assertThat(request.dimensionConstraint).isEqualTo(DimensionConstraint.MaxDimension(1280))
        assertThat(request.outputFileName).isEqualTo("whatsapp_out.mp4")
        assertThat(request.preset).isEqualTo(Preset.WhatsAppVideo16MB)
    }
}
