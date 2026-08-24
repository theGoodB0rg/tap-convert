package com.tapconvert.core.common.intake

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class MediaIntakeManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var stagingDir: File
    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var intakeManager: MediaIntakeManager

    @Before
    fun setup() {
        stagingDir = tempFolder.newFolder("staging")
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        every { context.contentResolver } returns contentResolver
        every { context.cacheDir } returns tempFolder.newFolder("cache")
        intakeManager = DefaultMediaIntakeManager()

        mockkStatic(Uri::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private fun createMockUri(pathOrUri: String, isContent: Boolean = false): Uri {
        val uri = mockk<Uri>(relaxed = true)
        if (isContent) {
            every { uri.scheme } returns "content"
            every { uri.path } returns pathOrUri
            every { uri.toString() } returns pathOrUri
            every { uri.lastPathSegment } returns pathOrUri.substringAfterLast('/')
        } else {
            every { uri.scheme } returns "file"
            every { uri.path } returns pathOrUri
            every { uri.toString() } returns "file://$pathOrUri"
            every { uri.lastPathSegment } returns File(pathOrUri).name
        }
        return uri
    }

    @Test
    fun `stageUris with empty list returns IntakeResult Empty`() = runTest {
        val result = intakeManager.stageUris(context, emptyList(), stagingDir)
        assertThat(result).isInstanceOf(IntakeResult.Empty::class.java)
    }

    @Test
    fun `stageUris with file URIs copies files to staging and returns Success`() = runTest {
        val sourceFile1 = tempFolder.newFile("photo1.jpg").apply { writeBytes(ByteArray(1024) { 1 }) }
        val sourceFile2 = tempFolder.newFile("video1.mp4").apply { writeBytes(ByteArray(2048) { 2 }) }

        val uri1 = createMockUri(sourceFile1.absolutePath, isContent = false)
        val uri2 = createMockUri(sourceFile2.absolutePath, isContent = false)

        val result = intakeManager.stageUris(context, listOf(uri1, uri2), stagingDir)

        assertThat(result).isInstanceOf(IntakeResult.Success::class.java)
        val success = result as IntakeResult.Success
        assertThat(success.items).hasSize(2)
        assertThat(success.totalSizeBytes).isEqualTo(3072L)
        assertThat(success.items[0].originalName).isEqualTo("photo1.jpg")
        assertThat(success.items[1].originalName).isEqualTo("video1.mp4")

        // Verify staged files actually exist in stagingDir
        for (item in success.items) {
            val stagedFile = File(item.uri.removePrefix("file://"))
            assertThat(stagedFile.exists()).isTrue()
            assertThat(stagedFile.parentFile?.absolutePath).isEqualTo(stagingDir.absolutePath)
        }
    }

    @Test
    fun `stageUris with content URI streams content to staging via ContentResolver`() = runTest {
        val contentUri = createMockUri("content://media/external/images/media/999.jpg", isContent = true)
        val contentBytes = "test image data content".toByteArray()

        every { contentResolver.openInputStream(contentUri) } answers {
            ByteArrayInputStream(contentBytes)
        }
        every { contentResolver.query(contentUri, any(), any(), any(), any()) } returns null

        val result = intakeManager.stageUris(context, listOf(contentUri), stagingDir)

        assertThat(result).isInstanceOf(IntakeResult.Success::class.java)
        val success = result as IntakeResult.Success
        assertThat(success.items).hasSize(1)
        assertThat(success.totalSizeBytes).isEqualTo(contentBytes.size.toLong())

        val stagedFile = File(success.items[0].uri.removePrefix("file://"))
        assertThat(stagedFile.exists()).isTrue()
        assertThat(stagedFile.readBytes()).isEqualTo(contentBytes)
    }

    @Test
    fun `stageUris handles SecurityException or stream errors gracefully`() = runTest {
        val brokenUri = createMockUri("content://restricted/media/123", isContent = true)
        every { contentResolver.openInputStream(brokenUri) } throws SecurityException("Permission Denial")

        val result = intakeManager.stageUris(context, listOf(brokenUri), stagingDir)

        assertThat(result).isInstanceOf(IntakeResult.Empty::class.java)
    }
}
