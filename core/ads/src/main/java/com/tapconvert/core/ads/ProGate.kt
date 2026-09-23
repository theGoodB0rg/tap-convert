package com.tapconvert.core.ads

/**
 * Decentralized Pro gate. Release code must pass a FRESHLY verified flag
 * (from PlayEntitlementVerifier.refresh, <10min old). A local [AdState.isPro]
 * boolean alone never grants Pro in release — it is a UI hint only.
 *
 * Keeping this pure + clock-injected preserves JVM testability.
 */
object ProGate {
    const val FRESHNESS_MS = 10 * 60 * 1000L

    fun isVerified(isPro: Boolean, sourceFresh: Boolean, queriedAtMs: Long, nowMs: Long): Boolean {
        if (!isPro || !sourceFresh) return false
        return (nowMs - queriedAtMs) <= FRESHNESS_MS
    }

    fun maxBatchFiles(isProVerified: Boolean, hasRewardPrivilege: Boolean): Int = when {
        isProVerified -> AdState.PRO_MAX_BATCH_FILES
        hasRewardPrivilege -> AdState.REWARDED_MAX_BATCH_FILES
        else -> AdState.FREE_MAX_BATCH_FILES
    }

    fun maxPdfImages(isProVerified: Boolean, hasRewardPrivilege: Boolean): Int = when {
        isProVerified -> AdState.PRO_MAX_PDF_IMAGES
        hasRewardPrivilege -> AdState.REWARDED_MAX_PDF_IMAGES
        else -> AdState.FREE_MAX_PDF_IMAGES
    }
}
