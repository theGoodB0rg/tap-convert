package com.tapconvert.app.ui.monetization

import com.tapconvert.core.ads.SubscriptionPlan

data class SubscriptionPlanUiModel(
    val plan: SubscriptionPlan,
    val title: String,
    val priceFormatted: String,
    val badge: String? = null,
    val isBestValue: Boolean = false
)

fun SubscriptionPlan.toUiModel(): SubscriptionPlanUiModel {
    return when (this) {
        is SubscriptionPlan.Lifetime -> SubscriptionPlanUiModel(
            plan = this,
            title = "Lifetime",
            priceFormatted = "$19.99",
            badge = "BEST VALUE",
            isBestValue = true
        )
        is SubscriptionPlan.Annual -> SubscriptionPlanUiModel(
            plan = this,
            title = "Annual",
            priceFormatted = "$9.99 / yr",
            badge = "POPULAR",
            isBestValue = false
        )
        is SubscriptionPlan.Monthly -> SubscriptionPlanUiModel(
            plan = this,
            title = "Monthly",
            priceFormatted = "$0.99 / mo",
            badge = null,
            isBestValue = false
        )
    }
}
