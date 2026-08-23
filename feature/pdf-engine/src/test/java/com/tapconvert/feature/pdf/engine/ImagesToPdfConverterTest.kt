package com.tapconvert.feature.pdf.engine

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.ConversionQuality
import com.tapconvert.core.model.DimensionConstraint
import com.tapconvert.feature.image.engine.BitmapDecoder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class ImagesToPdfConverterTest {

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "images_to_pdf_test")
    private val testImage = File(tempDir, "test_input.jpg")
    private val testOutput = File(tempDir, "test_output.pdf")

    @Before
    fun setUp() {
        tempDir.mkdirs()
        testImage.writeBytes(ByteArray(1024) { 0x11 })
        if (testOutput.exists()) testOutput.delete()
        mockkObject(BitmapDecoder)
    }

    @After
    fun tearDown() {
        unmockkAll()
        testImage.delete()
        testOutput.delete()
    }

    @Test
    fun `convert with High quality applies 200 DPI dimension constraint to BitmapDecoder`() {
        val constraintSlot = slot<DimensionConstraint>()
        val mockBitmap = mockk<android.graphics.Bitmap>(relaxed = true)
        every { mockBitmap.width } returns 1650
        every { mockBitmap.height } returns 2330
        every { BitmapDecoder.decodeFile(any(), capture(constraintSlot)) } returns mockBitmap

        ImagesToPdfConverter.convert(
            imageFiles = listOf(testImage),
            outputFile = testOutput,
            pageSize = PdfPageSize.A4,
            quality = ConversionQuality.High // 90% -> 200 DPI -> max dimension ~2339px
        )

        assertThat(constraintSlot.isCaptured).isTrue()
        val captured = constraintSlot.captured
        assertThat(captured).isInstanceOf(DimensionConstraint.MaxDimension::class.java)
        val maxDim = (captured as DimensionConstraint.MaxDimension).maxPixels
        // 842 pt * (200 / 72) = 2339 px
        assertThat(maxDim).isEqualTo(2339)
    }

    @Test
    fun `convert with Low quality applies 100 DPI dimension constraint to BitmapDecoder`() {
        val constraintSlot = slot<DimensionConstraint>()
        val mockBitmap = mockk<android.graphics.Bitmap>(relaxed = true)
        every { mockBitmap.width } returns 825
        every { mockBitmap.height } returns 1170
        every { BitmapDecoder.decodeFile(any(), capture(constraintSlot)) } returns mockBitmap

        ImagesToPdfConverter.convert(
            imageFiles = listOf(testImage),
            outputFile = testOutput,
            pageSize = PdfPageSize.A4,
            quality = ConversionQuality.Low // 30% -> 100 DPI -> max dimension ~1169px
        )

        assertThat(constraintSlot.isCaptured).isTrue()
        val captured = constraintSlot.captured
        assertThat(captured).isInstanceOf(DimensionConstraint.MaxDimension::class.java)
        val maxDim = (captured as DimensionConstraint.MaxDimension).maxPixels
        // 842 pt * (100 / 72) = 1169 px
        assertThat(maxDim).isEqualTo(1169)
    }

    @Test
    fun `convert with Balanced quality applies 150 DPI dimension constraint to BitmapDecoder`() {
        val constraintSlot = slot<DimensionConstraint>()
        val mockBitmap = mockk<android.graphics.Bitmap>(relaxed = true)
        every { mockBitmap.width } returns 1240
        every { mockBitmap.height } returns 1754
        every { BitmapDecoder.decodeFile(any(), capture(constraintSlot)) } returns mockBitmap

        ImagesToPdfConverter.convert(
            imageFiles = listOf(testImage),
            outputFile = testOutput,
            pageSize = PdfPageSize.A4,
            quality = ConversionQuality.Medium // 70% -> 150 DPI -> max dimension ~1754px
        )

        assertThat(constraintSlot.isCaptured).isTrue()
        val captured = constraintSlot.captured
        assertThat(captured).isInstanceOf(DimensionConstraint.MaxDimension::class.java)
        val maxDim = (captured as DimensionConstraint.MaxDimension).maxPixels
        // 842 pt * (150 / 72) = 1754 px
        assertThat(maxDim).isEqualTo(1754)
    }

    @Test
    fun `convert with explicit custom DimensionConstraint preserves user constraint`() {
        val constraintSlot = slot<DimensionConstraint>()
        val mockBitmap = mockk<android.graphics.Bitmap>(relaxed = true)
        every { mockBitmap.width } returns 500
        every { mockBitmap.height } returns 500
        every { BitmapDecoder.decodeFile(any(), capture(constraintSlot)) } returns mockBitmap

        val explicitConstraint = DimensionConstraint.MaxDimension(600)
        ImagesToPdfConverter.convert(
            imageFiles = listOf(testImage),
            outputFile = testOutput,
            pageSize = PdfPageSize.A4,
            quality = ConversionQuality.High,
            dimensionConstraint = explicitConstraint
        )

        assertThat(constraintSlot.isCaptured).isTrue()
        assertThat(constraintSlot.captured).isEqualTo(explicitConstraint)
    }
}
