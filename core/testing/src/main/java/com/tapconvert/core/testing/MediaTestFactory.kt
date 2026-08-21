package com.tapconvert.core.testing

import java.io.ByteArrayOutputStream

object MediaTestFactory {

    /**
     * Generates a synthetic MP4 ISO base container stream with ftyp, moov, and mdat boxes.
     */
    fun createMockMp4Bytes(payloadSizeBytes: Int = 1024): ByteArray {
        val stream = ByteArrayOutputStream()

        // 1. ftyp box (Major brand: isom, minor version: 512, compatible brands: isom, iso2, mp41)
        val ftypPayload = byteArrayOf(
            0x69, 0x73, 0x6F, 0x6D, // 'isom'
            0x00, 0x00, 0x02, 0x00, // version 512
            0x69, 0x73, 0x6F, 0x6D, // 'isom'
            0x69, 0x73, 0x6F, 0x32, // 'iso2'
            0x6D, 0x70, 0x34, 0x31  // 'mp41'
        )
        val ftypSize = ftypPayload.size + 8
        stream.write(byteArrayOf(
            (ftypSize shr 24).toByte(), (ftypSize shr 16).toByte(), (ftypSize shr 8).toByte(), ftypSize.toByte(),
            0x66, 0x74, 0x79, 0x70 // 'ftyp'
        ))
        stream.write(ftypPayload)

        // 2. mdat box (Media data payload)
        val safePayload = ByteArray(payloadSizeBytes.coerceAtLeast(64)) { 0xAA.toByte() }
        val mdatSize = safePayload.size + 8
        stream.write(byteArrayOf(
            (mdatSize shr 24).toByte(), (mdatSize shr 16).toByte(), (mdatSize shr 8).toByte(), mdatSize.toByte(),
            0x6D, 0x64, 0x61, 0x74 // 'mdat'
        ))
        stream.write(safePayload)

        // 3. moov box (Movie metadata container)
        val moovHeader = byteArrayOf(
            0x00, 0x00, 0x00, 0x18, // size 24
            0x6D, 0x6F, 0x6F, 0x76, // 'moov'
            0x00, 0x00, 0x00, 0x10, // mvhd size 16
            0x6D, 0x76, 0x68, 0x64, // 'mvhd'
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        stream.write(moovHeader)

        return stream.toByteArray()
    }

    /**
     * Generates a synthetic MP3 byte stream with MPEG-1 Audio Layer III syncwords (0xFFFB).
     */
    fun createMockMp3Bytes(frameCount: Int = 10, frameSize: Int = 144): ByteArray {
        val stream = ByteArrayOutputStream()
        val safeCount = frameCount.coerceAtLeast(1)

        for (i in 0 until safeCount) {
            // MP3 MPEG-1 Layer 3 128kbps 44.1kHz header
            stream.write(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64.toByte()))
            // Audio payload padding
            val payload = ByteArray((frameSize - 4).coerceAtLeast(8)) { 0x33.toByte() }
            stream.write(payload)
        }
        return stream.toByteArray()
    }

    /**
     * Generates a synthetic AAC ADTS byte stream with syncwords (0xFFF1).
     */
    fun createMockAacBytes(frameCount: Int = 10, frameSize: Int = 200): ByteArray {
        val stream = ByteArrayOutputStream()
        val safeCount = frameCount.coerceAtLeast(1)

        for (i in 0 until safeCount) {
            // AAC ADTS 7-byte header (MPEG-4, AAC LC, 44.1kHz, stereo)
            val header = byteArrayOf(
                0xFF.toByte(), 0xF1.toByte(), 0x50.toByte(), 0x80.toByte(),
                ((frameSize shr 5) and 0x1F).toByte(),
                ((frameSize and 0x1F) shl 3).toByte(),
                0xFC.toByte()
            )
            stream.write(header)
            val payload = ByteArray((frameSize - 7).coerceAtLeast(8)) { 0x44.toByte() }
            stream.write(payload)
        }
        return stream.toByteArray()
    }
}
