package com.tapconvert.core.testing

import com.tapconvert.core.analytics.AnalyticsTracker
import com.tapconvert.core.model.ConversionType

class FakeAnalyticsTracker : AnalyticsTracker {
    val loggedEvents = mutableListOf<Pair<String, Map<String, Any>>>()
    val startedConversions = mutableListOf<Pair<ConversionType, Long>>()
    val completedConversions = mutableListOf<Triple<ConversionType, Long, Long>>()
    val selectedPresets = mutableListOf<String>()
    val adImpressions = mutableListOf<Pair<String, String>>()

    override fun logEvent(name: String, params: Map<String, Any>) {
        loggedEvents.add(name to params)
    }

    override fun logConversionStarted(type: ConversionType, inputSizeBytes: Long) {
        startedConversions.add(type to inputSizeBytes)
    }

    override fun logConversionCompleted(type: ConversionType, durationMs: Long, outputSizeBytes: Long) {
        completedConversions.add(Triple(type, durationMs, outputSizeBytes))
    }

    override fun logPresetSelected(presetName: String) {
        selectedPresets.add(presetName)
    }

    override fun logAdImpression(adFormat: String, placement: String) {
        adImpressions.add(adFormat to placement)
    }

    fun clear() {
        loggedEvents.clear()
        startedConversions.clear()
        completedConversions.clear()
        selectedPresets.clear()
        adImpressions.clear()
    }
}
