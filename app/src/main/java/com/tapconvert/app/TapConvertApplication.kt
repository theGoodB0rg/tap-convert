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

    override fun onCreate() {
        super.onCreate()
        appScope.launch(Dispatchers.IO) {
            try {
                MobileAds.initialize(this@TapConvertApplication)
            } catch (_: Throwable) {}
        }
    }
}
