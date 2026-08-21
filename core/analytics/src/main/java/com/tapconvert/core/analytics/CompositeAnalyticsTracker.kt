package com.tapconvert.core.analytics

class CompositeAnalyticsTracker(
    private val trackers: List<AnalyticsTracker>
) : AnalyticsTracker {

    constructor(vararg trackers: AnalyticsTracker) : this(trackers.toList())

    override fun trackEvent(event: AnalyticsEvent) {
        trackers.forEach { tracker ->
            try {
                tracker.trackEvent(event)
            } catch (_: Throwable) {
                // Individual tracker failure must not crash the app or interrupt conversion
            }
        }
    }
}
