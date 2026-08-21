package com.tapconvert.core.testing

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.model.ConversionType
import org.junit.Test

class FakeAnalyticsTrackerTest {

    private val tracker = FakeAnalyticsTracker()

    @Test
    fun `records conversion lifecycle events accurately`() {
        tracker.logConversionStarted(ConversionType.IMAGE_COMPRESS, 1024L)
        tracker.logConversionCompleted(ConversionType.IMAGE_COMPRESS, 120L, 256L)
        tracker.logPresetSelected("WhatsAppVideo16MB")
        tracker.logAdImpression("BANNER", "Dashboard")

        assertThat(tracker.startedConversions).containsExactly(ConversionType.IMAGE_COMPRESS to 1024L)
        assertThat(tracker.completedConversions).containsExactly(Triple(ConversionType.IMAGE_COMPRESS, 120L, 256L))
        assertThat(tracker.selectedPresets).containsExactly("WhatsAppVideo16MB")
        assertThat(tracker.adImpressions).containsExactly("BANNER" to "Dashboard")
    }
}
