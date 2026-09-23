package com.tapconvert.core.billing

/**
 * Play product catalog. IDs must match Play Console byte-for-byte.
 * Monthly/Annual are SUBSCRIPTIONS, Lifetime is a ONE-TIME in-app product.
 */
object ProductCatalog {
    const val MONTHLY_ID = "tapconvert_pro_monthly"
    const val ANNUAL_ID = "tapconvert_pro_annual"
    const val LIFETIME_ID = "tapconvert_pro_lifetime"

    val SUBS_IDS = listOf(MONTHLY_ID, ANNUAL_ID)
    const val INAPP_ID = LIFETIME_ID
}
