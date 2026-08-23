package com.tapconvert.feature.media.engine

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.model.ConversionQuality
import com.tapconvert.core.model.TargetSize
import org.junit.Test

class BitrateCalculatorTest {

    @Test
    fun `calculateTargetBitrate calculates appropriate bitrates for 16MB WhatsApp video`() {
        val target16Mb = TargetSize.fromMegabytes(16)

        // 60-second video
        val spec60s = BitrateCalculator.calculateTargetBitrate(target16Mb, durationSeconds = 60.0)
        assertThat(spec60s.videoBitrateBps).isAtLeast(1_000_000) // ~2.0 Mbps
        assertThat(spec60s.recommendedMaxDimension).isAtLeast(720) // 720p+

        // 180-second video (3 minutes)
        val spec180s = BitrateCalculator.calculateTargetBitrate(target16Mb, durationSeconds = 180.0)
        assertThat(spec180s.videoBitrateBps).isAtLeast(500_000) // ~600 kbps
        assertThat(spec180s.recommendedMaxDimension).isAtLeast(480)

        // 600-second video (10 minutes)
        val spec600s = BitrateCalculator.calculateTargetBitrate(target16Mb, durationSeconds = 600.0)
        assertThat(spec600s.videoBitrateBps).isAtLeast(BitrateCalculator.MIN_VIDEO_BITRATE_BPS)
        assertThat(spec600s.videoBitrateBps).isLessThan(200_000)
        assertThat(spec600s.recommendedMaxDimension).isEqualTo(360) // 360p recommended
    }

    @Test
    fun `calculateTargetBitrate with 10 percent quality scales 32_5MB video to approximately 3_25MB`() {
        val source32_5Mb = 34_078_720L // 32.5 MB in bytes
        val duration30s = 30.0

        val spec10Pct = BitrateCalculator.calculateTargetBitrate(
            targetSize = null,
            durationSeconds = duration30s,
            sourceSizeBytes = source32_5Mb,
            sourceHeight = 1080,
            quality = ConversionQuality.Custom(10)
        )

        // Estimated output should be around 3.25 MB (allowing small overhead variance)
        val expectedTargetBytes = (source32_5Mb * 0.10).toLong()
        assertThat(spec10Pct.estimatedTotalSizeBytes).isLessThan(expectedTargetBytes * 2)
        // Bitrate should be scaled down significantly
        assertThat(spec10Pct.videoBitrateBps).isLessThan(1_000_000) // < 1 Mbps
        assertThat(spec10Pct.recommendedMaxDimension).isAtMost(480) // Downscaled to 360p or 480p
    }

    @Test
    fun `calculateTargetBitrate with 50 percent quality scales 32_5MB video to approximately 16MB`() {
        val source32_5Mb = 34_078_720L // 32.5 MB
        val duration30s = 30.0

        val spec50Pct = BitrateCalculator.calculateTargetBitrate(
            targetSize = null,
            durationSeconds = duration30s,
            sourceSizeBytes = source32_5Mb,
            sourceHeight = 1080,
            quality = ConversionQuality.Custom(50)
        )

        val expectedTargetBytes = (source32_5Mb * 0.50).toLong()
        assertThat(spec50Pct.estimatedTotalSizeBytes).isLessThan((expectedTargetBytes * 1.2).toLong())
        assertThat(spec50Pct.recommendedMaxDimension).isEqualTo(720) // 720p for 50%
    }

    @Test
    fun `calculateTargetBitrate clamps to max video bitrate on very short videos`() {
        val target25Mb = TargetSize.fromMegabytes(25)
        val spec5s = BitrateCalculator.calculateTargetBitrate(target25Mb, durationSeconds = 5.0)

        assertThat(spec5s.videoBitrateBps).isAtMost(BitrateCalculator.MAX_VIDEO_BITRATE_BPS)
        assertThat(spec5s.recommendedMaxDimension).isEqualTo(1080)
    }

    @Test
    fun `audio bitrate adapts downward on low bitrate budget allocations`() {
        val smallTarget = TargetSize.fromMegabytes(1) // 1MB
        val spec = BitrateCalculator.calculateTargetBitrate(smallTarget, durationSeconds = 120.0)

        assertThat(spec.audioBitrateBps).isEqualTo(48_000) // Dropped to 48 kbps on ultra-tight budget
    }

    @Test
    fun `calculateTargetBitrate with small 4MB video and 16MB WhatsApp preset prevents inflation`() {
        val source4Mb = 4 * 1024 * 1024L // 4MB in bytes
        val duration10s = 10.0 // 10 seconds (source video bitrate is approx 3.2 Mbps)
        val target16Mb = TargetSize.fromMegabytes(16)

        val spec = BitrateCalculator.calculateTargetBitrate(
            targetSize = target16Mb,
            durationSeconds = duration10s,
            sourceSizeBytes = source4Mb,
            sourceHeight = 1080,
            quality = ConversionQuality.Medium // 70%
        )

        // Estimated output size MUST NOT exceed original source size
        assertThat(spec.estimatedTotalSizeBytes).isAtMost(source4Mb)
        // Bitrate MUST NOT exceed source bitrate
        val maxSourceBitrateBps = ((source4Mb * 8.0) / duration10s).toInt()
        assertThat(spec.videoBitrateBps).isAtMost(maxSourceBitrateBps)
    }

    @Test
    fun `calculateTargetBitrate with small 1MB video and 25MB Discord preset prevents inflation`() {
        val source1Mb = 1024 * 1024L // 1MB in bytes
        val duration5s = 5.0
        val target25Mb = TargetSize.fromMegabytes(25)

        val spec = BitrateCalculator.calculateTargetBitrate(
            targetSize = target25Mb,
            durationSeconds = duration5s,
            sourceSizeBytes = source1Mb,
            sourceHeight = 720,
            quality = ConversionQuality.High // 90%
        )

        assertThat(spec.estimatedTotalSizeBytes).isAtMost(source1Mb)
    }

    @Test
    fun `calculateTargetBitrate monotonicity test over entire quality spectrum`() {
        val source30Mb = 30 * 1024 * 1024L
        val duration30s = 30.0

        var previousBitrate = 0
        var previousSize = 0L
        var previousDimension = 0

        for (pct in 10..100 step 10) {
            val spec = BitrateCalculator.calculateTargetBitrate(
                targetSize = null,
                durationSeconds = duration30s,
                sourceSizeBytes = source30Mb,
                sourceHeight = 1080,
                quality = ConversionQuality.Custom(pct)
            )

            // Monotonicity: Each step must be >= the previous step
            assertThat(spec.videoBitrateBps).isAtLeast(previousBitrate)
            assertThat(spec.estimatedTotalSizeBytes).isAtLeast(previousSize)
            assertThat(spec.recommendedMaxDimension).isAtLeast(previousDimension)

            // Even dimension constraint
            assertThat(spec.recommendedMaxDimension % 2).isEqualTo(0)
            assertThat(spec.recommendedMaxDimension).isAtLeast(144)

            previousBitrate = spec.videoBitrateBps
            previousSize = spec.estimatedTotalSizeBytes
            previousDimension = spec.recommendedMaxDimension
        }
    }
}
