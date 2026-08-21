package com.tapconvert.core.ads

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeBillingManager(
    initialStatus: SubscriptionStatus = SubscriptionStatus()
) : BillingManager {

    private val _subscriptionStatus = MutableStateFlow(initialStatus)
    override val subscriptionStatus: StateFlow<SubscriptionStatus> = _subscriptionStatus.asStateFlow()

    override fun purchase(activity: Activity?, plan: SubscriptionPlan) {
        _subscriptionStatus.value = SubscriptionStatus(
            isPro = true,
            tier = plan.tier,
            expiryTimestampMs = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000L)
        )
    }

    override fun restorePurchases() {
        // In fake implementation, no-op or maintains current state
    }

    fun setPro(isPro: Boolean, tier: SubscriptionTier = if (isPro) SubscriptionTier.PRO_ANNUAL else SubscriptionTier.FREE) {
        _subscriptionStatus.value = SubscriptionStatus(
            isPro = isPro,
            tier = tier,
            expiryTimestampMs = if (isPro) System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000L) else null
        )
    }
}
