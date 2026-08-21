package com.tapconvert.feature.pdf.engine

sealed interface PdfPageSize {
    val displayName: String

    data object A4 : PdfPageSize {
        override val displayName: String = "A4 (595 x 842 pt)"
        const val WIDTH_PT = 595
        const val HEIGHT_PT = 842
    }

    data object Letter : PdfPageSize {
        override val displayName: String = "Letter (612 x 792 pt)"
        const val WIDTH_PT = 612
        const val HEIGHT_PT = 792
    }

    data object FitImage : PdfPageSize {
        override val displayName: String = "Fit to Image Size"
    }

    data class Custom(
        val widthPt: Int,
        val heightPt: Int,
        override val displayName: String = "Custom ($widthPt x $heightPt pt)"
    ) : PdfPageSize {
        init {
            require(widthPt > 0 && heightPt > 0) {
                "Page dimensions must be positive, was $widthPt x $heightPt"
            }
        }
    }
}
