package com.tapconvert.core.testing

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.charset.StandardCharsets

class SyntheticFixturesTest {

    @Test
    fun `BitmapTestFactory generates synthetic PNG, JPEG, and WebP byte streams with valid magic headers`() {
        val png = BitmapTestFactory.createSyntheticPngBytes(100, 100)
        assertThat(png).isNotEmpty()
        // PNG header check: 0x89 'P' 'N' 'G'
        assertThat(png[0]).isEqualTo(0x89.toByte())
        assertThat(png[1]).isEqualTo('P'.code.toByte())
        assertThat(png[2]).isEqualTo('N'.code.toByte())
        assertThat(png[3]).isEqualTo('G'.code.toByte())

        val jpeg = BitmapTestFactory.createSyntheticJpegBytes(512)
        assertThat(jpeg.size).isAtLeast(512)
        // SOI: 0xFF 0xD8, EOI: 0xFF 0xD9
        assertThat(jpeg[0]).isEqualTo(0xFF.toByte())
        assertThat(jpeg[1]).isEqualTo(0xD8.toByte())
        assertThat(jpeg[jpeg.size - 2]).isEqualTo(0xFF.toByte())
        assertThat(jpeg[jpeg.size - 1]).isEqualTo(0xD9.toByte())

        val webp = BitmapTestFactory.createSyntheticWebpBytes(256)
        assertThat(webp.size).isAtLeast(256)
        val riff = String(webp.copyOfRange(0, 4), StandardCharsets.US_ASCII)
        val webpTag = String(webp.copyOfRange(8, 12), StandardCharsets.US_ASCII)
        assertThat(riff).isEqualTo("RIFF")
        assertThat(webpTag).isEqualTo("WEBP")
    }

    @Test
    fun `PdfTestFactory generates valid PDF 1_4 stream with required structure`() {
        val pdf = PdfTestFactory.createMinimalPdfBytes(pageCount = 3)
        val text = String(pdf, StandardCharsets.US_ASCII)

        assertThat(text).startsWith("%PDF-1.4")
        assertThat(text).contains("/Count 3")
        assertThat(text).contains("xref")
        assertThat(text).contains("trailer")
        assertThat(text.trimEnd()).endsWith("%%EOF")
    }

    @Test
    fun `PdfTestFactory generates encrypted and corrupt streams appropriately`() {
        val encrypted = PdfTestFactory.createEncryptedPdfBytes()
        val encText = String(encrypted, StandardCharsets.US_ASCII)
        assertThat(encText).contains("/Encrypt")

        val corrupt = PdfTestFactory.createCorruptPdfBytes()
        val corruptText = String(corrupt, StandardCharsets.US_ASCII)
        assertThat(corruptText).doesNotContain("%%EOF")
    }

    @Test
    fun `MediaTestFactory generates MP4, MP3, and AAC container structures`() {
        // MP4
        val mp4 = MediaTestFactory.createMockMp4Bytes(256)
        val mp4Header = String(mp4.copyOfRange(4, 8), StandardCharsets.US_ASCII)
        assertThat(mp4Header).isEqualTo("ftyp")

        // MP3
        val mp3 = MediaTestFactory.createMockMp3Bytes(frameCount = 5)
        assertThat(mp3).isNotEmpty()
        assertThat(mp3[0]).isEqualTo(0xFF.toByte())
        assertThat(mp3[1]).isEqualTo(0xFB.toByte())

        // AAC
        val aac = MediaTestFactory.createMockAacBytes(frameCount = 5)
        assertThat(aac).isNotEmpty()
        assertThat(aac[0]).isEqualTo(0xFF.toByte())
        assertThat(aac[1]).isEqualTo(0xF1.toByte())
    }
}

