package com.tapconvert.core.model

sealed interface ConversionQuality {
    val qualityPercent: Int

    data object Low : ConversionQuality {
        override val qualityPercent: Int = 50
    }

    data object Medium : ConversionQuality {
        override val qualityPercent: Int = 75
    }

    data object High : ConversionQuality {
        override val qualityPercent: Int = 90
    }

    data object Original : ConversionQuality {
        override val qualityPercent: Int = 100
    }

    data class Custom(override val qualityPercent: Int) : ConversionQuality {
        init {
            require(qualityPercent in 1..100) {
                "Quality percent must be between 1 and 100, was $qualityPercent"
            }
        }
    }
}
