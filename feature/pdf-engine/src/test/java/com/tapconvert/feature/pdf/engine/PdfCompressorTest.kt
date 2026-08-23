package com.tapconvert.feature.pdf.engine

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.ConversionError
import com.tapconvert.core.model.ConversionQuality
import com.tapconvert.core.model.DimensionConstraint
import com.tapconvert.core.model.TargetSize
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PdfCompressorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var outputDir: File
    private lateinit var validPdfFile: File

    class FakePdfPageRenderer(
        override val pageCount: Int,
        private val shouldFailOnPage: Int = -1,
        private val widthPt: Int = 595,
        private val heightPt: Int = 842
    ) : PdfPageRenderer {
        var isClosed = false
        val renderedDpis = mutableListOf<Float>()

        override fun getPageDimensions(pageIndex: Int): Pair<Int, Int> = widthPt to heightPt

        override fun renderPage(pageIndex: Int, targetDpi: Float, maxDimension: DimensionConstraint): Bitmap? {
            if (pageIndex == shouldFailOnPage) return null
            renderedDpis.add(targetDpi)
            val mockBitmap = mockk<Bitmap>(relaxed = true)
            every { mockBitmap.width } returns 500
            every { mockBitmap.height } returns 500
            return mockBitmap
        }

        override fun close() {
            isClosed = true
        }
    }

    @Before
    fun setUp() {
        outputDir = tempFolder.newFolder("pdf_compress_out")
        validPdfFile = tempFolder.newFile("sample.pdf").apply {
            writeBytes(ByteArray(1024) { 0x22 })
        }
    }

    @Test
    fun `compress emits error when source PDF does not exist`() {
        val nonExistent = File(tempFolder.root, "missing.pdf")
        val outputFile = File(outputDir, "out.pdf")

        val result = PdfCompressor.compress(
            pdfFile = nonExistent,
            outputFile = outputFile
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val err = (result as AppResult.Error).throwable
        assertThat(err).isInstanceOf(ConversionError.FileNotFound::class.java)
    }

    @Test
    fun `compress emits error when renderer throws CorruptFile`() {
        val outputFile = File(outputDir, "out_corrupt.pdf")

        val result = PdfCompressor.compress(
            pdfFile = validPdfFile,
            outputFile = outputFile,
            rendererFactory = { throw ConversionError.CorruptFile("Corrupt PDF", it.absolutePath) }
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val err = (result as AppResult.Error).throwable
        assertThat(err).isInstanceOf(ConversionError.CorruptFile::class.java)
    }

    @Test
    fun `compress emits EncryptedPdf error on password protected PDF`() {
        val outputFile = File(outputDir, "out_encrypted.pdf")

        val result = PdfCompressor.compress(
            pdfFile = validPdfFile,
            outputFile = outputFile,
            rendererFactory = { throw ConversionError.EncryptedPdf("Password protected") }
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val err = (result as AppResult.Error).throwable
        assertThat(err).isInstanceOf(ConversionError.EncryptedPdf::class.java)
    }

    @Test
    fun `compress successfully processes multi-page PDF document and tracks progress`() {
        val outputFile = File(outputDir, "out_multi.pdf")
        val progressList = mutableListOf<Pair<Int, Int>>()
        val fakeRenderer = FakePdfPageRenderer(pageCount = 3)

        val result = PdfCompressor.compress(
            pdfFile = validPdfFile,
            outputFile = outputFile,
            quality = ConversionQuality.Medium,
            rendererFactory = { fakeRenderer },
            onPageProgress = { current, total ->
                progressList.add(current to total)
            }
        )

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0L)
        assertThat(progressList).hasSize(3)
        assertThat(progressList.last()).isEqualTo(3 to 3)
        assertThat(fakeRenderer.isClosed).isTrue()
    }

    @Test
    fun `compress with target size and custom quality calculates adaptive DPI properly`() {
        val outputFile = File(outputDir, "out_target.pdf")
        val fakeRenderer = FakePdfPageRenderer(pageCount = 2)

        val result = PdfCompressor.compress(
            pdfFile = validPdfFile,
            outputFile = outputFile,
            quality = ConversionQuality.Custom(30),
            targetSize = TargetSize.fromKilobytes(100), // 100KB for 2 pages = 50KB/page -> 96 DPI
            includeBranding = false,
            rendererFactory = { fakeRenderer }
        )

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat(outputFile.exists()).isTrue()
        assertThat(fakeRenderer.renderedDpis).hasSize(2)
        assertThat(fakeRenderer.renderedDpis.first()).isEqualTo(120f)
    }
}
