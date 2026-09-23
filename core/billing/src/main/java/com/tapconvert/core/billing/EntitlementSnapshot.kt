package com.tapconvert.core.billing

import com.tapconvert.core.ads.SubscriptionTier

/**
 * Verified entitlement. Source of truth is ALWAYS a fresh Play query.
 * Conversions stay on-device/offline; Pro gates require PLAY_FRESH (<10min).
 */
data class EntitlementSnapshot(
    val isPro: Boolean = false,
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val purchaseTokensHash: Set<String> = emptySet(),
    val queriedAtMs: Long = 0L,
    val source: EntitlementSource = EntitlementSource.PLAY_EMPTY
) {
    companion object {
        const val FRESHNESS_MS = 10 * 60 * 1000L
        val FREE = EntitlementSnapshot()
    }

    fun isFresh(nowMs: Long = System.currentTimeMillis()): Boolean =
        source == EntitlementSource.PLAY_FRESH && (nowMs - queriedAtMs) <= FRESHNESS_MS

    /** Pro is honored only when freshly verified online. */
    fun isProVerified(nowMs: Long = System.currentTimeMillis()): Boolean =
        isPro && isFresh(nowMs)
}

enum class EntitlementSource {
    PLAY_FRESH,
    PLAY_EMPTY,
    OFFLINE,
    ERROR
}
