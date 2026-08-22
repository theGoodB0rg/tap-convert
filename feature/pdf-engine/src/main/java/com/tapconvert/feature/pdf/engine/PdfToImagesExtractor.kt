package com.tapconvert.feature.pdf.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.ConversionError
import com.tapconvert.core.model.MimeType
import com.tapconvert.feature.image.engine.ImageFormatConverter
import java.io.File
import kotlin.math.roundToInt

object PdfToImagesExtractor {

    /**
     * Extracts pages of a PDF document as high-resolution images.
     */
    fun extract(
        pdfFile: File,
        outputDirectory: File,
        targetFormat: MimeType.Image = MimeType.Image.JPEG,
        qualityPercent: Int = 90,
        dpiScale: Float = 2.0f,
        onPageProgress: ((currentPage: Int, totalPages: Int) -> Unit)? = null
    ): AppResult<List<File>> {
        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            return AppResult.Error(ConversionError.FileNotFound(pdfFile.absolutePath))
        }

        outputDirectory.mkdirs()
        val generatedFiles = mutableListOf<File>()

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = try {
                PdfRenderer(pfd)
            } catch (sec: SecurityException) {
                return AppResult.Error(ConversionError.EncryptedPdf("Password-protected PDF files cannot be extracted"))
            } catch (t: Throwable) {
                return AppResult.Error(ConversionError.CorruptFile("Invalid or corrupt PDF document: ${t.message}", pdfFile.absolutePath))
            }

            val pageCount = renderer.pageCount
            if (pageCount == 0) {
                return AppResult.Error(ConversionError.CorruptFile("PDF document has 0 pages", pdfFile.absolutePath))
            }

            val baseName = pdfFile.nameWithoutExtension
            val ext = targetFormat.primaryExtension

            for (pageIndex in 0 until pageCount) {
                val pageNumber = pageIndex + 1
                onPageProgress?.invoke(pageNumber, pageCount)

                val page = renderer.openPage(pageIndex)

                val renderW = (page.width * dpiScale).roundToInt().coerceAtLeast(1)
                val renderH = (page.height * dpiScale).roundToInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val pageFileName = com.tapconvert.core.common.ExportFileNameGenerator.generate(
                    originalName = "${baseName}_page_$pageNumber",
                    extension = ext,
                    fallbackName = "PDF_Page"
                )
                val pageFile = File(outputDirectory, pageFileName)

                ImageFormatConverter.convertToFile(bitmap, targetFormat, qualityPercent, pageFile)
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }

                generatedFiles.add(pageFile)
            }

            return AppResult.Success(generatedFiles)
        } catch (t: Throwable) {
            return AppResult.Error(ConversionError.IOError("Error extracting PDF pages: ${t.message}", t))
        } finally {
            try {
                renderer?.close()
            } catch (_: Throwable) {}
            try {
                pfd?.close()
            } catch (_: Throwable) {}
        }
    }
}
