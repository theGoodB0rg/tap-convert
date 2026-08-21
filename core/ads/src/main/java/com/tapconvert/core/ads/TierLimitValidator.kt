package com.tapconvert.core.ads

import com.tapconvert.core.model.ConversionType

sealed interface TierLimitResult {
    data object Allowed : TierLimitResult
    data class LimitExceeded(
        val requestedCount: Int,
        val allowedCount: Int,
        val isPdf: Boolean,
        val isPro: Boolean,
        val isFastPassActive: Boolean
    ) : TierLimitResult
}

object TierLimitValidator {

    fun validate(
        fileCount: Int,
        conversionType: ConversionType,
        adState: AdState,
        currentTimeMs: Long = System.currentTimeMillis()
    ): TierLimitResult {
        val isPdf = conversionType == ConversionType.IMAGES_TO_PDF
        val maxAllowed = if (isPdf) {
            adState.maxPdfImagesAllowed(currentTimeMs)
        } else {
            adState.maxBatchFilesAllowed(currentTimeMs)
        }

        return if (fileCount <= maxAllowed) {
            TierLimitResult.Allowed
        } else {
            TierLimitResult.LimitExceeded(
                requestedCount = fileCount,
                allowedCount = maxAllowed,
                isPdf = isPdf,
                isPro = adState.isPro,
                isFastPassActive = adState.isBatchModeUnlocked(currentTimeMs)
            )
        }
    }
}
