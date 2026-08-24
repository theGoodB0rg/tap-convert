package com.tapconvert.core.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Production-ready, lifecycle-safe helper for loading and presenting
 * Google Mobile Ads (AdMob) Interstitials and Rewarded Video Ads.
 */
class AdMobAdLoader(
    private val interstitialAdUnitId: String = TEST_INTERSTITIAL_AD_UNIT_ID,
    private val rewardedAdUnitId: String = TEST_REWARDED_AD_UNIT_ID
) {

    companion object {
        private const val TAG = "AdMobAdLoader"

        // Official Google AdMob Test Ad Unit IDs (always fill with test ads)
        const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    }

    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading: Boolean = false

    private var rewardedAd: RewardedAd? = null
    private var isRewardedLoading: Boolean = false

    val isInterstitialReady: Boolean
        get() = interstitialAd != null

    val isRewardedReady: Boolean
        get() = rewardedAd != null

    fun preloadInterstitial(context: Context, onLoaded: (() -> Unit)? = null, onFailed: ((String) -> Unit)? = null) {
        if (interstitialAd != null || isInterstitialLoading) return

        isInterstitialLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            interstitialAdUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isInterstitialLoading = false
                    interstitialAd = ad
                    Log.d(TAG, "AdMob Interstitial Ad loaded successfully")
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isInterstitialLoading = false
                    interstitialAd = null
                    val errorMsg = loadAdError.message
                    Log.w(TAG, "AdMob Interstitial failed to load: $errorMsg (code ${loadAdError.code})")
                    onFailed?.invoke(errorMsg)
                }
            }
        )
    }

    fun showInterstitial(
        activity: Activity,
        onDismissed: () -> Unit,
        onShowFailed: ((String) -> Unit)? = null
    ) {
        val ad = interstitialAd
        if (ad == null) {
            Log.w(TAG, "Interstitial not ready to show; proceeding immediately")
            onDismissed()
            preloadInterstitial(activity.applicationContext)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                onDismissed()
                preloadInterstitial(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                Log.w(TAG, "Interstitial failed to show: ${adError.message}")
                onShowFailed?.invoke(adError.message)
                onDismissed()
                preloadInterstitial(activity.applicationContext)
            }
        }

        ad.show(activity)
    }

    fun preloadRewarded(context: Context, onLoaded: (() -> Unit)? = null, onFailed: ((String) -> Unit)? = null) {
        if (rewardedAd != null || isRewardedLoading) return

        isRewardedLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            rewardedAdUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isRewardedLoading = false
                    rewardedAd = ad
                    Log.d(TAG, "AdMob Rewarded Ad loaded successfully")
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isRewardedLoading = false
                    rewardedAd = null
                    val errorMsg = loadAdError.message
                    Log.w(TAG, "AdMob Rewarded failed to load: $errorMsg (code ${loadAdError.code})")
                    onFailed?.invoke(errorMsg)
                }
            }
        )
    }

    fun showRewarded(
        activity: Activity,
        reward: AdReward = AdReward.SingleBatchUnlock(),
        onUserEarnedReward: (AdReward) -> Unit,
        onDismissed: () -> Unit,
        onShowFailed: ((String) -> Unit)? = null
    ) {
        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "Rewarded ad not ready to show")
            onShowFailed?.invoke("Ad is not ready yet. Please check your internet connection and try again.")
            preloadRewarded(activity.applicationContext)
            return
        }

        var rewardGranted = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                onDismissed()
                preloadRewarded(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                Log.w(TAG, "Rewarded ad failed to show: ${adError.message}")
                onShowFailed?.invoke(adError.message)
                onDismissed()
                preloadRewarded(activity.applicationContext)
            }
        }

        ad.show(activity) { rewardItem ->
            Log.d(TAG, "User earned reward: ${rewardItem.type} (amount: ${rewardItem.amount})")
            rewardGranted = true
            onUserEarnedReward(reward)
        }
    }
}
