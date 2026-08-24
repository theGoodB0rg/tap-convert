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
        val expiryMs = when (plan) {
            is SubscriptionPlan.Lifetime -> null // Lifetime never expires
            is SubscriptionPlan.Annual -> System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000L)
            is SubscriptionPlan.Monthly -> System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L)
        }
        _subscriptionStatus.value = SubscriptionStatus(
            isPro = true,
            tier = plan.tier,
            expiryTimestampMs = expiryMs
        )
    }

    override fun restorePurchases() {
        // In fake implementation, maintains current state
    }

    fun setPro(isPro: Boolean, tier: SubscriptionTier = if (isPro) SubscriptionTier.PRO_ANNUAL else SubscriptionTier.FREE) {
        val expiryMs = when {
            !isPro -> null
            tier == SubscriptionTier.PRO_LIFETIME -> null
            tier == SubscriptionTier.PRO_ANNUAL -> System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000L)
            else -> System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L)
        }
        _subscriptionStatus.value = SubscriptionStatus(
            isPro = isPro,
            tier = tier,
            expiryTimestampMs = expiryMs
        )
    }
}
