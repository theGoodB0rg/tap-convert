package com.tapconvert.feature.image.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import com.tapconvert.core.model.MimeType
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageFormatConverter {

    /**
     * Converts a Bitmap into compressed byte array in the requested target format.
     */
    fun convert(
        bitmap: Bitmap,
        targetFormat: MimeType.Image,
        qualityPercent: Int = 90
    ): ByteArray {
        val safeQuality = qualityPercent.coerceIn(1, 100)
        val stream = ByteArrayOutputStream()

        when (targetFormat) {
            is MimeType.Image.JPEG -> {
                // JPEG does not support alpha; draw over white background if bitmap has alpha
                val nonAlphaBitmap = if (bitmap.hasAlpha()) {
                    removeAlpha(bitmap, Color.WHITE)
                } else {
                    bitmap
                }
                nonAlphaBitmap.compress(Bitmap.CompressFormat.JPEG, safeQuality, stream)
                if (nonAlphaBitmap != bitmap && !nonAlphaBitmap.isRecycled) {
                    nonAlphaBitmap.recycle()
                }
            }

            is MimeType.Image.PNG -> {
                // PNG is lossless; quality argument is ignored by Android PNG compressor
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }

            is MimeType.Image.WEBP -> {
                val compressFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (safeQuality >= 100) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                bitmap.compress(compressFormat, safeQuality, stream)
            }

            is MimeType.Image.HEIC -> {
                val compressFormat = try {
                    Bitmap.CompressFormat.valueOf("HEIF")
                } catch (_: Throwable) {
                    try {
                        Bitmap.CompressFormat.valueOf("HEIC")
                    } catch (_: Throwable) {
                        Bitmap.CompressFormat.JPEG
                    }
                }
                bitmap.compress(compressFormat, safeQuality, stream)
            }

            is MimeType.Image.BMP -> {
                val nonAlphaBitmap = if (bitmap.hasAlpha()) removeAlpha(bitmap, Color.WHITE) else bitmap
                writeBmpStream(nonAlphaBitmap, stream)
                if (nonAlphaBitmap != bitmap && !nonAlphaBitmap.isRecycled) {
                    nonAlphaBitmap.recycle()
                }
            }

            is MimeType.Image.GIF -> {
                // Fallback GIF to PNG
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }

        return stream.toByteArray()
    }

    /**
     * Converts a Bitmap directly to an output file.
     */
    fun convertToFile(
        bitmap: Bitmap,
        targetFormat: MimeType.Image,
        qualityPercent: Int = 90,
        outputFile: File
    ) {
        val bytes = convert(bitmap, targetFormat, qualityPercent)
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { it.write(bytes) }
    }

    /**
     * Blends a bitmap with an alpha channel onto a solid background color (default WHITE).
     */
    fun removeAlpha(source: Bitmap, backgroundColor: Int = Color.WHITE): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(backgroundColor)
        canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return result
    }

    /**
     * Minimal BMP (Windows Bitmap 24-bit RGB) writer.
     */
    private fun writeBmpStream(bitmap: Bitmap, stream: ByteArrayOutputStream) {
        val width = bitmap.width
        val height = bitmap.height
        val rowSize = (24 * width + 31) / 32 * 4
        val imageSize = rowSize * height
        val fileSize = 54 + imageSize

        // BMP Header (14 bytes)
        stream.write(byteArrayOf(0x42, 0x4D)) // 'BM'
        writeIntLE(stream, fileSize)
        writeIntLE(stream, 0) // Reserved
        writeIntLE(stream, 54) // Offset to pixel data

        // DIB Header (BITMAPINFOHEADER - 40 bytes)
        writeIntLE(stream, 40) // Header size
        writeIntLE(stream, width)
        writeIntLE(stream, height)
        writeShortLE(stream, 1) // Planes
        writeShortLE(stream, 24) // Bits per pixel (RGB)
        writeIntLE(stream, 0) // Compression (BI_RGB)
        writeIntLE(stream, imageSize)
        writeIntLE(stream, 2835) // Horizontal resolution (72 DPI)
        writeIntLE(stream, 2835) // Vertical resolution
        writeIntLE(stream, 0) // Colors in palette
        writeIntLE(stream, 0) // Important colors

        // Pixel data (bottom-to-top, BGR format)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val rowPadding = rowSize - (width * 3)

        for (y in height - 1 downTo 0) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                stream.write(b)
                stream.write(g)
                stream.write(r)
            }
            for (p in 0 until rowPadding) {
                stream.write(0)
            }
        }
    }

    private fun writeIntLE(stream: ByteArrayOutputStream, value: Int) {
        stream.write(value and 0xFF)
        stream.write((value shr 8) and 0xFF)
        stream.write((value shr 16) and 0xFF)
        stream.write((value shr 24) and 0xFF)
    }

    private fun writeShortLE(stream: ByteArrayOutputStream, value: Int) {
        stream.write(value and 0xFF)
        stream.write((value shr 8) and 0xFF)
    }
}
