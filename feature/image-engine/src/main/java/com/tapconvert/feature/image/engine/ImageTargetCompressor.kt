package com.tapconvert.feature.image.engine

import android.graphics.Bitmap
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.ConversionError
import com.tapconvert.core.model.MimeType
import com.tapconvert.core.model.TargetSize
import kotlin.math.roundToInt

object ImageTargetCompressor {

    data class CompressionOutcome(
        val compressedBytes: ByteArray,
        val finalQuality: Int,
        val finalWidth: Int,
        val finalHeight: Int
    )

    /**
     * Compresses bitmap iteratively using binary search on quality and adaptive downscaling
     * to strictly satisfy output size <= targetSize.bytes.
     */
    fun compress(
        sourceBitmap: Bitmap,
        targetSize: TargetSize,
        targetFormat: MimeType.Image = MimeType.Image.JPEG,
        maxDownscaleIterations: Int = 5
    ): AppResult<CompressionOutcome> {
        var currentBitmap = sourceBitmap
        var bestOutcome: CompressionOutcome? = null
        var minAchievableBytes = Long.MAX_VALUE

        val formatToUse = if (targetFormat is MimeType.Image.PNG) MimeType.Image.WEBP else targetFormat

        for (downscaleStep in 0..maxDownscaleIterations) {
            var lowQ = 10
            var highQ = 95
            var passBestBytes: ByteArray? = null
            var passBestQ = lowQ

            // Binary search on compression quality (max 6 steps)
            for (iter in 0 until 6) {
                if (lowQ > highQ) break
                val midQ = (lowQ + highQ) / 2
                val bytes = ImageFormatConverter.convert(currentBitmap, formatToUse, midQ)
                val byteSize = bytes.size.toLong()

                if (byteSize < minAchievableBytes) {
                    minAchievableBytes = byteSize
                }

                if (byteSize <= targetSize.bytes) {
                    passBestBytes = bytes
                    passBestQ = midQ
                    // Try higher quality to get closest to target
                    lowQ = midQ + 1
                } else {
                    highQ = midQ - 1
                }
            }

            if (passBestBytes != null) {
                bestOutcome = CompressionOutcome(
                    compressedBytes = passBestBytes,
                    finalQuality = passBestQ,
                    finalWidth = currentBitmap.width,
                    finalHeight = currentBitmap.height
                )
                break
            }

            // Quality reduction alone wasn't enough; downscale dimensions by 15%
            if (downscaleStep < maxDownscaleIterations) {
                val nextW = (currentBitmap.width * 0.85f).roundToInt().coerceAtLeast(64)
                val nextH = (currentBitmap.height * 0.85f).roundToInt().coerceAtLeast(64)

                if (nextW == currentBitmap.width && nextH == currentBitmap.height) {
                    break // Cannot scale down any further
                }

                val scaled = try {
                    Bitmap.createScaledBitmap(currentBitmap, nextW, nextH, true)
                } catch (_: Throwable) {
                    break
                }

                if (currentBitmap != sourceBitmap && !currentBitmap.isRecycled) {
                    currentBitmap.recycle()
                }
                currentBitmap = scaled
            }
        }

        // Clean up intermediate bitmap if allocated
        if (currentBitmap != sourceBitmap && !currentBitmap.isRecycled) {
            currentBitmap.recycle()
        }

        return if (bestOutcome != null) {
            AppResult.Success(bestOutcome)
        } else {
            AppResult.Error(
                ConversionError.TargetSizeUnachievable(
                    minAchievableBytes = minAchievableBytes,
                    targetBytes = targetSize.bytes
                )
            )
        }
    }
}
