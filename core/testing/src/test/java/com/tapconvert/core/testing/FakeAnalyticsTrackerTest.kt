package com.tapconvert.core.testing

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MediaCategory
import org.junit.Test

class FakeAnalyticsTrackerTest {

    private val tracker = FakeAnalyticsTracker()

    @Test
    fun `records conversion lifecycle events accurately`() {
        tracker.logConversionStarted(ConversionType.IMAGE_COMPRESS, 1024L, sourceFormat = "image/png", presetId = "gov_passport_200kb")
        tracker.logConversionCompleted(ConversionType.IMAGE_COMPRESS, 120L, inputSizeBytes = 1024L, outputSizeBytes = 256L, presetId = "gov_passport_200kb")
        tracker.logPresetSelected("gov_passport_200kb", MediaCategory.IMAGE)
        tracker.logAdImpression("BANNER", "Dashboard")
        tracker.logAdRewardGranted("BATCH_PRO", 60)
        tracker.logShareAction("image/jpeg", 256L)

        assertThat(tracker.startedConversions).hasSize(1)
        assertThat(tracker.startedConversions[0].conversionType).isEqualTo(ConversionType.IMAGE_COMPRESS)
        assertThat(tracker.startedConversions[0].inputSizeBytes).isEqualTo(1024L)

        assertThat(tracker.completedConversions).hasSize(1)
        assertThat(tracker.completedConversions[0].conversionType).isEqualTo(ConversionType.IMAGE_COMPRESS)
        assertThat(tracker.completedConversions[0].durationMs).isEqualTo(120L)
        assertThat(tracker.completedConversions[0].outputSizeBytes).isEqualTo(256L)

        assertThat(tracker.hasCompletedConversionFor(ConversionType.IMAGE_COMPRESS)).isTrue()
        assertThat(tracker.hasCompletedConversionFor(ConversionType.VIDEO_COMPRESS)).isFalse()

        assertThat(tracker.hasSelectedPreset("gov_passport_200kb")).isTrue()
        assertThat(tracker.hasSelectedPreset("whatsapp_video_16mb")).isFalse()

        assertThat(tracker.adImpressions).hasSize(1)
        assertThat(tracker.rewardsGranted).hasSize(1)
        assertThat(tracker.shareActions).hasSize(1)
    }

    @Test
    fun `clear resets all recorded events`() {
        tracker.logAdImpression("INTERSTITIAL", "Result")
        assertThat(tracker.allEvents).isNotEmpty()

        tracker.clear()
        assertThat(tracker.allEvents).isEmpty()
        assertThat(tracker.adImpressions).isEmpty()
    }
}

