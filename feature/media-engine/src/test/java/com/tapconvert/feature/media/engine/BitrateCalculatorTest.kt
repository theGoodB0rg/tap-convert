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
        assertThat(spec60s.videoBitrateBps).isAtLeast(1_000_000) // ~1.8 Mbps
        assertThat(spec60s.recommendedMaxDimension).isAtLeast(1280) // 720p+
        assertThat(spec60s.targetHeight).isAtLeast(720)

        // 180-second video (3 minutes)
        val spec180s = BitrateCalculator.calculateTargetBitrate(target16Mb, durationSeconds = 180.0)
        assertThat(spec180s.videoBitrateBps).isAtLeast(400_000) // ~500 kbps
        assertThat(spec180s.recommendedMaxDimension).isAtLeast(854) // 480p
        assertThat(spec180s.targetHeight).isAtLeast(480)

        // 600-second video (10 minutes)
        val spec600s = BitrateCalculator.calculateTargetBitrate(target16Mb, durationSeconds = 600.0)
        assertThat(spec600s.videoBitrateBps).isAtLeast(BitrateCalculator.MIN_VIDEO_BITRATE_BPS)
        assertThat(spec600s.videoBitrateBps).isLessThan(200_000)
        assertThat(spec600s.recommendedMaxDimension).isEqualTo(640) // 360p recommended
        assertThat(spec600s.targetHeight).isEqualTo(360)
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
        assertThat(spec10Pct.targetHeight).isAtMost(480) // Downscaled to 360p or 480p
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
        assertThat(spec50Pct.recommendedMaxDimension).isEqualTo(1280) // 720p for 50%
        assertThat(spec50Pct.targetHeight).isEqualTo(720)
    }

    @Test
    fun `calculateTargetBitrate clamps to max video bitrate on very short videos`() {
        val target25Mb = TargetSize.fromMegabytes(25)
        val spec5s = BitrateCalculator.calculateTargetBitrate(target25Mb, durationSeconds = 5.0)

        assertThat(spec5s.videoBitrateBps).isAtMost(BitrateCalculator.MAX_VIDEO_BITRATE_BPS)
        assertThat(spec5s.recommendedMaxDimension).isEqualTo(1920)
        assertThat(spec5s.targetHeight).isEqualTo(1080)
    }

    @Test
    fun `audio bitrate adapts downward on low bitrate budget allocations`() {
        val smallTarget = TargetSize.fromMegabytes(1) // 1MB
        val spec = BitrateCalculator.calculateTargetBitrate(smallTarget, durationSeconds = 120.0)

        assertThat(spec.audioBitrateBps).isEqualTo(32_000) // Dropped to 32 kbps on ultra-tight budget
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
                sourceWidth = 1920,
                sourceHeight = 1080,
                quality = ConversionQuality.Custom(pct)
            )

            // Monotonicity: Each step must be >= the previous step
            assertThat(spec.videoBitrateBps).isAtLeast(previousBitrate)
            assertThat(spec.estimatedTotalSizeBytes).isAtLeast(previousSize)
            assertThat(spec.recommendedMaxDimension).isAtLeast(previousDimension)

            // Even dimension constraint
            assertThat(spec.targetWidth % 2).isEqualTo(0)
            assertThat(spec.targetHeight % 2).isEqualTo(0)
            assertThat(spec.targetWidth).isAtLeast(144)
            assertThat(spec.targetHeight).isAtLeast(144)

            previousBitrate = spec.videoBitrateBps
            previousSize = spec.estimatedTotalSizeBytes
            previousDimension = spec.recommendedMaxDimension
        }
    }

    @Test
    fun `calculateTargetDimensions preserves portrait aspect ratio without unintended micro-scaling`() {
        // Portrait 1080x1920 video (e.g. TikTok / Reel)
        val (w, h) = BitrateCalculator.calculateTargetDimensions(
            sourceWidth = 1080,
            sourceHeight = 1920,
            maxAllowedDimension = 1280
        )

        // Height should be scaled to 1280, width should be 720
        assertThat(h).isEqualTo(1280)
        assertThat(w).isEqualTo(720)
        assertThat(w % 2).isEqualTo(0)
        assertThat(h % 2).isEqualTo(0)
    }

    @Test
    fun `calculateTargetDimensions preserves ultrawide 21-9 aspect ratio and aligns macroblocks`() {
        // Ultrawide 2560x1080
        val (w, h) = BitrateCalculator.calculateTargetDimensions(
            sourceWidth = 2560,
            sourceHeight = 1080,
            maxAllowedDimension = 1920
        )

        assertThat(w).isEqualTo(1920)
        assertThat(h).isEqualTo(810)
        assertThat(w % 2).isEqualTo(0)
        assertThat(h % 2).isEqualTo(0)
    }

    @Test
    fun `calculateTargetDimensions handles odd pixel dimensions gracefully`() {
        // Odd resolution 1079x1919
        val (w, h) = BitrateCalculator.calculateTargetDimensions(
            sourceWidth = 1079,
            sourceHeight = 1919,
            maxAllowedDimension = 1280
        )

        assertThat(w % 2).isEqualTo(0)
        assertThat(h % 2).isEqualTo(0)
        assertThat(w).isAtLeast(144)
        assertThat(h).isAtLeast(144)
    }

    @Test
    fun `createCompensatedSpec generates lower bitrate and steps down resolution appropriately`() {
        val originalSpec = BitrateCalculator.calculateTargetBitrate(
            targetSize = TargetSize.fromMegabytes(16),
            durationSeconds = 60.0,
            sourceSizeBytes = 40 * 1024 * 1024L,
            sourceWidth = 1920,
            sourceHeight = 1080,
            quality = ConversionQuality.Medium
        )

        val compensated = BitrateCalculator.createCompensatedSpec(originalSpec, reductionFactor = 0.80)

        assertThat(compensated.videoBitrateBps).isLessThan(originalSpec.videoBitrateBps)
        assertThat(compensated.estimatedTotalSizeBytes).isLessThan(originalSpec.estimatedTotalSizeBytes)
        assertThat(compensated.targetWidth % 2).isEqualTo(0)
        assertThat(compensated.targetHeight % 2).isEqualTo(0)
    }

    @Test
    fun `effectiveTargetBytes is strictly bounded by quality percent of source`() {
        val sourceSize = 50 * 1024 * 1024L // 50 MB
        val targetSize = TargetSize.fromMegabytes(25) // 25 MB

        val spec40Pct = BitrateCalculator.calculateTargetBitrate(
            targetSize = targetSize,
            durationSeconds = 60.0,
            sourceSizeBytes = sourceSize,
            quality = ConversionQuality.Custom(40) // 40% of 50MB = 20MB (< 25MB target)
        )

        assertThat(spec40Pct.effectiveTargetBytes).isEqualTo((sourceSize * 0.40).toLong())
    }

    @Test
    fun `custom quality slider percentage takes precedence over preset target size when user chooses higher quality`() {
        val sourceSize = 100 * 1024 * 1024L // 100 MB
        val target16Mb = TargetSize.fromMegabytes(16) // 16 MB WhatsApp target

        // User dragged slider up to 40% (40 MB target)
        val spec40Pct = BitrateCalculator.calculateTargetBitrate(
            targetSize = target16Mb,
            durationSeconds = 60.0,
            sourceSizeBytes = sourceSize,
            quality = ConversionQuality.Custom(40)
        )

        // Effective budget should reflect the user's explicit 40% (40 MB), not be silently clamped to 16 MB
        val expected40Mb = (sourceSize * 0.40).toLong()
        assertThat(spec40Pct.effectiveTargetBytes).isEqualTo(expected40Mb)
        assertThat(spec40Pct.videoBitrateBps).isGreaterThan(BitrateCalculator.calculateTargetBitrate(target16Mb, 60.0).videoBitrateBps)
    }

    @Test
    fun `compression favoring guarantees max video bitrate prevents no-op on bloated camera files`() {
        val largeSource = 500 * 1024 * 1024L // 500 MB 1080p camera recording
        val duration60s = 60.0 // ~66 Mbps raw camera bitrate

        val spec100Pct = BitrateCalculator.calculateTargetBitrate(
            targetSize = null,
            durationSeconds = duration60s,
            sourceSizeBytes = largeSource,
            quality = ConversionQuality.Original // 100%
        )

        // Maximum video bitrate must be capped (e.g. <= 8 Mbps) so user always gets meaningful compression
        assertThat(spec100Pct.videoBitrateBps).isAtMost(BitrateCalculator.MAX_VIDEO_BITRATE_BPS)
        assertThat(spec100Pct.videoBitrateBps).isAtMost(8_000_000)
    }
}

