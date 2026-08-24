package com.tapconvert.core.ads

import com.tapconvert.core.analytics.AnalyticsTracker
import com.tapconvert.core.analytics.NoOpAnalyticsTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface AdManager {
    val state: StateFlow<AdState>
    fun shouldShowInterstitial(currentTimeMs: Long = System.currentTimeMillis()): Boolean
    fun recordConversion()
    fun onInterstitialShown(currentTimeMs: Long = System.currentTimeMillis())
    fun onBannerImpression()
    fun grantReward(reward: AdReward, currentTimeMs: Long = System.currentTimeMillis())
    fun consumeBatchToken(): Boolean
    fun setAdFree(isAdFree: Boolean)
    fun setPro(isPro: Boolean, tier: SubscriptionTier = if (isPro) SubscriptionTier.PRO_ANNUAL else SubscriptionTier.FREE)
    fun resetSession()
}

class DefaultAdManager(
    private val config: AdPolicyConfig = AdPolicyConfig(),
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker()
) : AdManager {

    private val _state = MutableStateFlow(AdState())
    override val state: StateFlow<AdState> = _state.asStateFlow()

    override fun shouldShowInterstitial(currentTimeMs: Long): Boolean {
        val current = _state.value
        if (current.isEffectiveAdFree) return false
        if (current.sessionConversionCount < config.minConversionsBeforeFirstInterstitial) return false
        if (current.sessionInterstitialCount >= config.maxInterstitialsPerSession) return false

        val elapsedSinceLast = currentTimeMs - current.lastInterstitialShowTime
        return elapsedSinceLast >= config.interstitialCooldownMs
    }

    override fun recordConversion() {
        _state.update { current ->
            current.copy(sessionConversionCount = current.sessionConversionCount + 1)
        }
    }

    override fun onInterstitialShown(currentTimeMs: Long) {
        _state.update { current ->
            current.copy(
                sessionInterstitialCount = current.sessionInterstitialCount + 1,
                lastInterstitialShowTime = currentTimeMs
            )
        }
        analyticsTracker.logAdImpression(AdPlacement.INTERSTITIAL_POST_CONVERSION.slotName, "interstitial")
    }

    override fun onBannerImpression() {
        analyticsTracker.logAdImpression(AdPlacement.BANNER_BOTTOM.slotName, "banner")
    }

    override fun grantReward(reward: AdReward, currentTimeMs: Long) {
        _state.update { current ->
            when (reward) {
                is AdReward.SingleBatchUnlock -> {
                    current.copy(unlockedBatchTokens = current.unlockedBatchTokens + 1)
                }
                is AdReward.BatchModeUnlock -> {
                    val newExpiry = maxOf(current.batchModeExpiryTime, currentTimeMs) + reward.durationMs
                    current.copy(batchModeExpiryTime = newExpiry)
                }
                is AdReward.UltraFastUnlock -> {
                    val newExpiry = maxOf(current.ultraFastExpiryTime, currentTimeMs) + reward.durationMs
                    current.copy(ultraFastExpiryTime = newExpiry)
                }
            }
        }
        val durationMin = when (reward) {
            is AdReward.SingleBatchUnlock -> 0
            is AdReward.BatchModeUnlock -> (reward.durationMs / 60_000L).toInt()
            is AdReward.UltraFastUnlock -> (reward.durationMs / 60_000L).toInt()
        }
        analyticsTracker.logAdRewardGranted(
            rewardType = reward.rewardName,
            durationMinutes = durationMin
        )
    }

    override fun consumeBatchToken(): Boolean {
        var consumed = false
        _state.update { current ->
            if (current.unlockedBatchTokens > 0) {
                consumed = true
                current.copy(unlockedBatchTokens = current.unlockedBatchTokens - 1)
            } else {
                current
            }
        }
        return consumed
    }

    override fun setAdFree(isAdFree: Boolean) {
        _state.update { it.copy(isAdFree = isAdFree) }
    }

    override fun setPro(isPro: Boolean, tier: SubscriptionTier) {
        _state.update { it.copy(isPro = isPro, subscriptionTier = tier) }
    }

    override fun resetSession() {
        _state.update { current ->
            current.copy(
                sessionConversionCount = 0,
                sessionInterstitialCount = 0,
                lastInterstitialShowTime = 0L
            )
        }
    }
}
