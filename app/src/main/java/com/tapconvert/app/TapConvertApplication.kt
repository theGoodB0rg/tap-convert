package com.tapconvert.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.tapconvert.core.database.repository.ConversionHistoryRepository
import com.tapconvert.core.database.repository.RoomConversionHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TapConvertApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val historyRepository: ConversionHistoryRepository by lazy {
        RoomConversionHistoryRepository.create(this)
    }

    /** Release graph: Play-backed verifier. Never Fake in release. */
    val entitlementVerifier: com.tapconvert.core.billing.EntitlementVerifier by lazy {
        com.tapconvert.core.billing.PlayEntitlementVerifier(this)
    }

    val adUnits by lazy { com.tapconvert.app.monetization.buildAdUnitProvider() }

    override fun onCreate() {
        super.onCreate()
        // UMP consent must precede MobileAds init (EEA/GDPR). Consent form itself
        // is shown from MainActivity once it has an Activity context.
        appScope.launch(Dispatchers.IO) {
            try {
                MobileAds.initialize(this@TapConvertApplication)
            } catch (_: Throwable) {}
        }
    }
}
