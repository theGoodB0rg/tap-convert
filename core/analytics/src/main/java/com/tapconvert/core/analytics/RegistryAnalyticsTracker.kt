package com.tapconvert.core.analytics

import com.tapconvert.core.common.diagnostics.AppObservabilityRegistry

/**
 * Always-on local sink for production diagnostics. It is intentionally provider
 * neutral: a remote Firebase sink can be composed later without removing the
 * local, privacy-safe evidence used to debug failed conversions offline.
 */
class RegistryAnalyticsTracker(
    private val registry: AppObservabilityRegistry = AppObservabilityRegistry.instance
) : AnalyticsTracker {
    override fun trackEvent(event: AnalyticsEvent) {
        registry.recordEvent(event.eventName, event.toParamsMap())
    }
}
