package com.tapconvert.core.common

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LifetimeStatsManagerTest {

    @Test
    fun `initial state returns 0 reclaimed bytes and 0 conversions`() = runTest {
        val manager = InMemoryLifetimeStatsManager()
        assertThat(manager.lifetimeReclaimedBytes.first()).isEqualTo(0L)
        assertThat(manager.lifetimeConversionsCount.first()).isEqualTo(0)
    }

    @Test
    fun `recordConversion accumulates reclaimed bytes and increments count`() = runTest {
        val manager = InMemoryLifetimeStatsManager()

        // 10 MB -> 2 MB (8 MB saved)
        manager.recordConversion(10_000_000L, 2_000_000L)
        assertThat(manager.lifetimeReclaimedBytes.first()).isEqualTo(8_000_000L)
        assertThat(manager.lifetimeConversionsCount.first()).isEqualTo(1)

        // 5 MB -> 1 MB (4 MB saved)
        manager.recordConversion(5_000_000L, 1_000_000L)
        assertThat(manager.lifetimeReclaimedBytes.first()).isEqualTo(12_000_000L)
        assertThat(manager.lifetimeConversionsCount.first()).isEqualTo(2)
    }

    @Test
    fun `recordConversion with larger output size does not decrement reclaimed bytes`() = runTest {
        val manager = InMemoryLifetimeStatsManager()

        // 2 MB -> 3 MB (0 saved)
        manager.recordConversion(2_000_000L, 3_000_000L)
        assertThat(manager.lifetimeReclaimedBytes.first()).isEqualTo(0L)
        assertThat(manager.lifetimeConversionsCount.first()).isEqualTo(1)
    }
}
