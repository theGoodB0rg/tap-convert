package com.tapconvert.feature.pdf.engine

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.ConversionError
import com.tapconvert.core.model.DimensionConstraint
import com.tapconvert.feature.image.engine.BitmapDecoder
import java.io.File
import java.io.FileOutputStream

object ImagesToPdfConverter {

    /**
     * Merges a list of image files into a single PDF document.
     * Uses sequential memory decoding to maintain an O(1) memory footprint.
     */
    fun convert(
        imageFiles: List<File>,
        outputFile: File,
        pageSize: PdfPageSize = PdfPageSize.A4,
        marginPt: Float = 20f,
        autoRotatePage: Boolean = true,
        dimensionConstraint: DimensionConstraint = DimensionConstraint.None,
        onPageProgress: ((currentPage: Int, totalPages: Int) -> Unit)? = null
    ): AppResult<File> {
        if (imageFiles.isEmpty()) {
            return AppResult.Error(ConversionError.FileNotFound("No image files provided for PDF conversion"))
        }

        val pdfDocument = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        var addedPages = 0

        try {
            imageFiles.forEachIndexed { index, imageFile ->
                val pageNumber = index + 1
                onPageProgress?.invoke(pageNumber, imageFiles.size)

                if (!imageFile.exists() || imageFile.length() == 0L) {
                    // Skip or fail if corrupt
                    return AppResult.Error(ConversionError.FileNotFound(imageFile.absolutePath))
                }

                val bitmap = BitmapDecoder.decodeFile(imageFile, dimensionConstraint)
                    ?: return AppResult.Error(ConversionError.CorruptFile("Failed to decode image", imageFile.absolutePath))

                val layout = PdfPageLayoutCalculator.calculateLayout(
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    pageSize = pageSize,
                    marginPt = marginPt,
                    autoRotatePage = autoRotatePage
                )

                val pageInfo = PdfDocument.PageInfo.Builder(
                    layout.pageWidthPt,
                    layout.pageHeightPt,
                    pageNumber
                ).create()

                val page = pdfDocument.startPage(pageInfo)
                val destRect = RectF(
                    layout.destLeft,
                    layout.destTop,
                    layout.destRight,
                    layout.destBottom
                )

                page.canvas.drawBitmap(bitmap, null, destRect, paint)
                pdfDocument.finishPage(page)
                addedPages++

                // Immediately recycle decoded bitmap to avoid accumulating memory across multiple pages
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }

            if (addedPages == 0) {
                return AppResult.Error(ConversionError.CorruptFile("No valid image pages could be rendered into PDF"))
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }

            return AppResult.Success(outputFile)
        } catch (t: Throwable) {
            return AppResult.Error(ConversionError.IOError("Failed writing PDF document: ${t.message}", t))
        } finally {
            try {
                pdfDocument.close()
            } catch (_: Throwable) {
                // Ignore document close errors on teardown
            }
        }
    }
}
