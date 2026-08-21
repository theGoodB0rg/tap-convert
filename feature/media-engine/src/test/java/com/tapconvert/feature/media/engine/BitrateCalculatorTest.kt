package com.tapconvert.feature.media.engine

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.model.TargetSize
import org.junit.Test

class BitrateCalculatorTest {

    @Test
    fun `calculateTargetBitrate calculates appropriate bitrates for 16MB WhatsApp video`() {
        val target16Mb = TargetSize.fromMegabytes(16)

        // 60-second video
        val spec60s = BitrateCalculator.calculateTargetBitrate(target16Mb, durationSeconds = 60.0)
        assertThat(spec60s.videoBitrateBps).isAtLeast(1_000_000) // ~2.0 Mbps
        assertThat(spec60s.recommendedMaxDimension).isAtLeast(1280) // 720p

        // 180-second video (3 minutes)
        val spec180s = BitrateCalculator.calculateTargetBitrate(target16Mb, durationSeconds = 180.0)
        assertThat(spec180s.videoBitrateBps).isAtLeast(500_000) // ~600 kbps
        assertThat(spec180s.recommendedMaxDimension).isEqualTo(854) // 480p recommended for quality

        // 600-second video (10 minutes)
        val spec600s = BitrateCalculator.calculateTargetBitrate(target16Mb, durationSeconds = 600.0)
        assertThat(spec600s.videoBitrateBps).isEqualTo(BitrateCalculator.MIN_VIDEO_BITRATE_BPS) // Clamped to floor
        assertThat(spec600s.recommendedMaxDimension).isEqualTo(640) // 360p recommended
    }

    @Test
    fun `calculateTargetBitrate clamps to max video bitrate on very short videos`() {
        val target25Mb = TargetSize.fromMegabytes(25)
        val spec5s = BitrateCalculator.calculateTargetBitrate(target25Mb, durationSeconds = 5.0)

        assertThat(spec5s.videoBitrateBps).isAtMost(BitrateCalculator.MAX_VIDEO_BITRATE_BPS)
        assertThat(spec5s.recommendedMaxDimension).isEqualTo(1920) // 1080p
    }

    @Test
    fun `audio bitrate adapts downward on low bitrate budget allocations`() {
        val smallTarget = TargetSize.fromMegabytes(1) // 1MB
        val spec = BitrateCalculator.calculateTargetBitrate(smallTarget, durationSeconds = 120.0)

        assertThat(spec.audioBitrateBps).isEqualTo(64_000) // Dropped to 64 kbps to save video quality
    }
}
