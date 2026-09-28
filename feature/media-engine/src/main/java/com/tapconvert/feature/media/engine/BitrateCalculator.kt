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
    const val CONSERVATIVE_AAC_FLOOR_BPS = 32_000
    // Long videos with tight budgets need a genuinely low floor. A 32 kbps
    // floor alone exceeds a 16 MiB budget for a two-hour source, before any
    // container overhead is added.
    const val MIN_VIDEO_BITRATE_BPS = 8_000       // 8 kbps
    const val MAX_VIDEO_BITRATE_BPS = 8_000_000   // 8 Mbps (avoids bloated camera bitrates, favoring real compression)
    const val DEFAULT_SAFETY_OVERHEAD = 0.15      // 15% headroom for container & VBR peak jitter

    /**
     * Conservative lower bound for a playable result that retains audio.
     * Devices may impose a higher codec floor; this is a warning threshold,
     * not a promise that the target is achievable.
     */
    fun minimumPlayableBytes(
        durationSeconds: Double,
        audioFloorBps: Int = CONSERVATIVE_AAC_FLOOR_BPS,
        videoFloorBps: Int = 4_000,
        containerOverheadPercent: Double = DEFAULT_SAFETY_OVERHEAD
    ): Long {
        val safeDuration = durationSeconds.coerceAtLeast(0.5)
        val payloadBytes = ((audioFloorBps.coerceAtLeast(0) + videoFloorBps.coerceAtLeast(0)) * safeDuration / 8.0)
        return (payloadBytes * (1.0 + containerOverheadPercent.coerceIn(0.02, 0.25))).toLong()
    }

    /** Single source of truth for the UI estimate and encoder byte ceiling. */
    fun calculateEffectiveTargetBytes(
        targetSize: TargetSize?,
        sourceSizeBytes: Long,
        quality: ConversionQuality
    ): Long {
        val qualityPct = quality.qualityPercent.coerceIn(1, 100)
        fun boundedBudget(bytes: Long): Long {
            val withMinimum = bytes.coerceAtLeast(100_000L)
            return if (sourceSizeBytes > 0L) min(withMinimum, sourceSizeBytes) else withMinimum
        }

        return when {
            targetSize != null -> {
                // A fixed service/preset cap is authoritative even when the
                // user moves the quality slider. The UI estimate and encoder
                // ceiling must describe the same byte contract.
                if (sourceSizeBytes > 0L) {
                    val scaledSource = (sourceSizeBytes * (qualityPct / 100.0)).toLong()
                    boundedBudget(min(targetSize.maxAllowedBytes, scaledSource))
                } else {
                    targetSize.maxAllowedBytes.coerceAtLeast(100_000L)
                }
            }
            quality is ConversionQuality.Custom && sourceSizeBytes > 0L -> {
                boundedBudget((sourceSizeBytes * (qualityPct / 100.0)).toLong())
            }
            sourceSizeBytes > 0L -> boundedBudget((sourceSizeBytes * (qualityPct / 100.0)).toLong())
            else -> TargetSize.fromMegabytes(16).bytes
        }
    }

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

        // Sizing Budget Calculation:
        // 1. If user explicitly dragged the quality slider (ConversionQuality.Custom), their chosen
        //    percentage has direct authority over the source size budget.
        // 2. If targetSize is specified (e.g. WhatsApp 16MB preset, Discord 25MB preset),
        //    the targetSize bytes serves as a strict hard ceiling for presets.
        //    If source size is known, we scale down if quality < 100%, but never exceed targetSize.
        // 3. If targetSize is null and source size is known, quality percentage strictly
        //    dictates the proportional target bytes from source size.
        // 4. Fallback to 16MB default budget.
        val effectiveTargetBytes = calculateEffectiveTargetBytes(targetSize, sourceSizeBytes, quality)

        val usableBits = (effectiveTargetBytes * 8.0 * (1.0 - containerOverheadPercent.coerceIn(0.02, 0.25))).toLong()
        val targetTotalBitrateBps = (usableBits / safeDuration).roundToInt()

        val extremeBudget = targetTotalBitrateBps < 32_000
        // Adaptive Audio Bitrate Budgeting based on total available bitrate
        val safeAudioBitrate = when {
            // Keep a very low-rate AAC track even for extreme durations. The
            // Media3 path re-encodes it instead of silently publishing a
            // video-only file. 8 kbps is the minimum practical AAC budget.
            targetTotalBitrateBps < 32_000 -> 8_000
            targetTotalBitrateBps < 200_000 -> 32_000
            targetTotalBitrateBps < 350_000 -> 48_000
            // Preserve ordinary AAC tracks (typically 64–128 kbps) whenever
            // the duration budget can afford them; otherwise the muxer would
            // silently publish a video-only file.
            targetTotalBitrateBps < 1_000_000 -> 128_000
            else -> audioBitrateBps.coerceIn(64_000, 192_000)
        }

        // A 4 kbps video floor is only used for extreme-duration files so an
        // 8 kbps AAC soundtrack can still fit inside the byte contract.
        val minimumVideoBitrate = if (extremeBudget) 4_000 else MIN_VIDEO_BITRATE_BPS
        val rawVideoBitrate = (targetTotalBitrateBps - safeAudioBitrate).coerceAtLeast(minimumVideoBitrate)

        // Bitrate Ceiling: when source size is known, output video bitrate must never exceed source video bitrate scaled by quality percentage
        val boundedBySourceBitrate = if (sourceSizeBytes > 0L) {
            val sourceBitrateBps = ((sourceSizeBytes * 8.0) / safeDuration).roundToInt()
            val sourceVideoBitrateEstimate = (sourceBitrateBps - safeAudioBitrate).coerceAtLeast(minimumVideoBitrate)
            val scaledCeiling = (sourceVideoBitrateEstimate * (qualityPct / 100.0)).roundToInt().coerceAtLeast(minimumVideoBitrate)
            min(rawVideoBitrate, scaledCeiling)
        } else {
            rawVideoBitrate
        }

        val finalVideoBitrate = boundedBySourceBitrate.coerceIn(minimumVideoBitrate, MAX_VIDEO_BITRATE_BPS)

        // Aspect-ratio-aware bounding box dimension selection
        val safeW = if (sourceWidth > 0) sourceWidth else 1920
        val safeH = if (sourceHeight > 0) sourceHeight else 1080
        val maxSourceDim = max(safeW, safeH)

        val maxAllowedDimension = when {
            // Low-budget hardware encoders often impose a bitrate floor tied to
            // frame size. Long sources need a much smaller frame to make the
            // requested byte budget achievable instead of producing an inflated
            // output while still reporting an apparently valid conversion.
            finalVideoBitrate < 100_000 -> 144
            finalVideoBitrate < 500_000 || qualityPct <= 20 -> 640
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
