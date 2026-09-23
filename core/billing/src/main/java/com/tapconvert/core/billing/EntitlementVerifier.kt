package com.tapconvert.core.billing

import android.app.Activity
import com.tapconvert.core.ads.SubscriptionPlan
import kotlinx.coroutines.flow.StateFlow

/**
 * Play-backed entitlement. Release impl queries BillingClient fresh every time;
 * local cache is a hint for UI only, never grants Pro.
 */
interface EntitlementVerifier {
    val entitlement: StateFlow<EntitlementSnapshot>

    /** Always-online refresh: queries SUBS + INAPP purchases. Never grants on error/offline. */
    suspend fun refresh(activity: Activity? = null): EntitlementSnapshot

    /** Launches Play purchase flow for the given plan. Returns false if Billing unavailable. */
    suspend fun purchase(activity: Activity, plan: SubscriptionPlan): Boolean

    fun clear()
}
