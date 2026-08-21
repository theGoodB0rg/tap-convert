package com.tapconvert.app.share

import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ShareHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `getMimeTypeForFile maps common extensions accurately`() {
        val jpg = tempFolder.newFile("photo.jpg")
        val webp = tempFolder.newFile("photo.webp")
        val mp4 = tempFolder.newFile("video.mp4")
        val mp3 = tempFolder.newFile("song.mp3")
        val pdf = tempFolder.newFile("document.pdf")

        assertThat(ShareHelper.getMimeTypeForFile(jpg)).isEqualTo("image/jpeg")
        assertThat(ShareHelper.getMimeTypeForFile(webp)).isEqualTo("image/webp")
        assertThat(ShareHelper.getMimeTypeForFile(mp4)).isEqualTo("video/mp4")
        assertThat(ShareHelper.getMimeTypeForFile(mp3)).isEqualTo("audio/mpeg")
        assertThat(ShareHelper.getMimeTypeForFile(pdf)).isEqualTo("application/pdf")
    }

    @Test
    fun `buildShareIntent runs without throwing exception for valid and missing paths`() {
        val dummyContext = object : ContextWrapper(null) {
            override fun getPackageName(): String = "com.tapconvert.app"
        }
        val file = tempFolder.newFile("test_output.mp4")

        val intent = ShareHelper.buildShareIntent(
            context = dummyContext,
            filePathOrUri = file.absolutePath,
            explicitMimeType = "video/mp4"
        )
        assertThat(intent).isNotNull()

        val missingIntent = ShareHelper.buildShareIntent(
            context = dummyContext,
            filePathOrUri = "content://media/external/images/media/999",
            explicitMimeType = "image/png"
        )
        assertThat(missingIntent).isNotNull()
    }
}
