package com.tapconvert.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class TargetSizeTest {

    @Test
    fun `factory methods create correct byte representations`() {
        val fromKb = TargetSize.fromKilobytes(200)
        assertThat(fromKb.bytes).isEqualTo(200 * 1024L)

        val fromMb = TargetSize.fromMegabytes(16)
        assertThat(fromMb.bytes).isEqualTo(16 * 1024L * 1024L)

        val fromGb = TargetSize.fromGigabytes(1)
        assertThat(fromGb.bytes).isEqualTo(1024L * 1024L * 1024L)
    }

    @Test
    fun `formatted returns clean readable strings`() {
        assertThat(TargetSize.fromKilobytes(200).formatted()).isEqualTo("200 KB")
        assertThat(TargetSize.fromMegabytes(16).formatted()).isEqualTo("16 MB")
        assertThat(TargetSize.fromMegabytes(25).formatted()).isEqualTo("25 MB")
        assertThat(TargetSize.fromBytes(500).formatted()).isEqualTo("500 B")
        assertThat(TargetSize(1572864).formatted()).isEqualTo("1.5 MB") // 1.5 MB
    }

    @Test
    fun `tolerance bounds and check work as expected`() {
        val target = TargetSize.fromKilobytes(100, tolerancePercent = 0.05) // 100 KB +- 5% (95KB to 105KB)
        val exactBytes = 100 * 1024L

        assertThat(target.isWithinTolerance(exactBytes)).isTrue()
        assertThat(target.isWithinTolerance((95 * 1024L))).isTrue()
        assertThat(target.isWithinTolerance((105 * 1024L))).isTrue()
        assertThat(target.isWithinTolerance((90 * 1024L))).isFalse()
        assertThat(target.isWithinTolerance((110 * 1024L))).isFalse()

        assertThat(target.isUnderLimit(exactBytes)).isTrue()
        assertThat(target.isUnderLimit(exactBytes + 1)).isFalse()
    }

    @Test
    fun `invalid arguments throw IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            TargetSize(0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TargetSize(-100L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TargetSize(1024L, tolerancePercent = -0.1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TargetSize(1024L, tolerancePercent = 0.6)
        }
    }

    @Test
    fun `comparable sorts target sizes in ascending order`() {
        val small = TargetSize.fromKilobytes(100)
        val medium = TargetSize.fromMegabytes(10)
        val large = TargetSize.fromGigabytes(1)

        val list = listOf(large, small, medium).sorted()
        assertThat(list).containsExactly(small, medium, large).inOrder()
    }
}
