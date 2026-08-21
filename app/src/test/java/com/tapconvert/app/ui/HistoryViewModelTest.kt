package com.tapconvert.app.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tapconvert.app.ui.history.HistoryViewModel
import com.tapconvert.core.database.cleaner.LruDiskCleaner
import com.tapconvert.core.database.entity.ConversionRecordEntity
import com.tapconvert.core.database.repository.InMemoryConversionHistoryRepository
import com.tapconvert.core.model.ConversionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `history records and favorite filter flow properly`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = InMemoryConversionHistoryRepository()
        val r1 = ConversionRecordEntity(
            id = "rec1",
            conversionType = ConversionType.IMAGE_COMPRESS.name,
            inputUris = listOf("file:///in1.jpg"),
            outputUris = listOf("file:///out1.jpg"),
            originalSizeBytes = 1000L,
            outputSizeBytes = 500L,
            savedBytes = 500L,
            durationMs = 50L,
            isFavorited = false
        )
        val r2 = ConversionRecordEntity(
            id = "rec2",
            conversionType = ConversionType.PDF_TO_IMAGES.name,
            inputUris = listOf("file:///in2.pdf"),
            outputUris = listOf("file:///out2.jpg"),
            originalSizeBytes = 2000L,
            outputSizeBytes = 1000L,
            savedBytes = 1000L,
            durationMs = 100L,
            isFavorited = true
        )

        repository.save(r1)
        repository.save(r2)

        val viewModel = HistoryViewModel(
            repository = repository,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher
        )

        viewModel.records.test {
            var item = awaitItem()
            if (item.isEmpty()) {
                item = awaitItem()
            }
            assertThat(item).hasSize(2)

            viewModel.toggleFavoritesFilter()
            val filtered = awaitItem()
            assertThat(filtered).hasSize(1)
            assertThat(filtered[0].id).isEqualTo("rec2")
        }
    }

    @Test
    fun `deleteRecord removes record from repository`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = InMemoryConversionHistoryRepository()
        val r = ConversionRecordEntity(
            id = "to_delete",
            conversionType = ConversionType.IMAGE_COMPRESS.name,
            inputUris = listOf("file:///in.jpg"),
            outputUris = listOf("file:///out.jpg"),
            originalSizeBytes = 1000L,
            outputSizeBytes = 500L,
            savedBytes = 500L,
            durationMs = 50L
        )
        repository.save(r)
        assertThat(repository.getById("to_delete")).isNotNull()

        val viewModel = HistoryViewModel(
            repository = repository,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher
        )
        viewModel.deleteRecord("to_delete")

        assertThat(repository.getById("to_delete")).isNull()
    }

    @Test
    fun `triggerDiskCleanup purges non-favorited records and reports reclaimed metrics`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val cacheDir = tempFolder.newFolder("vm_cache")
        val outFile = File(cacheDir, "vm_out.mp4").apply { writeBytes(ByteArray(800)) }

        val repository = InMemoryConversionHistoryRepository()
        val nonFavRecord = ConversionRecordEntity(
            id = "rec_non_fav",
            conversionType = ConversionType.VIDEO_COMPRESS.name,
            inputUris = listOf("file:///in.mp4"),
            outputUris = listOf("file://${outFile.absolutePath}"),
            originalSizeBytes = 1600L,
            outputSizeBytes = 800L,
            savedBytes = 800L,
            durationMs = 100L,
            isFavorited = false
        )
        repository.save(nonFavRecord)

        val diskCleaner = LruDiskCleaner(
            cacheDirectories = listOf(cacheDir),
            repository = repository
        )

        val viewModel = HistoryViewModel(
            repository = repository,
            diskCleaner = diskCleaner,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher
        )

        var reportedFiles = -1
        var reportedBytes = -1L

        viewModel.triggerDiskCleanup { report ->
            reportedFiles = report.filesDeleted
            reportedBytes = report.bytesReclaimed
        }

        assertThat(reportedFiles).isAtLeast(1)
        assertThat(reportedBytes).isAtLeast(800L)
        assertThat(outFile.exists()).isFalse()
        assertThat(repository.getById("rec_non_fav")).isNull()
    }
}
