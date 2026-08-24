package com.tapconvert.core.common.intake

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MediaCategory
import com.tapconvert.core.model.MimeType
import com.tapconvert.core.model.Preset
import org.junit.Before
import org.junit.Test

class MediaIntakeClassifierTest {

    private lateinit var classifier: MediaIntakeClassifier

    @Before
    fun setup() {
        classifier = DefaultMediaIntakeClassifier()
    }

    private fun createItem(name: String, mime: String = "application/octet-stream", size: Long = 1024L): StagedMediaItem {
        return StagedMediaItem(
            uri = "file:///cache/staging/$name",
            originalName = name,
            mimeType = mime,
            sizeBytes = size
        )
    }

    @Test
    fun `resolve with explicit preset returns preset configuration`() {
        val items = listOf(createItem("my_video.mp4", "video/mp4"))
        val decision = classifier.resolve(
            items = items,
            explicitPreset = Preset.WhatsAppVideo16MB
        )

        assertThat(decision.category).isEqualTo(MediaCategory.VIDEO)
        assertThat(decision.conversionType).isEqualTo(ConversionType.VIDEO_COMPRESS)
        assertThat(decision.targetMimeType).isEqualTo(MimeType.Video.MP4)
    }

    @Test
    fun `resolve with explicit specificType PDF_COMPRESS returns PDF_COMPRESS`() {
        val items = listOf(createItem("document.pdf", "application/pdf"))
        val decision = classifier.resolve(
            items = items,
            explicitType = ConversionType.PDF_COMPRESS
        )

        assertThat(decision.category).isEqualTo(MediaCategory.DOCUMENT)
        assertThat(decision.conversionType).isEqualTo(ConversionType.PDF_COMPRESS)
        assertThat(decision.targetMimeType).isEqualTo(MimeType.Document.PDF)
    }

    @Test
    fun `resolve with explicit specificType PDF_TO_IMAGES returns PDF_TO_IMAGES`() {
        val items = listOf(createItem("document.pdf", "application/pdf"))
        val decision = classifier.resolve(
            items = items,
            explicitType = ConversionType.PDF_TO_IMAGES
        )

        assertThat(decision.category).isEqualTo(MediaCategory.DOCUMENT)
        assertThat(decision.conversionType).isEqualTo(ConversionType.PDF_TO_IMAGES)
        assertThat(decision.targetMimeType).isEqualTo(MimeType.Image.JPEG)
    }

    @Test
    fun `resolve with explicit specificType IMAGES_TO_PDF returns IMAGES_TO_PDF`() {
        val items = listOf(createItem("photo1.jpg", "image/jpeg"), createItem("photo2.png", "image/png"))
        val decision = classifier.resolve(
            items = items,
            explicitType = ConversionType.IMAGES_TO_PDF
        )

        assertThat(decision.category).isEqualTo(MediaCategory.DOCUMENT)
        assertThat(decision.conversionType).isEqualTo(ConversionType.IMAGES_TO_PDF)
        assertThat(decision.targetMimeType).isEqualTo(MimeType.Document.PDF)
    }

    @Test
    fun `resolve with explicit category VIDEO returns VIDEO_COMPRESS MP4`() {
        val items = listOf(createItem("video.mp4", "video/mp4"))
        val decision = classifier.resolve(
            items = items,
            explicitCategory = MediaCategory.VIDEO
        )

        assertThat(decision.category).isEqualTo(MediaCategory.VIDEO)
        assertThat(decision.conversionType).isEqualTo(ConversionType.VIDEO_COMPRESS)
        assertThat(decision.targetMimeType).isEqualTo(MimeType.Video.MP4)
    }

    @Test
    fun `resolve with explicit category IMAGE returns IMAGE_COMPRESS WEBP`() {
        val items = listOf(createItem("photo.jpg", "image/jpeg"))
        val decision = classifier.resolve(
            items = items,
            explicitCategory = MediaCategory.IMAGE
        )

        assertThat(decision.category).isEqualTo(MediaCategory.IMAGE)
        assertThat(decision.conversionType).isEqualTo(ConversionType.IMAGE_COMPRESS)
        assertThat(decision.targetMimeType).isEqualTo(MimeType.Image.WEBP)
    }

    @Test
    fun `resolve with explicit category DOCUMENT and PDF items returns PDF_COMPRESS`() {
        val items = listOf(createItem("statement.pdf", "application/pdf"))
        val decision = classifier.resolve(
            items = items,
            explicitCategory = MediaCategory.DOCUMENT
        )

        assertThat(decision.category).isEqualTo(MediaCategory.DOCUMENT)
        assertThat(decision.conversionType).isEqualTo(ConversionType.PDF_COMPRESS)
        assertThat(decision.targetMimeType).isEqualTo(MimeType.Document.PDF)
    }

