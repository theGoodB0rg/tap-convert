package com.tapconvert.core.analytics

import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MediaCategory

interface AnalyticsTracker {
    fun trackEvent(event: AnalyticsEvent)

    fun logEvent(name: String, params: Map<String, Any> = emptyMap()) {
        trackEvent(AnalyticsEvent.Custom(name, params))
    }

    fun logConversionStarted(type: ConversionType, inputSizeBytes: Long, sourceFormat: String = "unknown", presetId: String? = null) {
        trackEvent(AnalyticsEvent.ConversionStarted(type, inputSizeBytes, sourceFormat, presetId))
    }

    fun logConversionCompleted(
        type: ConversionType,
        durationMs: Long,
        inputSizeBytes: Long = 0L,
        outputSizeBytes: Long,
        compressionRatio: Float = if (inputSizeBytes > 0) outputSizeBytes.toFloat() / inputSizeBytes.toFloat() else 1.0f,
        presetId: String? = null
    ) {
        trackEvent(AnalyticsEvent.ConversionCompleted(type, durationMs, inputSizeBytes, outputSizeBytes, compressionRatio, presetId))
    }

    fun logConversionFailed(type: ConversionType, errorType: String, errorMessage: String, presetId: String? = null) {
        trackEvent(AnalyticsEvent.ConversionFailed(type, errorType, errorMessage, presetId))
    }

    fun logPresetSelected(presetName: String, category: MediaCategory = MediaCategory.IMAGE) {
        trackEvent(AnalyticsEvent.PresetSelected(presetName, category))
    }

    fun logAdImpression(adFormat: String, placement: String) {
        trackEvent(AnalyticsEvent.AdImpression(adFormat, placement))
    }

    fun logAdRewardGranted(rewardType: String, durationMinutes: Int) {
        trackEvent(AnalyticsEvent.AdRewardGranted(rewardType, durationMinutes))
    }

    fun logShareAction(format: String, outputSizeBytes: Long) {
        trackEvent(AnalyticsEvent.ShareAction(format, outputSizeBytes))
    }
}

class NoOpAnalyticsTracker : AnalyticsTracker {
    override fun trackEvent(event: AnalyticsEvent) = Unit
}

