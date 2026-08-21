package com.tapconvert.feature.pdf.engine

import kotlin.math.roundToInt

object PdfPageLayoutCalculator {

    data class PageLayout(
        val pageWidthPt: Int,
        val pageHeightPt: Int,
        val destLeft: Float,
        val destTop: Float,
        val destWidth: Float,
        val destHeight: Float
    ) {
        val destRight: Float
            get() = destLeft + destWidth

        val destBottom: Float
            get() = destTop + destHeight
    }

    /**
     * Calculates page geometry and aspect-fit centering for an image placed on a PDF page.
     */
    fun calculateLayout(
        imageWidth: Int,
        imageHeight: Int,
        pageSize: PdfPageSize = PdfPageSize.A4,
        marginPt: Float = 20f,
        autoRotatePage: Boolean = true
    ): PageLayout {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return PageLayout(595, 842, 0f, 0f, 0f, 0f)
        }

        if (pageSize is PdfPageSize.FitImage) {
            return PageLayout(
                pageWidthPt = imageWidth,
                pageHeightPt = imageHeight,
                destLeft = 0f,
                destTop = 0f,
                destWidth = imageWidth.toFloat(),
                destHeight = imageHeight.toFloat()
            )
        }

        val (baseW, baseH) = when (pageSize) {
            is PdfPageSize.A4 -> PdfPageSize.A4.WIDTH_PT to PdfPageSize.A4.HEIGHT_PT
            is PdfPageSize.Letter -> PdfPageSize.Letter.WIDTH_PT to PdfPageSize.Letter.HEIGHT_PT
            is PdfPageSize.Custom -> pageSize.widthPt to pageSize.heightPt
            is PdfPageSize.FitImage -> imageWidth to imageHeight
        }

        // Auto-match page orientation (portrait vs landscape) to image aspect ratio
        val isImageLandscape = imageWidth > imageHeight
        val isPageLandscape = baseW > baseH

        val (pageW, pageH) = if (autoRotatePage && (isImageLandscape != isPageLandscape)) {
            baseH to baseW
        } else {
            baseW to baseH
        }

        val safeMargin = marginPt.coerceAtLeast(0f).coerceAtMost(minOf(pageW, pageH) / 4f)
        val usableW = (pageW - 2 * safeMargin).coerceAtLeast(1f)
        val usableH = (pageH - 2 * safeMargin).coerceAtLeast(1f)

        val scaleW = usableW / imageWidth.toFloat()
        val scaleH = usableH / imageHeight.toFloat()
        val scale = minOf(scaleW, scaleH)

        val destW = imageWidth * scale
        val destH = imageHeight * scale

        // Center on page
        val destLeft = safeMargin + (usableW - destW) / 2f
        val destTop = safeMargin + (usableH - destH) / 2f

        return PageLayout(
            pageWidthPt = pageW,
            pageHeightPt = pageH,
            destLeft = destLeft,
            destTop = destTop,
            destWidth = destW,
            destHeight = destH
        )
    }
}
