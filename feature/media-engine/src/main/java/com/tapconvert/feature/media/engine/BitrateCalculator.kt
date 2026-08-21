package com.tapconvert.feature.media.engine

import com.tapconvert.core.model.TargetSize
import kotlin.math.roundToInt

object BitrateCalculator {

    data class VideoEncodingSpec(
        val videoBitrateBps: Int,
        val audioBitrateBps: Int,
        val recommendedMaxDimension: Int,
        val estimatedTotalSizeBytes: Long
    )

    const val DEFAULT_AUDIO_BITRATE_BPS = 128_000 // 128 kbps
    const val MIN_VIDEO_BITRATE_BPS = 250_000     // 250 kbps
    const val MAX_VIDEO_BITRATE_BPS = 8_000_000   // 8 Mbps

    /**
     * Calculates the optimal video and audio bitrate to fit within targetSize.bytes over durationSeconds.
     */
    fun calculateTargetBitrate(
        targetSize: TargetSize,
        durationSeconds: Double,
        audioBitrateBps: Int = DEFAULT_AUDIO_BITRATE_BPS,
        containerOverheadPercent: Double = 0.04
    ): VideoEncodingSpec {
        val safeDuration = durationSeconds.coerceAtLeast(1.0)
        val usableBits = (targetSize.bytes * 8.0 * (1.0 - containerOverheadPercent)).toLong()
        val totalBitrateBps = (usableBits / safeDuration).roundToInt()

        val safeAudioBitrate = if (totalBitrateBps < 400_000) {
            64_000 // Reduce audio to 64kbps on low bitrate allocations
        } else if (totalBitrateBps < 600_000) {
            96_000
        } else {
            audioBitrateBps
        }

        val rawVideoBitrate = totalBitrateBps - safeAudioBitrate
        val finalVideoBitrate = rawVideoBitrate.coerceIn(MIN_VIDEO_BITRATE_BPS, MAX_VIDEO_BITRATE_BPS)

        val recommendedMaxDimension = when {
            finalVideoBitrate >= 3_000_000 -> 1920 // 1080p
            finalVideoBitrate >= 1_200_000 -> 1280 // 720p
            finalVideoBitrate >= 500_000 -> 854    // 480p
            else -> 640                            // 360p
        }

        val estimatedSizeBytes = (((finalVideoBitrate + safeAudioBitrate) * safeDuration / 8.0) * (1.0 + containerOverheadPercent)).toLong()

        return VideoEncodingSpec(
            videoBitrateBps = finalVideoBitrate,
            audioBitrateBps = safeAudioBitrate,
            recommendedMaxDimension = recommendedMaxDimension,
            estimatedTotalSizeBytes = estimatedSizeBytes
        )
    }
}
