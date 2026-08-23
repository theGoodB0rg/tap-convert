package com.tapconvert.feature.image.engine

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.ConversionError
import com.tapconvert.core.model.MimeType
import com.tapconvert.core.model.TargetSize
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test

class ImageTargetCompressorTest {

    @Before
    fun setUp() {
        mockkObject(ImageFormatConverter)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `compress with smaller original size caps effective target and prevents inflation`() {
        val mockBitmap = mockk<android.graphics.Bitmap>(relaxed = true)
        val target200Kb = TargetSize.fromKilobytes(200) // 204,800 bytes
        val source40Kb = 40_000L

        // Return 50,000 bytes for high Q (which is > 40KB but < 200KB) and 35,000 bytes for low Q
        every { ImageFormatConverter.convert(any(), any(), any()) } answers {
            val q = thirdArg<Int>()
            if (q > 50) ByteArray(50_000) else ByteArray(35_000)
        }

        val result = ImageTargetCompressor.compress(
            sourceBitmap = mockBitmap,
            targetSize = target200Kb,
            targetFormat = MimeType.Image.JPEG,
            originalSizeBytes = source40Kb
        )

        assertThat(result.isSuccess).isTrue()
        val outcome = (result as AppResult.Success).data
        // Output bytes MUST be <= 40,000 bytes (capped by source size, not inflated to 200KB)
        assertThat(outcome.compressedBytes.size.toLong()).isAtMost(source40Kb)
    }

    @Test
    fun `compress with unachievable target size reports effective targetBytes in error`() {
        val mockBitmap = mockk<android.graphics.Bitmap>(relaxed = true)
        val target200Kb = TargetSize.fromKilobytes(200)
        val source20Kb = 20_000L

        // Always return 30,000 bytes (cannot reach 20KB)
        every { ImageFormatConverter.convert(any(), any(), any()) } returns ByteArray(30_000)

        val result = ImageTargetCompressor.compress(
            sourceBitmap = mockBitmap,
            targetSize = target200Kb,
            targetFormat = MimeType.Image.JPEG,
            originalSizeBytes = source20Kb,
            maxDownscaleIterations = 0
        )

        assertThat(result.isError).isTrue()
        val error = (result as AppResult.Error).throwable
        assertThat(error).isInstanceOf(ConversionError.TargetSizeUnachievable::class.java)
        val targetError = error as ConversionError.TargetSizeUnachievable
        assertThat(targetError.targetBytes).isEqualTo(source20Kb)
    }
}
