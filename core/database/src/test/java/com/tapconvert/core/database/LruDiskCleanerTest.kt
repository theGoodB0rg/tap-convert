package com.tapconvert.core.database

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.database.cleaner.LruDiskCleaner
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LruDiskCleanerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var cacheDir: File
    private lateinit var cleaner: LruDiskCleaner

    @Before
    fun setUp() {
        cacheDir = tempFolder.newFolder("tapconvert_cache")
        cleaner = LruDiskCleaner(
            cacheDirectories = listOf(cacheDir),
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
}