    @Test
    fun `resolve with explicit category DOCUMENT and image items returns IMAGES_TO_PDF`() {
        val items = listOf(createItem("page1.jpg", "image/jpeg"), createItem("page2.jpg", "image/jpeg"))
        val decision = classifier.resolve(
            items = items,
            explicitCategory = MediaCategory.DOCUMENT
        )

        assertThat(decision.category).isEqualTo(MediaCategory.DOCUMENT)
        assertThat(decision.conversionType).isEqualTo(ConversionType.IMAGES_TO_PDF)
        assertThat(decision.targetMimeType).isEqualTo(MimeType.Document.PDF)
    }

    @Test
    fun `resolve with explicit category AUDIO returns EXTRACT_AUDIO MP3`() {
        val items = listOf(createItem("audio.mp3", "audio/mpeg"))
        val decision = classifier.resolve(
            items = items,
            explicitCategory = MediaCategory.AUDIO
        )

        assertThat(decision.category).isEqualTo(MediaCategory.AUDIO)
        assertThat(decision.conversionType).isEqualTo(ConversionType.EXTRACT_AUDIO)
        assertThat(decision.targetMimeType).isEqualTo(MimeType.Audio.MP3)
    }

    @Test
    fun `resolve with untagged universal intake video file resolves to VIDEO_COMPRESS MP4`() {
        val mp4Item = listOf(createItem("1787583442306_4059ce_1000003432.mp4", "video/mp4"))
        val decision1 = classifier.resolve(items = mp4Item)

        assertThat(decision1.category).isEqualTo(MediaCategory.VIDEO)
        assertThat(decision1.conversionType).isEqualTo(ConversionType.VIDEO_COMPRESS)
        assertThat(decision1.targetMimeType).isEqualTo(MimeType.Video.MP4)

        val mkvItem = listOf(createItem("movie.mkv", "video/x-matroska"))
        val decision2 = classifier.resolve(items = mkvItem)
        assertThat(decision2.category).isEqualTo(MediaCategory.VIDEO)
        assertThat(decision2.conversionType).isEqualTo(ConversionType.VIDEO_COMPRESS)
        assertThat(decision2.targetMimeType).isEqualTo(MimeType.Video.MP4)
    }

    @Test
    fun `resolve with untagged universal intake image file resolves to IMAGE_COMPRESS WEBP`() {
        val jpgItem = listOf(createItem("camera_shot.jpg", "image/jpeg"))
        val decision1 = classifier.resolve(items = jpgItem)

        assertThat(decision1.category).isEqualTo(MediaCategory.IMAGE)
        assertThat(decision1.conversionType).isEqualTo(ConversionType.IMAGE_COMPRESS)
        assertThat(decision1.targetMimeType).isEqualTo(MimeType.Image.WEBP)

        val pngItem = listOf(createItem("diagram.png", "image/png"))
        val decision2 = classifier.resolve(items = pngItem)
        assertThat(decision2.category).isEqualTo(MediaCategory.IMAGE)
        assertThat(decision2.conversionType).isEqualTo(ConversionType.IMAGE_COMPRESS)
        assertThat(decision2.targetMimeType).isEqualTo(MimeType.Image.WEBP)
    }

    @Test
    fun `resolve with untagged universal intake PDF document resolves to PDF_COMPRESS PDF`() {
        val pdfItem = listOf(createItem("contract.pdf", "application/pdf"))
        val decision = classifier.resolve(items = pdfItem)

        assertThat(decision.category).isEqualTo(MediaCategory.DOCUMENT)
        assertThat(decision.conversionType).isEqualTo(ConversionType.PDF_COMPRESS)
        assertThat(decision.targetMimeType).isEqualTo(MimeType.Document.PDF)
    }

    @Test
    fun `resolve with untagged universal intake audio file resolves to EXTRACT_AUDIO MP3`() {
        val audioItem = listOf(createItem("recording.mp3", "audio/mpeg"))
        val decision = classifier.resolve(items = audioItem)

        assertThat(decision.category).isEqualTo(MediaCategory.AUDIO)
        assertThat(decision.conversionType).isEqualTo(ConversionType.EXTRACT_AUDIO)
        assertThat(decision.targetMimeType).isEqualTo(MimeType.Audio.MP3)
    }

    @Test
    fun `resolve with empty items defaults gracefully to IMAGE_COMPRESS WEBP`() {
        val decision = classifier.resolve(items = emptyList())

        assertThat(decision.category).isEqualTo(MediaCategory.IMAGE)
        assertThat(decision.conversionType).isEqualTo(ConversionType.IMAGE_COMPRESS)
        assertThat(decision.targetMimeType).isEqualTo(MimeType.Image.WEBP)
    }
}
