package com.tapconvert.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PresetTest {

    @Test
    fun `all presets have unique ids and non-empty metadata`() {
        val ids = mutableSetOf<String>()
        Preset.allPresets.forEach { preset ->
            assertThat(preset.id).isNotEmpty()
            assertThat(preset.name).isNotEmpty()
            assertThat(preset.description).isNotEmpty()
            assertThat(ids.add(preset.id)).isTrue()
        }
    }

    @Test
    fun `presets filter by conversion type accurately`() {
        val imageCompressPresets = Preset.presetsFor(ConversionType.IMAGE_COMPRESS)
        assertThat(imageCompressPresets).contains(Preset.GovPassport200KB)
        assertThat(imageCompressPresets).contains(Preset.WebStandard500KB)
        assertThat(imageCompressPresets).doesNotContain(Preset.WhatsAppVideo16MB)

        val videoCompressPresets = Preset.presetsFor(ConversionType.VIDEO_COMPRESS)
        assertThat(videoCompressPresets).contains(Preset.WhatsAppVideo16MB)
        assertThat(videoCompressPresets).contains(Preset.EmailVideo25MB)
        assertThat(videoCompressPresets).contains(Preset.DiscordVideo10MB)

        val audioExtractPresets = Preset.presetsFor(ConversionType.EXTRACT_AUDIO)
        assertThat(audioExtractPresets).contains(Preset.Mp3HighQuality320)
        assertThat(audioExtractPresets).contains(Preset.AacStandard256)

        val pdfPresets = Preset.presetsFor(ConversionType.IMAGES_TO_PDF)
        assertThat(pdfPresets).contains(Preset.PdfPrintHighRes)
        assertThat(pdfPresets).contains(Preset.PdfCompactWeb)
    }

    @Test
    fun `presets filter by media category accurately`() {
        val videoPresets = Preset.presetsFor(MediaCategory.VIDEO)
        assertThat(videoPresets).isNotEmpty()
        videoPresets.forEach { assertThat(it.category).isEqualTo(MediaCategory.VIDEO) }

        val imagePresets = Preset.presetsFor(MediaCategory.IMAGE)
        assertThat(imagePresets).isNotEmpty()
        imagePresets.forEach { assertThat(it.category).isEqualTo(MediaCategory.IMAGE) }
    }

    @Test
    fun `findById retrieves correct preset instance`() {
        val found = Preset.findById("whatsapp_video_16mb")
        assertThat(found).isEqualTo(Preset.WhatsAppVideo16MB)

        val notFound = Preset.findById("non_existent_id")
        assertThat(notFound).isNull()
    }

    @Test
    fun `preset target sizes match intent specifications`() {
        assertThat(Preset.WhatsAppVideo16MB.targetSize?.bytes).isEqualTo(16 * 1024L * 1024L)
        assertThat(Preset.EmailVideo25MB.targetSize?.bytes).isEqualTo(25 * 1024L * 1024L)
        assertThat(Preset.DiscordVideo10MB.targetSize?.bytes).isEqualTo(10 * 1024L * 1024L)
        assertThat(Preset.GovPassport200KB.targetSize?.bytes).isEqualTo(200 * 1024L)
        assertThat(Preset.WebStandard500KB.targetSize?.bytes).isEqualTo(500 * 1024L)
    }
}
