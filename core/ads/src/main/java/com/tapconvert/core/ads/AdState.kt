package com.tapconvert.core.ads

data class AdState(
    val isAdFree: Boolean = false,
    val isPro: Boolean = false,
    val subscriptionTier: SubscriptionTier = SubscriptionTier.FREE,
    val sessionConversionCount: Int = 0,
    val sessionInterstitialCount: Int = 0,
    val lastInterstitialShowTime: Long = 0L,
    val batchModeExpiryTime: Long = 0L,
    val ultraFastExpiryTime: Long = 0L,
    val unlockedBatchTokens: Int = 0
) {
    companion object {
        const val FREE_MAX_BATCH_FILES = 5
        const val FREE_MAX_PDF_IMAGES = 10

        const val REWARDED_MAX_BATCH_FILES = 20
        const val REWARDED_MAX_PDF_IMAGES = 25

        const val PRO_MAX_BATCH_FILES = 100
        const val PRO_MAX_PDF_IMAGES = 500
    }

    val isEffectiveAdFree: Boolean
        get() = isPro || isAdFree

    fun hasBatchTaskPrivilege(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        return isEffectiveAdFree || unlockedBatchTokens > 0 || currentTimeMs < batchModeExpiryTime
    }

    fun isBatchModeUnlocked(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        return hasBatchTaskPrivilege(currentTimeMs)
    }

    fun isUltraFastUnlocked(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        return isEffectiveAdFree || currentTimeMs < ultraFastExpiryTime
    }

    fun maxBatchFilesAllowed(currentTimeMs: Long = System.currentTimeMillis()): Int {
        return when {
            isPro -> PRO_MAX_BATCH_FILES
            hasBatchTaskPrivilege(currentTimeMs) -> REWARDED_MAX_BATCH_FILES
            else -> FREE_MAX_BATCH_FILES
        }
    }

    fun maxPdfImagesAllowed(currentTimeMs: Long = System.currentTimeMillis()): Int {
        return when {
            isPro -> PRO_MAX_PDF_IMAGES
            hasBatchTaskPrivilege(currentTimeMs) -> REWARDED_MAX_PDF_IMAGES
            else -> FREE_MAX_PDF_IMAGES
        }
    }
}
