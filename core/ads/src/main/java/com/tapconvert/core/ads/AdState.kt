package com.tapconvert.core.ads

data class AdState(
    val isAdFree: Boolean = false,
    val isPro: Boolean = false,
    val subscriptionTier: SubscriptionTier = SubscriptionTier.FREE,
    val sessionConversionCount: Int = 0,
    val sessionInterstitialCount: Int = 0,
    val lastInterstitialShowTime: Long = 0L,
    val batchModeExpiryTime: Long = 0L,
    val ultraFastExpiryTime: Long = 0L
) {
    val isEffectiveAdFree: Boolean
        get() = isPro || isAdFree

    fun isBatchModeUnlocked(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        return isEffectiveAdFree || currentTimeMs < batchModeExpiryTime
    }

    fun isUltraFastUnlocked(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        return isEffectiveAdFree || currentTimeMs < ultraFastExpiryTime
    }

    fun maxBatchFilesAllowed(currentTimeMs: Long = System.currentTimeMillis()): Int {
        return when {
            isPro -> 100
            isBatchModeUnlocked(currentTimeMs) -> 10
            else -> 2
        }
    }

    fun maxPdfImagesAllowed(currentTimeMs: Long = System.currentTimeMillis()): Int {
        return when {
            isPro -> 500
            isBatchModeUnlocked(currentTimeMs) -> 15
            else -> 5
        }
    }
}
