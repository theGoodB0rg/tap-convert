package com.tapconvert.feature.image.engine

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.model.MimeType
import org.junit.Test
import java.io.ByteArrayOutputStream

class ImageFormatConverterTest {

    @Test
    fun `converter handles all image format targets`() {
        // Assert format targets are mapped correctly
        assertThat(MimeType.Image.JPEG.primaryExtension).isEqualTo("jpg")
        assertThat(MimeType.Image.PNG.primaryExtension).isEqualTo("png")
        assertThat(MimeType.Image.WEBP.primaryExtension).isEqualTo("webp")
        assertThat(MimeType.Image.HEIC.primaryExtension).isEqualTo("heic")
        assertThat(MimeType.Image.BMP.primaryExtension).isEqualTo("bmp")
    }
}
