package com.tapconvert.app.monetization

import android.app.Activity
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.tapconvert.core.ads.AdUnitProvider
import com.tapconvert.core.ads.StaticAdUnitProvider
import com.tapconvert.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * UMP consent gate: MobileAds must init only after consent flow completes.
 * Required for EEA/GDPR + AdMob approval. Testable via [ConsentGate] fake.
 */
interface ConsentGate {
    val canRequestAds: StateFlow<Boolean>
    suspend fun requestConsent(activity: Activity)
}

class UmpConsentGate(activity: Activity) : ConsentGate {
    private val consentInfo: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(activity)
    private val _canRequestAds = MutableStateFlow(consentInfo.canRequestAds())
    override val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    override suspend fun requestConsent(activity: Activity) {
        val params = ConsentRequestParameters.Builder().build()
        suspendCancellableCoroutine { cont ->
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                _canRequestAds.value = consentInfo.canRequestAds()
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }
}

class FakeConsentGate(initial: Boolean = true) : ConsentGate {
    private val _canRequestAds = MutableStateFlow(initial)
    override val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()
    override suspend fun requestConsent(activity: Activity) { /* no-op */ }
    fun setCanRequestAds(v: Boolean) { _canRequestAds.value = v }
}

fun buildAdUnitProvider(): AdUnitProvider = StaticAdUnitProvider(
    appId = "unused-manifest-placeholder",
    bannerId = BuildConfig.ADMOB_BANNER_ID,
    interstitialId = BuildConfig.ADMOB_INTERSTITIAL_ID,
    rewardedId = BuildConfig.ADMOB_REWARDED_ID
)
