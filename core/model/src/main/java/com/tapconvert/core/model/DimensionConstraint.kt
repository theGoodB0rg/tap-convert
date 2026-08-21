package com.tapconvert.core.model

import kotlin.math.roundToInt

sealed interface DimensionConstraint {

    fun calculateDimensions(originalWidth: Int, originalHeight: Int): Pair<Int, Int>

    data object None : DimensionConstraint {
        override fun calculateDimensions(originalWidth: Int, originalHeight: Int): Pair<Int, Int> {
            return originalWidth to originalHeight
        }
    }

    data class MaxDimension(val maxPixels: Int) : DimensionConstraint {
        init {
            require(maxPixels > 0) { "maxPixels must be positive, was $maxPixels" }
        }

        override fun calculateDimensions(originalWidth: Int, originalHeight: Int): Pair<Int, Int> {
            if (originalWidth <= 0 || originalHeight <= 0) return 0 to 0
            val maxSide = maxOf(originalWidth, originalHeight)
            if (maxSide <= maxPixels) return originalWidth to originalHeight

            val scale = maxPixels.toFloat() / maxSide.toFloat()
            val targetW = (originalWidth * scale).roundToInt().coerceAtLeast(1)
            val targetH = (originalHeight * scale).roundToInt().coerceAtLeast(1)
            return targetW to targetH
        }
    }

    data class Exact(
        val targetWidth: Int,
        val targetHeight: Int,
        val mode: FitMode = FitMode.FIT_INSIDE
    ) : DimensionConstraint {
        init {
            require(targetWidth > 0 && targetHeight > 0) {
                "Width ($targetWidth) and height ($targetHeight) must be positive"
            }
        }

        override fun calculateDimensions(originalWidth: Int, originalHeight: Int): Pair<Int, Int> {
            if (originalWidth <= 0 || originalHeight <= 0) return targetWidth to targetHeight
            return when (mode) {
                FitMode.STRETCH_EXACT -> targetWidth to targetHeight
                FitMode.FIT_INSIDE -> {
                    val scaleW = targetWidth.toFloat() / originalWidth.toFloat()
                    val scaleH = targetHeight.toFloat() / originalHeight.toFloat()
                    val scale = minOf(scaleW, scaleH)
                    val w = (originalWidth * scale).roundToInt().coerceAtLeast(1)
                    val h = (originalHeight * scale).roundToInt().coerceAtLeast(1)
                    w to h
                }
            }
        }
    }

    data class ScalePercentage(val percentage: Float) : DimensionConstraint {
        init {
            require(percentage in 0.01f..10.0f) {
                "Scale percentage must be between 0.01 (1%) and 10.0 (1000%), was $percentage"
            }
        }

        override fun calculateDimensions(originalWidth: Int, originalHeight: Int): Pair<Int, Int> {
            val w = (originalWidth * percentage).roundToInt().coerceAtLeast(1)
            val h = (originalHeight * percentage).roundToInt().coerceAtLeast(1)
            return w to h
        }
    }

    enum class FitMode {
        FIT_INSIDE,
        STRETCH_EXACT
    }
}
