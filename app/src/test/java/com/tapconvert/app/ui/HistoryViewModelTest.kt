package com.tapconvert.app.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tapconvert.app.ui.history.HistoryViewModel
import com.tapconvert.core.database.entity.ConversionRecordEntity
import com.tapconvert.core.database.repository.InMemoryConversionHistoryRepository
import com.tapconvert.core.model.ConversionType
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HistoryViewModelTest {

    @Test
    fun `history records and favorite filter flow properly`() = runTest {
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

        val viewModel = HistoryViewModel(repository)

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

        repository.deleteById("to_delete")
        assertThat(repository.getById("to_delete")).isNull()
    }
}
