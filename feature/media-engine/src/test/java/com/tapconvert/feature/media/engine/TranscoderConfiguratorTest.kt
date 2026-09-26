package com.tapconvert.feature.media.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TranscoderConfiguratorTest {

    @Test
    fun `alignToMacroblock aligns dimensions to 16-pixel boundary`() {
        assertThat(TranscoderConfigurator.alignToMacroblock(1920) % 16).isEqualTo(0)
        assertThat(TranscoderConfigurator.alignToMacroblock(1080) % 16).isEqualTo(0)
        assertThat(TranscoderConfigurator.alignToMacroblock(720) % 16).isEqualTo(0)
        assertThat(TranscoderConfigurator.alignToMacroblock(1280) % 16).isEqualTo(0)

        // Odd numbers aligned properly
        val alignedOdd = TranscoderConfigurator.alignToMacroblock(853)
        assertThat(alignedOdd % 16).isEqualTo(0)
        assertThat(alignedOdd).isAtLeast(144)

        // Small dimensions clamped to min 144
        val alignedSmall = TranscoderConfigurator.alignToMacroblock(100)
        assertThat(alignedSmall).isAtLeast(144)
        assertThat(alignedSmall % 16).isEqualTo(0)

        // Edge case: zero or negative
        val alignedZero = TranscoderConfigurator.alignToMacroblock(0)
        assertThat(alignedZero).isAtLeast(144)
        assertThat(alignedZero % 16).isEqualTo(0)
    }

    @Test
    fun `createEncoderFormat creates non-null MediaFormat without throwing`() {
        val format = TranscoderConfigurator.createEncoderFormat(
            targetWidth = 1280,
            targetHeight = 720,
            bitrateBps = 2_000_000,
            frameRate = 30,
            iFrameIntervalSeconds = 2
        )
        assertThat(format).isNotNull()
    }
}
