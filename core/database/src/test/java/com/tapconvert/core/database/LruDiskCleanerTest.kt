package com.tapconvert.core.database

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.database.cleaner.LruDiskCleaner
import com.tapconvert.core.database.entity.ConversionRecordEntity
import com.tapconvert.core.database.repository.InMemoryConversionHistoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LruDiskCleanerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var cacheDir: File
    private lateinit var fakeRepository: InMemoryConversionHistoryRepository
    private lateinit var cleaner: LruDiskCleaner

    @Before
    fun setUp() {
        cacheDir = tempFolder.newFolder("tapconvert_cache")
        fakeRepository = InMemoryConversionHistoryRepository()
        cleaner = LruDiskCleaner(
            cacheDirectories = listOf(cacheDir),
            repository = fakeRepository,
            maxAgeMs = 48 * 3600 * 1000L,
            maxStorageBudgetBytes = 1000L // 1000 bytes for testing
        )
    }

    @Test
    fun `enforceStorageQuota deletes oldest files when budget is exceeded`() {
        // Create 3 files: total 1500 bytes (budget is 1000, target 800)
        val file1 = File(cacheDir, "file1.dat").apply {
            writeBytes(ByteArray(500))
            setLastModified(1000L) // Oldest
        }
        val file2 = File(cacheDir, "file2.dat").apply {
            writeBytes(ByteArray(500))
            setLastModified(2000L) // Middle
        }
        val file3 = File(cacheDir, "file3.dat").apply {
            writeBytes(ByteArray(500))
            setLastModified(3000L) // Newest
        }

        val report = cleaner.enforceStorageQuota(storageBudgetBytes = 1000L)

        // 1500 bytes > 1000 budget, needs to drop to <= 800 bytes
        // Deleting file1 (500B) leaves 1000B (still > 800), deleting file2 (500B) leaves 500B (<= 800)
        assertThat(report.filesDeleted).isEqualTo(2)
        assertThat(report.bytesReclaimed).isEqualTo(1000L)
        assertThat(file1.exists()).isFalse()
        assertThat(file2.exists()).isFalse()
        assertThat(file3.exists()).isTrue()
    }

    @Test
    fun `performManualCachePurge deletes non-favorited files and protects favorited files`() = runTest {
        val nonFavFile = File(cacheDir, "out_non_fav.mp4").apply { writeBytes(ByteArray(400)) }
        val favFile = File(cacheDir, "out_fav.mp4").apply { writeBytes(ByteArray(600)) }

        val nonFavRecord = ConversionRecordEntity(
            id = "rec1",
            conversionType = "VIDEO_COMPRESS",
            inputUris = listOf("file:///in.mp4"),
            outputUris = listOf("file://${nonFavFile.absolutePath}"),
            originalSizeBytes = 1000L,
            outputSizeBytes = 400L,
            savedBytes = 600L,
            durationMs = 100L,
            createdAt = System.currentTimeMillis(),
            isFavorited = false
        )
        val favRecord = ConversionRecordEntity(
            id = "rec2",
            conversionType = "VIDEO_COMPRESS",
            inputUris = listOf("file:///in2.mp4"),
            outputUris = listOf("file://${favFile.absolutePath}"),
            originalSizeBytes = 1000L,
            outputSizeBytes = 600L,
            savedBytes = 400L,
            durationMs = 100L,
            createdAt = System.currentTimeMillis(),
            isFavorited = true
        )

        fakeRepository.save(nonFavRecord)
        fakeRepository.save(favRecord)

        val report = cleaner.performManualCachePurge(protectFavorites = true)

        assertThat(report.filesDeleted).isAtLeast(1)
        assertThat(nonFavFile.exists()).isFalse()
        assertThat(fakeRepository.getById("rec1")).isNull()

        // Favorited record and its file should be preserved
        assertThat(favFile.exists()).isTrue()
        assertThat(fakeRepository.getById("rec2")).isNotNull()
    }

    @Test
    fun `performManualCachePurge recursively cleans subdirectories including intake_staging`() = runTest {
        val stagingSubdir = File(cacheDir, "intake_staging").apply { mkdirs() }
        val stagedFile = File(stagingSubdir, "staged_photo.jpg").apply { writeBytes(ByteArray(300)) }

        assertThat(stagedFile.exists()).isTrue()

        val report = cleaner.performManualCachePurge(protectFavorites = true)

        assertThat(report.filesDeleted).isAtLeast(1)
        assertThat(report.bytesReclaimed).isAtLeast(300L)
        assertThat(stagedFile.exists()).isFalse()
    }

    @Test
    fun `deleteRecordWithFiles removes physical output files and repository record`() = runTest {
        val outFile = File(cacheDir, "test_file.png").apply { writeBytes(ByteArray(250)) }
        val record = ConversionRecordEntity(
            id = "rec_del",
            conversionType = "IMAGE_COMPRESS",
            inputUris = listOf("file:///raw.png"),
            outputUris = listOf("file://${outFile.absolutePath}"),
            originalSizeBytes = 500L,
            outputSizeBytes = 250L,
            savedBytes = 250L,
            durationMs = 50L,
            createdAt = System.currentTimeMillis(),
            isFavorited = false
        )

        fakeRepository.save(record)
        assertThat(outFile.exists()).isTrue()

        val result = cleaner.deleteRecordWithFiles("rec_del")

        assertThat(result).isTrue()
        assertThat(outFile.exists()).isFalse()
        assertThat(fakeRepository.getById("rec_del")).isNull()
    }
}
