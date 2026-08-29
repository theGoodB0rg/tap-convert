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

    @Test
    fun `viral share subject and body formatting are correct`() {
        assertThat(ShareHelper.VIRAL_SHARE_SUBJECT).isEqualTo("Converted with TapConvert")
        val viralBody = ShareHelper.getViralShareBody("com.tapconvert.app")
        assertThat(viralBody).contains("Converted with TapConvert — 100% Offline & Private")
        assertThat(viralBody).contains("https://play.google.com/store/apps/details?id=com.tapconvert.app")
    }

    @Test
    fun `buildShareIntent and createShareChooserIntent create valid intents`() {
        val dummyContext = object : ContextWrapper(null) {
            override fun getPackageName(): String = "com.tapconvert.app"
        }
        val file = tempFolder.newFile("document.pdf")

        val intent = ShareHelper.buildShareIntent(dummyContext, file.absolutePath)
        assertThat(intent).isNotNull()

        val chooser = ShareHelper.createShareChooserIntent(dummyContext, file.absolutePath)
        assertThat(chooser).isNotNull()
    }

    @Test
    fun `resolveCommonMimeType handles single, same category, and mixed categories correctly`() {
        assertThat(ShareHelper.resolveCommonMimeType(listOf("image/jpeg"))).isEqualTo("image/jpeg")
        assertThat(ShareHelper.resolveCommonMimeType(listOf("image/jpeg", "image/jpeg"))).isEqualTo("image/jpeg")
        assertThat(ShareHelper.resolveCommonMimeType(listOf("image/jpeg", "image/png", "image/webp"))).isEqualTo("image/*")
        assertThat(ShareHelper.resolveCommonMimeType(listOf("video/mp4", "video/webm"))).isEqualTo("video/*")
        assertThat(ShareHelper.resolveCommonMimeType(listOf("image/jpeg", "video/mp4"))).isEqualTo("*/*")
        assertThat(ShareHelper.resolveCommonMimeType(emptyList())).isEqualTo("*/*")
    }

    @Test
    fun `buildMultipleShareIntent and createMultipleShareChooserIntent create valid intents`() {
        val dummyContext = object : ContextWrapper(null) {
            override fun getPackageName(): String = "com.tapconvert.app"
        }
        val file1 = tempFolder.newFile("img1.jpg")
        val file2 = tempFolder.newFile("img2.jpg")

        val intent = ShareHelper.buildMultipleShareIntent(dummyContext, listOf(file1.absolutePath, file2.absolutePath))
        assertThat(intent).isNotNull()

        val chooser = ShareHelper.createMultipleShareChooserIntent(dummyContext, listOf(file1.absolutePath, file2.absolutePath))
        assertThat(chooser).isNotNull()
    }

    @Test
    fun `buildShareAsDocumentIntent uses application octet-stream MIME type`() {
        val dummyContext = object : ContextWrapper(null) {
            override fun getPackageName(): String = "com.tapconvert.app"
        }
        val file = tempFolder.newFile("video_to_share.mp4")

        val intent = ShareHelper.buildShareAsDocumentIntent(dummyContext, file.absolutePath)
        assertThat(intent).isNotNull()
        assertThat(ShareHelper.DOCUMENT_MIME_TYPE).isEqualTo("application/octet-stream")

        val chooser = ShareHelper.createShareAsDocumentChooserIntent(dummyContext, file.absolutePath)
        assertThat(chooser).isNotNull()
    }

    @Test
    fun `buildMultipleShareAsDocumentIntent creates valid batch document intent`() {
        val dummyContext = object : ContextWrapper(null) {
            override fun getPackageName(): String = "com.tapconvert.app"
        }
        val file1 = tempFolder.newFile("vid1.mp4")
        val file2 = tempFolder.newFile("vid2.mp4")

        val intent = ShareHelper.buildMultipleShareAsDocumentIntent(dummyContext, listOf(file1.absolutePath, file2.absolutePath))
        assertThat(intent).isNotNull()

        val chooser = ShareHelper.createMultipleShareAsDocumentChooserIntent(dummyContext, listOf(file1.absolutePath, file2.absolutePath))
        assertThat(chooser).isNotNull()
    }
}



