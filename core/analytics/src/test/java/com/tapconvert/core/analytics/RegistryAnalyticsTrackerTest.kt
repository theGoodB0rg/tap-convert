package com.tapconvert.core.analytics

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.common.diagnostics.AppObservabilityRegistry
import org.junit.Test

class RegistryAnalyticsTrackerTest {
    @Test
    fun `forwards analytics events to bounded local diagnostics`() {
        val registry = AppObservabilityRegistry()
        RegistryAnalyticsTracker(registry).trackEvent(
            AnalyticsEvent.Custom("video_compression_contract", mapOf("output_bytes" to 123L))
        )

        assertThat(registry.createSnapshot().recentEvents.single().name)
            .isEqualTo("video_compression_contract")
    }
}
