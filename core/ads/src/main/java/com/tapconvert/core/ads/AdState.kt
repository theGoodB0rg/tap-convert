package com.tapconvert.core.ads

data class AdState(
    val isAdFree: Boolean = false,
    val sessionConversionCount: Int = 0,
    val sessionInterstitialCount: Int = 0,
    val lastInterstitialShowTime: Long = 0L,
    val batchModeExpiryTime: Long = 0L,
    val ultraFastExpiryTime: Long = 0L
) {
    fun isBatchModeUnlocked(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        return isAdFree || currentTimeMs < batchModeExpiryTime
    }

    fun isUltraFastUnlocked(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        return isAdFree || currentTimeMs < ultraFastExpiryTime
    }
}
