package com.tapconvert.feature.media.engine

import android.media.MediaCodecInfo
import android.media.MediaFormat
import kotlin.math.roundToInt

/**
 * Encapsulates video and audio encoding configuration, unthrottling hardware codec clocks,
 * setting optimal VBR bitrate modes, macroblock alignment, and resilient codec parameters.
 */
object TranscoderConfigurator {

    const val DEFAULT_I_FRAME_INTERVAL_SECONDS = 2
    const val MAX_OPERATING_RATE_FPS = 1000

    /**
     * Creates an optimized MediaFormat for H.264/AVC hardware video encoding.
     */
    fun createEncoderFormat(
        targetWidth: Int,
        targetHeight: Int,
        bitrateBps: Int,
        frameRate: Int = 30,
        iFrameIntervalSeconds: Int = DEFAULT_I_FRAME_INTERVAL_SECONDS,
        outputMime: String = MediaFormat.MIMETYPE_VIDEO_AVC
    ): MediaFormat {
        val alignedWidth = alignToMacroblock(targetWidth)
        val alignedHeight = alignToMacroblock(targetHeight)
        val clampedFrameRate = when {
            bitrateBps < 100_000 -> 1
            bitrateBps < 500_000 -> 15
            bitrateBps < 800_000 -> minOf(frameRate, 24)
            else -> frameRate.coerceIn(15, 60)
        }

        val format = (try {
            MediaFormat.createVideoFormat(outputMime, alignedWidth, alignedHeight)
        } catch (_: Throwable) { null }) ?: MediaFormat()

        format.apply {
            trySafely { setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) }
            trySafely { setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps.coerceAtLeast(BitrateCalculator.MIN_VIDEO_BITRATE_BPS)) }
            trySafely { setInteger(MediaFormat.KEY_FRAME_RATE, clampedFrameRate) }
            val lowBitrateIFrameInterval = if (bitrateBps < 100_000) 10 else iFrameIntervalSeconds
            trySafely { setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, lowBitrateIFrameInterval.coerceAtLeast(1)) }

            // Constant Bitrate mode is intentional here: several OEM surface
            // encoders ignore a low VBR target and silently fall back to a much
            // higher profile bitrate, which breaks the byte-size contract.
            trySafely {
                setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                )
            }

            // Some vendor codecs consult KEY_MAX_BIT_RATE even in CBR mode.
            // Keeping it equal to the requested bitrate prevents an OEM from
            // selecting a large implicit peak bitrate for long videos.
            trySafely {
                setInteger(
                    MAX_BITRATE_KEY,
                    bitrateBps.coerceAtLeast(BitrateCalculator.MIN_VIDEO_BITRATE_BPS)
                )
            }

            // Unthrottle hardware clocks to process as fast as possible (non-realtime max frequency)
            trySafely {
                setInteger(MediaFormat.KEY_OPERATING_RATE, MAX_OPERATING_RATE_FPS)
            }

            // High priority allocation for media processing
            trySafely {
                setInteger(MediaFormat.KEY_PRIORITY, 0)
            }
        }

        return format
    }

    /**
     * Optimizes decoder MediaFormat with max operating rate and high priority.
     */
    fun configureDecoderFormat(sourceFormat: MediaFormat): MediaFormat {
        // Create a copy or apply to existing format safely
        trySafely {
            sourceFormat.setInteger(MediaFormat.KEY_OPERATING_RATE, MAX_OPERATING_RATE_FPS)
        }
        trySafely {
            sourceFormat.setInteger(MediaFormat.KEY_PRIORITY, 0)
        }
        return sourceFormat
    }

    /**
     * Aligns dimension to 16-pixel macroblock boundary for optimal hardware DMA performance.
     * Minimum 144 pixels.
     */
    fun alignToMacroblock(dimension: Int): Int {
        val safeDim = dimension.coerceAtLeast(144)
        val remainder = safeDim % 16
        return if (remainder == 0) {
            safeDim
        } else {
            // Round to nearest even 16-pixel multiple, keeping even
            val rounded = ((safeDim + 8) / 16) * 16
            rounded.coerceAtLeast(144)
        }
    }

    private inline fun trySafely(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // Ignored on legacy OEM devices that reject extended MediaFormat keys
        }
    }

    // The Android SDK stubs used by this project omit MediaFormat.KEY_MAX_BIT_RATE,
    // but vendor codecs commonly consume its platform key by name.
    private const val MAX_BITRATE_KEY = "max-bitrate"
}
