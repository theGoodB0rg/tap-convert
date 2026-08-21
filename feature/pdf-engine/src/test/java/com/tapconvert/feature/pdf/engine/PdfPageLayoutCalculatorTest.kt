package com.tapconvert.feature.pdf.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PdfPageLayoutCalculatorTest {

    @Test
    fun `FitImage layout matches exact image dimensions with zero margins`() {
        val layout = PdfPageLayoutCalculator.calculateLayout(
            imageWidth = 1920,
            imageHeight = 1080,
            pageSize = PdfPageSize.FitImage
        )

        assertThat(layout.pageWidthPt).isEqualTo(1920)
        assertThat(layout.pageHeightPt).isEqualTo(1080)
        assertThat(layout.destLeft).isEqualTo(0f)
        assertThat(layout.destTop).isEqualTo(0f)
        assertThat(layout.destWidth).isEqualTo(1920f)
        assertThat(layout.destHeight).isEqualTo(1080f)
    }

    @Test
    fun `A4 portrait layout centers a 4-3 portrait image with margins`() {
        val layout = PdfPageLayoutCalculator.calculateLayout(
            imageWidth = 3000,
            imageHeight = 4000,
            pageSize = PdfPageSize.A4,
            marginPt = 20f,
            autoRotatePage = false
        )

        assertThat(layout.pageWidthPt).isEqualTo(PdfPageSize.A4.WIDTH_PT)
        assertThat(layout.pageHeightPt).isEqualTo(PdfPageSize.A4.HEIGHT_PT)
        assertThat(layout.destLeft).isAtLeast(20f)
        assertThat(layout.destTop).isAtLeast(20f)
        assertThat(layout.destRight).isAtMost(PdfPageSize.A4.WIDTH_PT - 20f + 0.1f)
        assertThat(layout.destBottom).isAtMost(PdfPageSize.A4.HEIGHT_PT - 20f + 0.1f)
    }

    @Test
    fun `autoRotatePage switches A4 page orientation to landscape for landscape image`() {
        val layout = PdfPageLayoutCalculator.calculateLayout(
            imageWidth = 4000,
            imageHeight = 2000, // Landscape 2:1
            pageSize = PdfPageSize.A4,
            marginPt = 20f,
            autoRotatePage = true
        )

        // A4 standard portrait is 595 x 842. Rotated landscape is 842 x 595.
        assertThat(layout.pageWidthPt).isEqualTo(PdfPageSize.A4.HEIGHT_PT) // 842
        assertThat(layout.pageHeightPt).isEqualTo(PdfPageSize.A4.WIDTH_PT) // 595
    }

    @Test
    fun `handles invalid or zero image dimensions gracefully without division by zero`() {
        val layout = PdfPageLayoutCalculator.calculateLayout(0, 0)
        assertThat(layout.pageWidthPt).isGreaterThan(0)
        assertThat(layout.pageHeightPt).isGreaterThan(0)
    }
}
