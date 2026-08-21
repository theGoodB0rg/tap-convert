package com.tapconvert.core.ads

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

enum class SubscriptionTier {
    FREE,
    PRO_MONTHLY,
    PRO_ANNUAL
}

sealed class SubscriptionPlan(
    val productId: String,
    val priceFormatted: String,
    val tier: SubscriptionTier
) {
    data object Monthly : SubscriptionPlan(
        productId = "tapconvert_pro_monthly",
        priceFormatted = "$0.99/mo",
        tier = SubscriptionTier.PRO_MONTHLY
    )

    data object Annual : SubscriptionPlan(
        productId = "tapconvert_pro_annual",
        priceFormatted = "$9.99/yr",
        tier = SubscriptionTier.PRO_ANNUAL
    )
}

data class SubscriptionStatus(
    val isPro: Boolean = false,
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val expiryTimestampMs: Long? = null
)

interface BillingManager {
    val subscriptionStatus: StateFlow<SubscriptionStatus>
    fun purchase(activity: Activity?, plan: SubscriptionPlan)
    fun restorePurchases()
}
