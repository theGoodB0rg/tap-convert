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
        val clampedFrameRate = frameRate.coerceIn(15, 60)

        val format = (try {
            MediaFormat.createVideoFormat(outputMime, alignedWidth, alignedHeight)
        } catch (_: Throwable) { null }) ?: MediaFormat()

        format.apply {
            trySafely { setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) }
            trySafely { setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps.coerceAtLeast(BitrateCalculator.MIN_VIDEO_BITRATE_BPS)) }
            trySafely { setInteger(MediaFormat.KEY_FRAME_RATE, clampedFrameRate) }
            trySafely { setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameIntervalSeconds.coerceAtLeast(1)) }

            // Variable Bitrate mode for optimal compression and strict budgeting
            trySafely {
                setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
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
}
