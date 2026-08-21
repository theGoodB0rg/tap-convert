package com.tapconvert.core.analytics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CompositeAnalyticsTrackerTest {

    private class TestTracker : AnalyticsTracker {
        val events = mutableListOf<AnalyticsEvent>()
        override fun trackEvent(event: AnalyticsEvent) {
            events.add(event)
        }
    }

    private class FaultyTracker : AnalyticsTracker {
        override fun trackEvent(event: AnalyticsEvent) {
            throw IllegalStateException("Network unreachable")
        }
    }

    @Test
    fun `dispatches event to all child trackers`() {
        val t1 = TestTracker()
        val t2 = TestTracker()
        val composite = CompositeAnalyticsTracker(t1, t2)

        val event = AnalyticsEvent.AdImpression("BANNER", "Home")
        composite.trackEvent(event)

        assertThat(t1.events).containsExactly(event)
        assertThat(t2.events).containsExactly(event)
    }

    @Test
    fun `faulty child tracker does not crash composite or prevent subsequent trackers from receiving event`() {
        val t1 = FaultyTracker()
        val t2 = TestTracker()
        val composite = CompositeAnalyticsTracker(t1, t2)

        val event = AnalyticsEvent.AdImpression("REWARDED", "BatchUnlock")
        composite.trackEvent(event)

        assertThat(t2.events).containsExactly(event)
    }
}
