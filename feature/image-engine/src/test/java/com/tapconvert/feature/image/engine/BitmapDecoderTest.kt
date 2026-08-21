package com.tapconvert.feature.image.engine

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.model.DimensionConstraint
import org.junit.Test
import java.io.File

class BitmapDecoderTest {

    @Test
    fun `calculateInSampleSize scales down in powers of two`() {
        // Equal dimensions
        assertThat(BitmapDecoder.calculateInSampleSize(1000, 1000, 1000, 1000)).isEqualTo(1)

        // 4x larger
        assertThat(BitmapDecoder.calculateInSampleSize(4000, 4000, 1000, 1000)).isEqualTo(4)

        // 8x larger
        assertThat(BitmapDecoder.calculateInSampleSize(8000, 4000, 1000, 500)).isEqualTo(8)

        // Smaller than requested
        assertThat(BitmapDecoder.calculateInSampleSize(500, 500, 1000, 1000)).isEqualTo(1)

        // Invalid requests
        assertThat(BitmapDecoder.calculateInSampleSize(0, 0, 100, 100)).isEqualTo(1)
        assertThat(BitmapDecoder.calculateInSampleSize(100, 100, 0, 0)).isEqualTo(1)
    }

    @Test
    fun `decodeByteArray returns null for empty or invalid byte array`() {
        val result = BitmapDecoder.decodeByteArray(ByteArray(0))
        assertThat(result).isNull()

        val corruptResult = BitmapDecoder.decodeByteArray(byteArrayOf(0x00, 0x01, 0x02))
        assertThat(corruptResult).isNull()
    }

    @Test
    fun `decodeFile returns null for non-existent file`() {
        val result = BitmapDecoder.decodeFile(File("non_existent_image.jpg"))
        assertThat(result).isNull()
    }
}
