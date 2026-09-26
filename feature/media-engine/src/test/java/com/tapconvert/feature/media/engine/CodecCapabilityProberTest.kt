package com.tapconvert.feature.media.engine

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class CodecCapabilityProberTest {

    @After
    fun tearDown() {
        CodecCapabilityProber.clearCache()
    }

    @Test
    fun `probeAvcEncoder returns valid profile and caches result`() {
        val profile1 = CodecCapabilityProber.probeAvcEncoder()
        assertThat(profile1).isNotNull()
        assertThat(profile1.maxSupportedWidth).isAtLeast(144)
        assertThat(profile1.maxSupportedHeight).isAtLeast(144)

        val profile2 = CodecCapabilityProber.probeAvcEncoder()
        assertThat(profile2).isSameInstanceAs(profile1)

        CodecCapabilityProber.clearCache()
        val profile3 = CodecCapabilityProber.probeAvcEncoder()
        assertThat(profile3).isNotNull()
    }
}
