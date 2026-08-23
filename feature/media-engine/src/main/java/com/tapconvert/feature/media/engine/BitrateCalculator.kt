package com.tapconvert.feature.media.engine

import com.tapconvert.core.model.ConversionQuality
import com.tapconvert.core.model.TargetSize
import kotlin.math.min
import kotlin.math.roundToInt

object BitrateCalculator {

    data class VideoEncodingSpec(
        val videoBitrateBps: Int,
        val audioBitrateBps: Int,
        val recommendedMaxDimension: Int,
        val estimatedTotalSizeBytes: Long
    )

    const val DEFAULT_AUDIO_BITRATE_BPS = 128_000 // 128 kbps
    const val MIN_VIDEO_BITRATE_BPS = 150_000     // 150 kbps
    const val MAX_VIDEO_BITRATE_BPS = 8_000_000   // 8 Mbps

    /**
     * Calculates the optimal video and audio bitrate and resolution to fit within targetSize or quality constraints.
     */
    fun calculateTargetBitrate(
        targetSize: TargetSize? = null,
        durationSeconds: Double = 60.0,
        sourceSizeBytes: Long = 0L,
        sourceHeight: Int = 1080,
        quality: ConversionQuality = ConversionQuality.Medium,
        audioBitrateBps: Int = DEFAULT_AUDIO_BITRATE_BPS,
        containerOverheadPercent: Double = 0.04
    ): VideoEncodingSpec {
        val safeDuration = durationSeconds.coerceAtLeast(1.0)
        val qualityPct = quality.qualityPercent.coerceIn(5, 100)

        val effectiveTargetBytes: Long = when {
            targetSize != null && sourceSizeBytes > 0L -> {
                val sourceBudget = (sourceSizeBytes * (qualityPct / 100.0)).toLong()
                min(targetSize.bytes, sourceBudget).coerceAtLeast(200_000L)
            }
            targetSize != null -> targetSize.bytes
            sourceSizeBytes > 0L -> ((sourceSizeBytes * (qualityPct / 100.0)).toLong()).coerceAtLeast(200_000L)
            else -> TargetSize.fromMegabytes(16).bytes
        }

        val usableBits = (effectiveTargetBytes * 8.0 * (1.0 - containerOverheadPercent)).toLong()
        val targetTotalBitrateBps = (usableBits / safeDuration).roundToInt()

        // If source size and duration are known, calculate source baseline bitrate
        val sourceBitrateBps = if (sourceSizeBytes > 0L) {
            ((sourceSizeBytes * 8.0) / safeDuration).roundToInt()
        } else {
            MAX_VIDEO_BITRATE_BPS
        }

        val safeAudioBitrate = when {
            targetTotalBitrateBps < 300_000 -> 48_000
            targetTotalBitrateBps < 500_000 -> 64_000
            targetTotalBitrateBps < 800_000 -> 96_000
            else -> audioBitrateBps
        }

        val rawVideoBitrate = (targetTotalBitrateBps - safeAudioBitrate).coerceAtLeast(MIN_VIDEO_BITRATE_BPS)

        // Universal Bitrate Ceiling:
        // When source size is known, output video bitrate must never exceed source video bitrate scaled by quality percentage across ALL modes
        val boundedBySourceBitrate = if (sourceSizeBytes > 0L) {
            val sourceVideoBitrateEstimate = (sourceBitrateBps - safeAudioBitrate).coerceAtLeast(MIN_VIDEO_BITRATE_BPS)
            val scaledBitrate = (sourceVideoBitrateEstimate * (qualityPct / 100.0)).roundToInt().coerceAtLeast(MIN_VIDEO_BITRATE_BPS)
            min(rawVideoBitrate, scaledBitrate)
        } else {
            rawVideoBitrate
        }

        val finalVideoBitrate = boundedBySourceBitrate.coerceIn(MIN_VIDEO_BITRATE_BPS, MAX_VIDEO_BITRATE_BPS)

        // Determine recommended height dimension based on bitrate and quality percentage
        val effectiveSourceHeight = if (sourceHeight > 0) sourceHeight else 1080
        val maxAllowedHeight = when {
            finalVideoBitrate < 350_000 || qualityPct <= 20 -> 360
            finalVideoBitrate < 750_000 || qualityPct <= 40 -> 480
            finalVideoBitrate < 2_000_000 || qualityPct <= 70 -> 720
            finalVideoBitrate < 4_500_000 || qualityPct <= 90 -> 1080
            else -> effectiveSourceHeight
        }

        val rawRecommendedHeight = min(effectiveSourceHeight, maxAllowedHeight)
        // Video codecs strictly require even dimensions (multiples of 2)
        val recommendedMaxDimension = ((rawRecommendedHeight / 2) * 2).coerceAtLeast(144)

        val estimatedSizeBytes = (((finalVideoBitrate + safeAudioBitrate) * safeDuration / 8.0) * (1.0 + containerOverheadPercent)).toLong()

        return VideoEncodingSpec(
            videoBitrateBps = finalVideoBitrate,
            audioBitrateBps = safeAudioBitrate,
            recommendedMaxDimension = recommendedMaxDimension,
            estimatedTotalSizeBytes = estimatedSizeBytes
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
        sourceHeight = 1080,
        quality = ConversionQuality.Medium,
        audioBitrateBps = audioBitrateBps
    )
}
