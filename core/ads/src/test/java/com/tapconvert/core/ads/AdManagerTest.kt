package com.tapconvert.core.ads

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.testing.FakeAnalyticsTracker
import org.junit.Test

class AdManagerTest {

    private val fakeAnalytics = FakeAnalyticsTracker()
    private val config = AdPolicyConfig(
        minConversionsBeforeFirstInterstitial = 2,
        interstitialCooldownMs = 60_000L,
        maxInterstitialsPerSession = 5
    )
    private val adManager = DefaultAdManager(config, fakeAnalytics)

    @Test
    fun `interstitial suppressed before minimum conversion threshold is met`() {
        val now = 100_000L
        assertThat(adManager.shouldShowInterstitial(now)).isFalse()

        adManager.recordConversion() // 1 conversion
        assertThat(adManager.shouldShowInterstitial(now)).isFalse()

        adManager.recordConversion() // 2 conversions (threshold met)
        assertThat(adManager.shouldShowInterstitial(now)).isTrue()
    }

    @Test
    fun `interstitial cooldown timer prevents consecutive rapid impressions`() {
        var now = 100_000L
        adManager.recordConversion()
        adManager.recordConversion()

        assertThat(adManager.shouldShowInterstitial(now)).isTrue()
        adManager.onInterstitialShown(now)

        // 30 seconds later (within 60s cooldown)
        now += 30_000L
        assertThat(adManager.shouldShowInterstitial(now)).isFalse()

        // 61 seconds later (cooldown elapsed)
        now += 31_000L
        assertThat(adManager.shouldShowInterstitial(now)).isTrue()
    }

    @Test
    fun `rewarded single batch grant unlocks batch privileges and consumption decrements token`() {
        val now = 100_000L
        assertThat(adManager.state.value.hasBatchTaskPrivilege(now)).isFalse()
        assertThat(adManager.state.value.maxBatchFilesAllowed(now)).isEqualTo(5)
        assertThat(adManager.state.value.maxPdfImagesAllowed(now)).isEqualTo(10)
        assertThat(adManager.state.value.unlockedBatchTokens).isEqualTo(0)

        val reward = AdReward.SingleBatchUnlock(maxBatchFiles = 20, maxPdfImages = 25)
        adManager.grantReward(reward, currentTimeMs = now)

        assertThat(adManager.state.value.unlockedBatchTokens).isEqualTo(1)
        assertThat(adManager.state.value.hasBatchTaskPrivilege(now)).isTrue()
        assertThat(adManager.state.value.maxBatchFilesAllowed(now)).isEqualTo(20)
        assertThat(adManager.state.value.maxPdfImagesAllowed(now)).isEqualTo(25)

        // Consuming token decrements token count back to 0
        val consumed = adManager.consumeBatchToken()
        assertThat(consumed).isTrue()
        assertThat(adManager.state.value.unlockedBatchTokens).isEqualTo(0)
        assertThat(adManager.state.value.hasBatchTaskPrivilege(now)).isFalse()
        assertThat(adManager.state.value.maxBatchFilesAllowed(now)).isEqualTo(5)
        assertThat(adManager.state.value.maxPdfImagesAllowed(now)).isEqualTo(10)

        // Consuming again returns false
        val consumedAgain = adManager.consumeBatchToken()
        assertThat(consumedAgain).isFalse()

        assertThat(fakeAnalytics.rewardsGranted).hasSize(1)
    }

    @Test
    fun `adFree flag suppresses all interstitials`() {
        val now = 100_000L
        adManager.recordConversion()
        adManager.recordConversion()

        adManager.setAdFree(true)
        assertThat(adManager.shouldShowInterstitial(now)).isFalse()
        assertThat(adManager.state.value.isBatchModeUnlocked(now)).isTrue()
    }

    @Test
    fun `pro subscription tier unlocks unlimited batch files, unlimited pdf images, and suppresses interstitials`() {
        val now = 100_000L
        adManager.recordConversion()
        adManager.recordConversion()

        adManager.setPro(true, SubscriptionTier.PRO_LIFETIME)

        assertThat(adManager.state.value.isPro).isTrue()
        assertThat(adManager.state.value.subscriptionTier).isEqualTo(SubscriptionTier.PRO_LIFETIME)
        assertThat(adManager.shouldShowInterstitial(now)).isFalse()
        assertThat(adManager.state.value.isBatchModeUnlocked(now)).isTrue()
        assertThat(adManager.state.value.isUltraFastUnlocked(now)).isTrue()
        assertThat(adManager.state.value.maxBatchFilesAllowed(now)).isEqualTo(100)
        assertThat(adManager.state.value.maxPdfImagesAllowed(now)).isEqualTo(500)
    }

    @Test
    fun `fake billing manager purchases and updates subscription status correctly for lifetime and subscriptions`() {
        val billingManager = FakeBillingManager()
        assertThat(billingManager.subscriptionStatus.value.isPro).isFalse()

        billingManager.purchase(null, SubscriptionPlan.Lifetime)
        assertThat(billingManager.subscriptionStatus.value.isPro).isTrue()
        assertThat(billingManager.subscriptionStatus.value.tier).isEqualTo(SubscriptionTier.PRO_LIFETIME)
        assertThat(billingManager.subscriptionStatus.value.expiryTimestampMs).isNull()

        billingManager.purchase(null, SubscriptionPlan.Annual)
        assertThat(billingManager.subscriptionStatus.value.isPro).isTrue()
        assertThat(billingManager.subscriptionStatus.value.tier).isEqualTo(SubscriptionTier.PRO_ANNUAL)
        assertThat(billingManager.subscriptionStatus.value.expiryTimestampMs).isNotNull()

        billingManager.purchase(null, SubscriptionPlan.Monthly)
        assertThat(billingManager.subscriptionStatus.value.isPro).isTrue()
        assertThat(billingManager.subscriptionStatus.value.tier).isEqualTo(SubscriptionTier.PRO_MONTHLY)
    }
}
