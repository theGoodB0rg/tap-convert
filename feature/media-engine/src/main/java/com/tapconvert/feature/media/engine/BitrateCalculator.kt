package com.tapconvert.feature.media.engine

import com.tapconvert.core.model.ConversionQuality
import com.tapconvert.core.model.TargetSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object BitrateCalculator {

    data class VideoEncodingSpec(
        val videoBitrateBps: Int,
        val audioBitrateBps: Int,
        val recommendedMaxDimension: Int,
        val targetWidth: Int,
        val targetHeight: Int,
        val estimatedTotalSizeBytes: Long,
        val effectiveTargetBytes: Long
    )

    const val DEFAULT_AUDIO_BITRATE_BPS = 128_000 // 128 kbps
    const val MIN_VIDEO_BITRATE_BPS = 120_000     // 120 kbps
    const val MAX_VIDEO_BITRATE_BPS = 12_000_000  // 12 Mbps
    const val DEFAULT_SAFETY_OVERHEAD = 0.12      // 12% headroom for container & VBR peak jitter

    /**
     * Calculates the optimal video and audio bitrate and resolution to strictly fit within targetSize or quality constraints.
     * Guarantees that output target size never inflates beyond source size scaled by quality percentage.
     */
    fun calculateTargetBitrate(
        targetSize: TargetSize? = null,
        durationSeconds: Double = 60.0,
        sourceSizeBytes: Long = 0L,
        sourceWidth: Int = 1920,
        sourceHeight: Int = 1080,
        quality: ConversionQuality = ConversionQuality.Medium,
        audioBitrateBps: Int = DEFAULT_AUDIO_BITRATE_BPS,
        containerOverheadPercent: Double = DEFAULT_SAFETY_OVERHEAD
    ): VideoEncodingSpec {
        val safeDuration = durationSeconds.coerceAtLeast(0.5)
        val qualityPct = quality.qualityPercent.coerceIn(1, 100)

        // Strict Downward Sizing Guarantee:
        // If source size is known, target bytes must never exceed sourceSizeBytes * (qualityPct / 100)
        val effectiveTargetBytes: Long = when {
            targetSize != null && sourceSizeBytes > 0L -> {
                val sourceBudget = (sourceSizeBytes * (qualityPct / 100.0)).toLong()
                min(targetSize.bytes, sourceBudget).coerceAtLeast(100_000L)
            }
            targetSize != null -> targetSize.bytes.coerceAtLeast(100_000L)
            sourceSizeBytes > 0L -> ((sourceSizeBytes * (qualityPct / 100.0)).toLong()).coerceAtLeast(100_000L)
            else -> TargetSize.fromMegabytes(16).bytes
        }

        val usableBits = (effectiveTargetBytes * 8.0 * (1.0 - containerOverheadPercent.coerceIn(0.02, 0.25))).toLong()
        val targetTotalBitrateBps = (usableBits / safeDuration).roundToInt()

        // Adaptive Audio Bitrate Budgeting based on total available bitrate
        val safeAudioBitrate = when {
            targetTotalBitrateBps < 200_000 -> 32_000
            targetTotalBitrateBps < 350_000 -> 48_000
            targetTotalBitrateBps < 600_000 -> 64_000
            targetTotalBitrateBps < 1_000_000 -> 96_000
            else -> audioBitrateBps.coerceIn(64_000, 192_000)
        }

        val rawVideoBitrate = (targetTotalBitrateBps - safeAudioBitrate).coerceAtLeast(MIN_VIDEO_BITRATE_BPS)

        // Bitrate Ceiling: when source size is known, output video bitrate must never exceed source video bitrate scaled by quality percentage
        val boundedBySourceBitrate = if (sourceSizeBytes > 0L) {
            val sourceBitrateBps = ((sourceSizeBytes * 8.0) / safeDuration).roundToInt()
            val sourceVideoBitrateEstimate = (sourceBitrateBps - safeAudioBitrate).coerceAtLeast(MIN_VIDEO_BITRATE_BPS)
            val scaledCeiling = (sourceVideoBitrateEstimate * (qualityPct / 100.0)).roundToInt().coerceAtLeast(MIN_VIDEO_BITRATE_BPS)
            min(rawVideoBitrate, scaledCeiling)
        } else {
            rawVideoBitrate
        }

        val finalVideoBitrate = boundedBySourceBitrate.coerceIn(MIN_VIDEO_BITRATE_BPS, MAX_VIDEO_BITRATE_BPS)

        // Aspect-ratio-aware bounding box dimension selection
        val safeW = if (sourceWidth > 0) sourceWidth else 1920
        val safeH = if (sourceHeight > 0) sourceHeight else 1080
        val maxSourceDim = max(safeW, safeH)

        val maxAllowedDimension = when {
            finalVideoBitrate < 350_000 || qualityPct <= 20 -> 640
            finalVideoBitrate < 750_000 || qualityPct <= 40 -> 854
            finalVideoBitrate < 2_000_000 || qualityPct <= 70 -> 1280
            finalVideoBitrate < 4_500_000 || qualityPct <= 90 -> 1920
            else -> maxSourceDim
        }

        val recommendedMaxDimension = min(maxSourceDim, maxAllowedDimension)
        val (targetW, targetH) = calculateTargetDimensions(safeW, safeH, recommendedMaxDimension)

        val estimatedSizeBytes = (((finalVideoBitrate + safeAudioBitrate) * safeDuration / 8.0) * (1.0 + containerOverheadPercent)).toLong()

        return VideoEncodingSpec(
            videoBitrateBps = finalVideoBitrate,
            audioBitrateBps = safeAudioBitrate,
            recommendedMaxDimension = recommendedMaxDimension,
            targetWidth = targetW,
            targetHeight = targetH,
            estimatedTotalSizeBytes = estimatedSizeBytes,
            effectiveTargetBytes = effectiveTargetBytes
        )
    }

    /**
     * Calculates target width and height preserving aspect ratio with even dimensions for H.264 macroblock alignment.
     */
    fun calculateTargetDimensions(
        sourceWidth: Int,
        sourceHeight: Int,
        maxAllowedDimension: Int
    ): Pair<Int, Int> {
        val safeW = if (sourceWidth > 0) sourceWidth else 1920
        val safeH = if (sourceHeight > 0) sourceHeight else 1080
        val maxDim = max(safeW, safeH)

        val scale = if (maxDim > maxAllowedDimension && maxAllowedDimension >= 144) {
            maxAllowedDimension.toFloat() / maxDim.toFloat()
        } else {
            1.0f
        }

        val outW = ((safeW * scale).roundToInt() / 2) * 2
        val outH = ((safeH * scale).roundToInt() / 2) * 2

        return Pair(outW.coerceAtLeast(144), outH.coerceAtLeast(144))
    }

    /**
     * Creates a compensated encoding specification with reduced bitrate / resolution
     * for closed-loop retry passes when hardware encoders overshoot.
     */
    fun createCompensatedSpec(
        currentSpec: VideoEncodingSpec,
        reductionFactor: Double = 0.80
    ): VideoEncodingSpec {
        val compensatedVideoBitrate = (currentSpec.videoBitrateBps * reductionFactor).roundToInt().coerceAtLeast(MIN_VIDEO_BITRATE_BPS)
        val compensatedAudioBitrate = if (currentSpec.audioBitrateBps > 64_000 && reductionFactor < 0.85) {
            (currentSpec.audioBitrateBps * 0.75).roundToInt().coerceAtLeast(32_000)
        } else {
            currentSpec.audioBitrateBps
        }

        // Also step down resolution if bitrate is significantly constrained
        val stepDownDim = if (compensatedVideoBitrate < 400_000 && currentSpec.recommendedMaxDimension > 480) {
            480
        } else if (compensatedVideoBitrate < 900_000 && currentSpec.recommendedMaxDimension > 720) {
            720
        } else if (compensatedVideoBitrate < 2_000_000 && currentSpec.recommendedMaxDimension > 1280) {
            1280
        } else {
            currentSpec.recommendedMaxDimension
        }

        val (targetW, targetH) = calculateTargetDimensions(currentSpec.targetWidth, currentSpec.targetHeight, stepDownDim)
        val newEstimatedSize = (currentSpec.estimatedTotalSizeBytes * reductionFactor).toLong()

        return currentSpec.copy(
            videoBitrateBps = compensatedVideoBitrate,
            audioBitrateBps = compensatedAudioBitrate,
            recommendedMaxDimension = stepDownDim,
            targetWidth = targetW,
            targetHeight = targetH,
            estimatedTotalSizeBytes = newEstimatedSize
        )
    }

    /**
     * Backward-compatible overloads.
     */
    fun calculateTargetBitrate(
        targetSize: TargetSize,
        durationSeconds: Double
    ): VideoEncodingSpec = calculateTargetBitrate(
        targetSize = targetSize,
        durationSeconds = durationSeconds,
        sourceSizeBytes = 0L,
        sourceWidth = 1920,
        sourceHeight = 1080,
        quality = ConversionQuality.Medium,
        audioBitrateBps = DEFAULT_AUDIO_BITRATE_BPS
    )

    fun calculateTargetBitrate(
        targetSize: TargetSize,
        durationSeconds: Double,
        audioBitrateBps: Int
    ): VideoEncodingSpec = calculateTargetBitrate(
        targetSize = targetSize,
        durationSeconds = durationSeconds,
        sourceSizeBytes = 0L,
        sourceWidth = 1920,
        sourceHeight = 1080,
        quality = ConversionQuality.Medium,
        audioBitrateBps = audioBitrateBps
    )
}
