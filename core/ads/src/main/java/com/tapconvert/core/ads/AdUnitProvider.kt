package com.tapconvert.core.ads

/**
 * Real ad unit IDs are injected via BuildConfig (release) — never hardcoded.
 * Debug defaults to Google sample IDs so CI/emulators always fill test ads.
 */
interface AdUnitProvider {
    val appId: String
    val bannerId: String
    val interstitialId: String
    val rewardedId: String
}

data class StaticAdUnitProvider(
    override val appId: String,
    override val bannerId: String,
    override val interstitialId: String,
    override val rewardedId: String
) : AdUnitProvider {
    companion object {
        fun test(): AdUnitProvider = StaticAdUnitProvider(
            appId = "ca-app-pub-3940256099942544~3347511713",
            bannerId = AdMobAdLoader.TEST_BANNER_AD_UNIT_ID,
            interstitialId = AdMobAdLoader.TEST_INTERSTITIAL_AD_UNIT_ID,
            rewardedId = AdMobAdLoader.TEST_REWARDED_AD_UNIT_ID
        )
    }
}
