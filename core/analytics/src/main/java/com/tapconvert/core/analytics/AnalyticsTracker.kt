package com.tapconvert.core.analytics

import com.tapconvert.core.model.ConversionType

interface AnalyticsTracker {
    fun logEvent(name: String, params: Map<String, Any> = emptyMap())
    fun logConversionStarted(type: ConversionType, inputSizeBytes: Long)
    fun logConversionCompleted(type: ConversionType, durationMs: Long, outputSizeBytes: Long)
    fun logPresetSelected(presetName: String)
    fun logAdImpression(adFormat: String, placement: String)
}

class NoOpAnalyticsTracker : AnalyticsTracker {
    override fun logEvent(name: String, params: Map<String, Any>) = Unit
    override fun logConversionStarted(type: ConversionType, inputSizeBytes: Long) = Unit
    override fun logConversionCompleted(type: ConversionType, durationMs: Long, outputSizeBytes: Long) = Unit
    override fun logPresetSelected(presetName: String) = Unit
    override fun logAdImpression(adFormat: String, placement: String) = Unit
}
