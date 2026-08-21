package com.tapconvert.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MimeTypeTest {

    @Test
    fun `fromMime parses known formats across all categories`() {
        assertThat(MimeType.fromMime("image/jpeg")).isEqualTo(MimeType.Image.JPEG)
        assertThat(MimeType.fromMime("image/jpg")).isEqualTo(MimeType.Image.JPEG)
        assertThat(MimeType.fromMime("image/png")).isEqualTo(MimeType.Image.PNG)
        assertThat(MimeType.fromMime("image/webp")).isEqualTo(MimeType.Image.WEBP)
        assertThat(MimeType.fromMime("image/heic")).isEqualTo(MimeType.Image.HEIC)

        assertThat(MimeType.fromMime("video/mp4")).isEqualTo(MimeType.Video.MP4)
        assertThat(MimeType.fromMime("video/quicktime")).isEqualTo(MimeType.Video.MOV)
        assertThat(MimeType.fromMime("video/x-matroska")).isEqualTo(MimeType.Video.MKV)

        assertThat(MimeType.fromMime("audio/mpeg")).isEqualTo(MimeType.Audio.MP3)
        assertThat(MimeType.fromMime("audio/mp3")).isEqualTo(MimeType.Audio.MP3)
        assertThat(MimeType.fromMime("audio/aac")).isEqualTo(MimeType.Audio.AAC)
        assertThat(MimeType.fromMime("audio/wav")).isEqualTo(MimeType.Audio.WAV)

        assertThat(MimeType.fromMime("application/pdf")).isEqualTo(MimeType.Document.PDF)
    }

    @Test
    fun `fromExtension parses standard extensions correctly`() {
        assertThat(MimeType.fromExtension("jpg")).isEqualTo(MimeType.Image.JPEG)
        assertThat(MimeType.fromExtension(".jpeg")).isEqualTo(MimeType.Image.JPEG)
        assertThat(MimeType.fromExtension("PNG")).isEqualTo(MimeType.Image.PNG)
        assertThat(MimeType.fromExtension("webp")).isEqualTo(MimeType.Image.WEBP)
        assertThat(MimeType.fromExtension("mp4")).isEqualTo(MimeType.Video.MP4)
        assertThat(MimeType.fromExtension("mp3")).isEqualTo(MimeType.Audio.MP3)
        assertThat(MimeType.fromExtension("pdf")).isEqualTo(MimeType.Document.PDF)
    }

    @Test
    fun `fromFileName extracts and parses mime type`() {
        assertThat(MimeType.fromFileName("vacation_photo.JPG")).isEqualTo(MimeType.Image.JPEG)
        assertThat(MimeType.fromFileName("contract.final.pdf")).isEqualTo(MimeType.Document.PDF)
        assertThat(MimeType.fromFileName("recorded_interview.mp3")).isEqualTo(MimeType.Audio.MP3)
        assertThat(MimeType.fromFileName("birthday_video.mp4")).isEqualTo(MimeType.Video.MP4)
        assertThat(MimeType.fromFileName("mystery_file")).isInstanceOf(MimeType.Other::class.java)
    }

    @Test
    fun `unknown mime types fallback gracefully to Other`() {
        val unknown = MimeType.fromMime("application/x-custom-archive")
        assertThat(unknown).isInstanceOf(MimeType.Other::class.java)
        assertThat(unknown.rawMimeType).isEqualTo("application/x-custom-archive")
    }

    @Test
    fun `all categories match expected types`() {
        assertThat(MimeType.Image.JPEG.category).isEqualTo(MediaCategory.IMAGE)
        assertThat(MimeType.Video.MP4.category).isEqualTo(MediaCategory.VIDEO)
        assertThat(MimeType.Audio.MP3.category).isEqualTo(MediaCategory.AUDIO)
        assertThat(MimeType.Document.PDF.category).isEqualTo(MediaCategory.DOCUMENT)
    }
}
