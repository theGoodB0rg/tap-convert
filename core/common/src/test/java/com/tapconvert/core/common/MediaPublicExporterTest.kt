package com.tapconvert.core.common

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.model.MimeType
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MediaPublicExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `getDestinationDisplayForMime returns formatted public folder labels`() {
        val imageDisplay = MediaPublicExporter.getDestinationDisplayForMime(MimeType.Image.JPEG)
        assertThat(imageDisplay).isEqualTo("Saved to Pictures/TapConvert")

        val videoDisplay = MediaPublicExporter.getDestinationDisplayForMime(MimeType.Video.MP4)
        assertThat(videoDisplay).isEqualTo("Saved to Movies/TapConvert")

        val audioDisplay = MediaPublicExporter.getDestinationDisplayForMime(MimeType.Audio.MP3)
        assertThat(audioDisplay).isEqualTo("Saved to Music/TapConvert")

        val docDisplay = MediaPublicExporter.getDestinationDisplayForMime(MimeType.Document.PDF)
        assertThat(docDisplay).isEqualTo("Saved to Documents/TapConvert")
    }

    @Test
    fun `getDestinationDisplayForMime returns custom path when provided`() {
        val customDisplay = MediaPublicExporter.getDestinationDisplayForMime(
            mimeType = MimeType.Image.JPEG,
            customPath = "Download/MyFolder"
        )
        assertThat(customDisplay).isEqualTo("Saved to Download/MyFolder")
    }

    @Test
    fun `exportFile with nonexistent file returns failure result`() {
        val nonExistentFile = File(tempFolder.root, "ghost.jpg")
        val mockContext = io.mockk.mockk<android.content.Context>(relaxed = true)

        val result = MediaPublicExporter.exportFile(
            context = mockContext,
            sourceFile = nonExistentFile,
            mimeType = MimeType.Image.JPEG
        )

        assertThat(result.isSuccess).isFalse()
        assertThat(result.destinationDisplay).isEqualTo("Original file unavailable")
    }
}
