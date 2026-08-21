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
    fun `rewarded grant unlocks feature privileges with expiration timestamp`() {
        val now = 100_000L
        assertThat(adManager.state.value.isBatchModeUnlocked(now)).isFalse()
        assertThat(adManager.state.value.maxBatchFilesAllowed(now)).isEqualTo(2)
        assertThat(adManager.state.value.maxPdfImagesAllowed(now)).isEqualTo(5)

        val reward = AdReward.BatchModeUnlock(durationMs = 30 * 60 * 1000L)
        adManager.grantReward(reward, currentTimeMs = now)

        assertThat(adManager.state.value.isBatchModeUnlocked(now)).isTrue()
        assertThat(adManager.state.value.maxBatchFilesAllowed(now)).isEqualTo(10)
        assertThat(adManager.state.value.maxPdfImagesAllowed(now)).isEqualTo(15)

        assertThat(adManager.state.value.isBatchModeUnlocked(now + 31 * 60 * 1000L)).isFalse()
        assertThat(adManager.state.value.maxBatchFilesAllowed(now + 31 * 60 * 1000L)).isEqualTo(2)

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

        adManager.setPro(true, SubscriptionTier.PRO_ANNUAL)

        assertThat(adManager.state.value.isPro).isTrue()
        assertThat(adManager.state.value.subscriptionTier).isEqualTo(SubscriptionTier.PRO_ANNUAL)
        assertThat(adManager.shouldShowInterstitial(now)).isFalse()
        assertThat(adManager.state.value.isBatchModeUnlocked(now)).isTrue()
        assertThat(adManager.state.value.isUltraFastUnlocked(now)).isTrue()
        assertThat(adManager.state.value.maxBatchFilesAllowed(now)).isEqualTo(100)
        assertThat(adManager.state.value.maxPdfImagesAllowed(now)).isEqualTo(500)
    }

    @Test
    fun `fake billing manager purchases and updates subscription status correctly`() {
        val billingManager = FakeBillingManager()
        assertThat(billingManager.subscriptionStatus.value.isPro).isFalse()

        billingManager.purchase(null, SubscriptionPlan.Annual)
        assertThat(billingManager.subscriptionStatus.value.isPro).isTrue()
        assertThat(billingManager.subscriptionStatus.value.tier).isEqualTo(SubscriptionTier.PRO_ANNUAL)

        billingManager.purchase(null, SubscriptionPlan.Monthly)
        assertThat(billingManager.subscriptionStatus.value.isPro).isTrue()
        assertThat(billingManager.subscriptionStatus.value.tier).isEqualTo(SubscriptionTier.PRO_MONTHLY)
    }
}
