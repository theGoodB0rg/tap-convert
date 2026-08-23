package com.tapconvert.feature.pdf.engine

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.ConversionError
import com.tapconvert.core.model.ConversionQuality
import com.tapconvert.core.model.DimensionConstraint
import com.tapconvert.feature.image.engine.BitmapDecoder
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

object ImagesToPdfConverter {

    /**
     * Merges a list of image files into a single PDF document.
     * Uses sequential memory decoding and DPI-aware rasterization to maintain an O(1) memory footprint
     * and eliminate multi-megabyte PDF file bloat.
     */
    fun convert(
        imageFiles: List<File>,
        outputFile: File,
        pageSize: PdfPageSize = PdfPageSize.A4,
        marginPt: Float = 20f,
        autoRotatePage: Boolean = true,
        includeBranding: Boolean = true,
        quality: ConversionQuality = ConversionQuality.Medium,
        dimensionConstraint: DimensionConstraint = DimensionConstraint.None,
        onPageProgress: ((currentPage: Int, totalPages: Int) -> Unit)? = null
    ): AppResult<File> {
        if (imageFiles.isEmpty()) {
            return AppResult.Error(ConversionError.FileNotFound("No image files provided for PDF conversion"))
        }

        val targetDpi = when {
            quality.qualityPercent >= 90 -> 200
            quality.qualityPercent >= 60 -> 150
            else -> 100
        }

        val maxPagePt = when (pageSize) {
            is PdfPageSize.A4 -> maxOf(PdfPageSize.A4.WIDTH_PT, PdfPageSize.A4.HEIGHT_PT)
            is PdfPageSize.Letter -> maxOf(PdfPageSize.Letter.WIDTH_PT, PdfPageSize.Letter.HEIGHT_PT)
            is PdfPageSize.Custom -> maxOf(pageSize.widthPt, pageSize.heightPt)
            is PdfPageSize.FitImage -> 842
        }

        val dpiMaxPixels = (maxPagePt * (targetDpi / 72f)).roundToInt()
        val effectiveConstraint = if (dimensionConstraint is DimensionConstraint.None) {
            DimensionConstraint.MaxDimension(dpiMaxPixels)
        } else {
            dimensionConstraint
        }

        val pdfDocument = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(160, 100, 116, 139)
            textSize = 9f
            textAlign = Paint.Align.CENTER
        }
        var addedPages = 0

        try {
            val reservedBottom = if (includeBranding) 24f else 0f
            imageFiles.forEachIndexed { index, imageFile ->
                val pageNumber = index + 1
                onPageProgress?.invoke(pageNumber, imageFiles.size)

                if (!imageFile.exists() || imageFile.length() == 0L) {
                    // Skip or fail if corrupt
                    return AppResult.Error(ConversionError.FileNotFound(imageFile.absolutePath))
                }

                val bitmap = BitmapDecoder.decodeFile(imageFile, effectiveConstraint)
                    ?: return AppResult.Error(ConversionError.CorruptFile("Failed to decode image", imageFile.absolutePath))

                val layout = PdfPageLayoutCalculator.calculateLayout(
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    pageSize = pageSize,
                    marginPt = marginPt,
                    autoRotatePage = autoRotatePage,
                    reservedBottomMarginPt = reservedBottom
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

                if (includeBranding) {
                    val footerText = "Page $pageNumber of ${imageFiles.size} • Converted with TapConvert"
                    val footerY = layout.pageHeightPt - 8f
                    val footerX = layout.pageWidthPt / 2f
                    page.canvas.drawText(footerText, footerX, footerY, textPaint)
                }

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
