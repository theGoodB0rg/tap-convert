package com.tapconvert.core.testing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream

object BitmapTestFactory {

    /**
     * Creates an in-memory Android Bitmap with given dimensions and color.
     */
    fun createBitmap(
        width: Int = 100,
        height: Int = 100,
        color: Int = Color.BLUE,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, config)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        return bitmap
    }

    /**
     * Creates a pattern/checkerboard bitmap useful for compression testing (non-uniform entropy).
     */
    fun createPatternBitmap(
        width: Int = 200,
        height: Int = 200,
        cellSize: Int = 20
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint1 = Paint().apply { color = Color.RED }
        val paint2 = Paint().apply { color = Color.YELLOW }

        for (x in 0 until width step cellSize) {
            for (y in 0 until height step cellSize) {
                val paint = if ((x / cellSize + y / cellSize) % 2 == 0) paint1 else paint2
                canvas.drawRect(
                    x.toFloat(),
                    y.toFloat(),
                    (x + cellSize).toFloat(),
                    (y + cellSize).toFloat(),
                    paint
                )
            }
        }
        return bitmap
    }

    /**
     * Compresses bitmap to byte array (JPEG, PNG, or WEBP).
     */
    fun toByteArray(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 90
    ): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(format, quality, stream)
        return stream.toByteArray()
    }

    /**
     * Generates a valid minimal synthetic PNG file byte stream.
     * Starts with PNG signature: 89 50 4E 47 0D 0A 1A 0A
     */
    fun createSyntheticPngBytes(width: Int = 1, height: Int = 1): ByteArray {
        return byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
            // IHDR chunk (13 bytes data)
            0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52, // "IHDR"
            (width shr 24).toByte(), (width shr 16).toByte(), (width shr 8).toByte(), width.toByte(),
            (height shr 24).toByte(), (height shr 16).toByte(), (height shr 8).toByte(), height.toByte(),
            0x08, 0x02, 0x00, 0x00, 0x00, // 8-bit truecolor RGB
            0x4C.toByte(), 0x73.toByte(), 0x5E.toByte(), 0xC8.toByte(), // CRC
            // IEND chunk
            0x00, 0x00, 0x00, 0x00,
            0x49, 0x45, 0x4E, 0x44, // "IEND"
            0xAE.toByte(), 0x42.toByte(), 0x60.toByte(), 0x82.toByte()
        )
    }

    /**
     * Generates a valid minimal synthetic JPEG file byte stream.
     * Starts with SOI (FF D8) and ends with EOI (FF D9).
     */
    fun createSyntheticJpegBytes(payloadSize: Int = 256): ByteArray {
        val stream = ByteArrayOutputStream()
        // SOI marker
        stream.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        // APP0 JFIF marker
        stream.write(byteArrayOf(
            0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, // length = 16
            0x4A, 0x46, 0x49, 0x46, 0x00, // "JFIF\0"
            0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00
        ))
        // Synthetic payload bytes
        val body = ByteArray(payloadSize.coerceAtLeast(16)) { 0x55.toByte() }
        stream.write(body)
        // EOI marker
        stream.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        return stream.toByteArray()
    }

    /**
     * Generates a valid minimal synthetic WebP file byte stream with RIFF header.
     */
    fun createSyntheticWebpBytes(payloadSize: Int = 128): ByteArray {
        val stream = ByteArrayOutputStream()
        val safePayload = ByteArray(payloadSize.coerceAtLeast(16)) { 0x33.toByte() }
        val fileSize = safePayload.size + 12

        // RIFF header
        stream.write(byteArrayOf(0x52, 0x49, 0x46, 0x46)) // 'RIFF'
        stream.write(byteArrayOf(
            (fileSize and 0xFF).toByte(),
            ((fileSize shr 8) and 0xFF).toByte(),
            ((fileSize shr 16) and 0xFF).toByte(),
            ((fileSize shr 24) and 0xFF).toByte()
        ))
        stream.write(byteArrayOf(0x57, 0x45, 0x42, 0x50)) // 'WEBP'
        stream.write(byteArrayOf(0x56, 0x50, 0x38, 0x20)) // 'VP8 '
        val vp8Size = safePayload.size
        stream.write(byteArrayOf(
            (vp8Size and 0xFF).toByte(),
            ((vp8Size shr 8) and 0xFF).toByte(),
            ((vp8Size shr 16) and 0xFF).toByte(),
            ((vp8Size shr 24) and 0xFF).toByte()
        ))
        stream.write(safePayload)
        return stream.toByteArray()
    }
}

