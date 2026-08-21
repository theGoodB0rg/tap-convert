package com.tapconvert.feature.image.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tapconvert.core.model.DimensionConstraint
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

object BitmapDecoder {

    /**
     * Calculates the optimal sample size (power of 2) for decoding large images safely.
     */
    fun calculateInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        if (reqWidth <= 0 || reqHeight <= 0 || srcWidth <= 0 || srcHeight <= 0) return 1
        var inSampleSize = 1

        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * Decodes a byte array safely using two-pass bounds checking and dimension constraints.
     */
    fun decodeByteArray(
        bytes: ByteArray,
        constraint: DimensionConstraint = DimensionConstraint.None
    ): Bitmap? {
        if (bytes.isEmpty()) return null

        return try {
            // Pass 1: Decode bounds only
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            val originalW = options.outWidth
            val originalH = options.outHeight
            if (originalW <= 0 || originalH <= 0) return null

            val (targetW, targetH) = constraint.calculateDimensions(originalW, originalH)

            // Pass 2: Decode with inSampleSize
            val sampleSize = calculateInSampleSize(originalW, originalH, targetW, targetH)
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

            // Apply EXIF orientation
            val orientation = ByteArrayInputStream(bytes).use { ExifTransformer.extractOrientation(it) }
            val rotated = ExifTransformer.applyOrientation(decoded, orientation)

            // Scale to exact target dimensions if needed
            scaleIfNeeded(rotated, targetW, targetH)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Decodes an InputStream safely by reading to memory buffer.
     */
    fun decodeStream(
        inputStream: InputStream,
        constraint: DimensionConstraint = DimensionConstraint.None
    ): Bitmap? {
        return try {
            val bytes = inputStream.readBytes()
            decodeByteArray(bytes, constraint)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Decodes a File safely with bounds check and EXIF orientation extraction.
     */
    fun decodeFile(
        file: File,
        constraint: DimensionConstraint = DimensionConstraint.None
    ): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null

        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val originalW = options.outWidth
            val originalH = options.outHeight
            if (originalW <= 0 || originalH <= 0) return null

            val (targetW, targetH) = constraint.calculateDimensions(originalW, originalH)
            val sampleSize = calculateInSampleSize(originalW, originalH, targetW, targetH)

            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
            val orientation = ExifTransformer.extractOrientation(file)
            val rotated = ExifTransformer.applyOrientation(decoded, orientation)

            scaleIfNeeded(rotated, targetW, targetH)
        } catch (_: Throwable) {
            null
        }
    }

    private fun scaleIfNeeded(bitmap: Bitmap, targetW: Int, targetH: Int): Bitmap {
        if (targetW <= 0 || targetH <= 0) return bitmap
        if (bitmap.width == targetW && bitmap.height == targetH) return bitmap

        return try {
            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            if (scaled != bitmap && !bitmap.isRecycled) {
                bitmap.recycle()
            }
            scaled
        } catch (_: Throwable) {
            bitmap
        }
    }
}
