package com.tapconvert.core.testing

import com.tapconvert.core.analytics.AnalyticsEvent
import com.tapconvert.core.analytics.AnalyticsTracker
import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MediaCategory

class FakeAnalyticsTracker : AnalyticsTracker {
    val allEvents = mutableListOf<AnalyticsEvent>()
    val startedConversions = mutableListOf<AnalyticsEvent.ConversionStarted>()
    val completedConversions = mutableListOf<AnalyticsEvent.ConversionCompleted>()
    val failedConversions = mutableListOf<AnalyticsEvent.ConversionFailed>()
    val selectedPresets = mutableListOf<AnalyticsEvent.PresetSelected>()
    val adImpressions = mutableListOf<AnalyticsEvent.AdImpression>()
    val rewardsGranted = mutableListOf<AnalyticsEvent.AdRewardGranted>()
    val shareActions = mutableListOf<AnalyticsEvent.ShareAction>()

    override fun trackEvent(event: AnalyticsEvent) {
        allEvents.add(event)
        when (event) {
            is AnalyticsEvent.ConversionStarted -> startedConversions.add(event)
            is AnalyticsEvent.ConversionCompleted -> completedConversions.add(event)
            is AnalyticsEvent.ConversionFailed -> failedConversions.add(event)
            is AnalyticsEvent.PresetSelected -> selectedPresets.add(event)
            is AnalyticsEvent.AdImpression -> adImpressions.add(event)
            is AnalyticsEvent.AdRewardGranted -> rewardsGranted.add(event)
            is AnalyticsEvent.ShareAction -> shareActions.add(event)
            is AnalyticsEvent.Custom -> Unit
        }
    }

    fun hasCompletedConversionFor(type: ConversionType): Boolean =
        completedConversions.any { it.conversionType == type }

    fun hasSelectedPreset(presetId: String): Boolean =
        selectedPresets.any { it.presetId == presetId }

    fun clear() {
        allEvents.clear()
        startedConversions.clear()
        completedConversions.clear()
        failedConversions.clear()
        selectedPresets.clear()
        adImpressions.clear()
        rewardsGranted.clear()
        shareActions.clear()
    }
}

