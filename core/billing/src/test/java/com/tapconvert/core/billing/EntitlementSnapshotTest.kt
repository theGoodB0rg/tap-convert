package com.tapconvert.core.billing

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.ads.SubscriptionTier
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EntitlementSnapshotTest {

    @Test
    fun free_byDefault_notVerified() {
        val s = EntitlementSnapshot.FREE
        assertThat(s.isPro).isFalse()
        assertThat(s.isProVerified(1000L)).isFalse()
    }

    @Test
    fun freshPro_isVerified_stale_isNot() {
        val fresh = EntitlementSnapshot(
            isPro = true, tier = SubscriptionTier.PRO_ANNUAL,
            queriedAtMs = 10_000L, source = EntitlementSource.PLAY_FRESH
        )
        assertThat(fresh.isProVerified(10_000L + EntitlementSnapshot.FRESHNESS_MS - 1)).isTrue()
        assertThat(fresh.isProVerified(10_000L + EntitlementSnapshot.FRESHNESS_MS + 1)).isFalse()
    }

    @Test
    fun offlineOrError_neverGrants() {
        val offline = EntitlementSnapshot(isPro = true, queriedAtMs = 0L, source = EntitlementSource.OFFLINE)
        assertThat(offline.isProVerified(100L)).isFalse()
        val err = EntitlementSnapshot(isPro = true, queriedAtMs = 0L, source = EntitlementSource.ERROR)
        assertThat(err.isProVerified(100L)).isFalse()
    }

    @Test
    fun fakeVerifier_grantAndRevoke() = runTest {
        val fake = FakeEntitlementVerifier()
        assertThat(fake.refresh(null).isPro).isFalse()
        fake.grantPro(EntitlementSnapshot(isPro = true, tier = SubscriptionTier.PRO_LIFETIME))
        assertThat(fake.entitlement.value.isProVerified()).isTrue()
        fake.revoke()
        assertThat(fake.entitlement.value.isPro).isFalse()
    }
}
