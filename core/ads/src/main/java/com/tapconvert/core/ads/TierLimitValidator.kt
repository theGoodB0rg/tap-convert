package com.tapconvert.core.ads

import com.tapconvert.core.model.ConversionType

sealed interface TierLimitResult {
    data object Allowed : TierLimitResult
    data class LimitExceeded(
        val requestedCount: Int,
        val allowedCount: Int,
        val freeLimit: Int,
        val rewardedLimit: Int,
        val isPdf: Boolean,
        val isPro: Boolean,
        val isFastPassActive: Boolean,
        val canUnlockWithReward: Boolean = !isPro && requestedCount <= rewardedLimit
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
        val freeLimit = if (isPdf) AdState.FREE_MAX_PDF_IMAGES else AdState.FREE_MAX_BATCH_FILES
        val rewardedLimit = if (isPdf) AdState.REWARDED_MAX_PDF_IMAGES else AdState.REWARDED_MAX_BATCH_FILES
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
                freeLimit = freeLimit,
                rewardedLimit = rewardedLimit,
                isPdf = isPdf,
                isPro = adState.isPro,
                isFastPassActive = adState.hasBatchTaskPrivilege(currentTimeMs),
                canUnlockWithReward = !adState.isPro && fileCount <= rewardedLimit
            )
        }
    }
}
