package com.tapconvert.core.billing

import android.app.Activity
import com.tapconvert.core.ads.SubscriptionPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Test/dev double. Same interface as Play impl — keeps :core:ads + :app tests hermetic.
 * Release graph must never bind this.
 */
class FakeEntitlementVerifier(
    initial: EntitlementSnapshot = EntitlementSnapshot.FREE,
    private val clock: () -> Long = System::currentTimeMillis
) : EntitlementVerifier {

    private val _entitlement = MutableStateFlow(initial)
    override val entitlement: StateFlow<EntitlementSnapshot> = _entitlement.asStateFlow()

    var refreshResult: EntitlementSnapshot? = null
    var purchaseResult: Boolean = true
    var lastPurchasedPlan: SubscriptionPlan? = null
        private set

    override suspend fun refresh(activity: Activity?): EntitlementSnapshot {
        val snapshot = refreshResult ?: _entitlement.value.copy(
            queriedAtMs = clock(),
            source = if (_entitlement.value.isPro) EntitlementSource.PLAY_FRESH else EntitlementSource.PLAY_EMPTY
        )
        _entitlement.value = snapshot
        return snapshot
    }

    override suspend fun purchase(activity: Activity, plan: SubscriptionPlan): Boolean {
        lastPurchasedPlan = plan
        return purchaseResult
    }

    override fun clear() {
        _entitlement.value = EntitlementSnapshot.FREE
    }

    fun grantPro(snapshot: EntitlementSnapshot) {
        _entitlement.value = snapshot.copy(queriedAtMs = clock(), source = EntitlementSource.PLAY_FRESH)
    }

    fun revoke() {
        _entitlement.value = EntitlementSnapshot.FREE.copy(queriedAtMs = clock())
    }
}
