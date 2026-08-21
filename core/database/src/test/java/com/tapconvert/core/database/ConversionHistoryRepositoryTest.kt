package com.tapconvert.core.database

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.database.entity.ConversionRecordEntity
import com.tapconvert.core.database.repository.InMemoryConversionHistoryRepository
import com.tapconvert.core.model.ConversionType
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ConversionHistoryRepositoryTest {

    private val repository = InMemoryConversionHistoryRepository()

    @Test
    fun `save and getAll returns sorted records in descending creation order`() = runTest {
        val record1 = ConversionRecordEntity(
            id = "rec_1",
            conversionType = ConversionType.IMAGE_COMPRESS.name,
            inputUris = listOf("file:///in1.jpg"),
            outputUris = listOf("file:///out1.jpg"),
            originalSizeBytes = 1000L,
            outputSizeBytes = 500L,
            savedBytes = 500L,
            durationMs = 100L,
            createdAt = 1000L
        )

        val record2 = ConversionRecordEntity(
            id = "rec_2",
            conversionType = ConversionType.PDF_TO_IMAGES.name,
            inputUris = listOf("file:///in2.pdf"),
            outputUris = listOf("file:///out2.jpg"),
            originalSizeBytes = 2000L,
            outputSizeBytes = 1000L,
            savedBytes = 1000L,
            durationMs = 200L,
            createdAt = 2000L // newer
        )

        repository.save(record1)
        repository.save(record2)

        repository.getAll().test {
            val list = awaitItem()
            assertThat(list).hasSize(2)
            assertThat(list[0].id).isEqualTo("rec_2") // newest first
            assertThat(list[1].id).isEqualTo("rec_1")
        }
    }

    @Test
    fun `setFavorited updates favorite status`() = runTest {
        val record = ConversionRecordEntity(
            id = "rec_fav",
            conversionType = ConversionType.VIDEO_COMPRESS.name,
            inputUris = listOf("file:///in.mp4"),
            outputUris = listOf("file:///out.mp4"),
            originalSizeBytes = 5000L,
            outputSizeBytes = 2000L,
            savedBytes = 3000L,
            durationMs = 500L,
            isFavorited = false
        )

        repository.save(record)
        assertThat(repository.getById("rec_fav")?.isFavorited).isFalse()

        repository.setFavorited("rec_fav", true)
        assertThat(repository.getById("rec_fav")?.isFavorited).isTrue()
    }

    @Test
    fun `deleteExpiredNonFavorited protects favorited items from deletion`() = runTest {
        val oldExpired = ConversionRecordEntity(
            id = "old_expired",
            conversionType = ConversionType.IMAGE_COMPRESS.name,
            inputUris = listOf("file:///in.jpg"),
            outputUris = listOf("file:///out.jpg"),
            originalSizeBytes = 1000L,
            outputSizeBytes = 500L,
            savedBytes = 500L,
            durationMs = 100L,
            createdAt = 1000L,
            isFavorited = false
        )

        val oldFavorited = ConversionRecordEntity(
            id = "old_favorited",
            conversionType = ConversionType.IMAGE_COMPRESS.name,
            inputUris = listOf("file:///in.jpg"),
            outputUris = listOf("file:///out.jpg"),
            originalSizeBytes = 1000L,
            outputSizeBytes = 500L,
            savedBytes = 500L,
            durationMs = 100L,
            createdAt = 1000L,
            isFavorited = true
        )

        repository.save(oldExpired)
        repository.save(oldFavorited)

        val deletedCount = repository.deleteExpiredNonFavorited(olderThanTimestamp = 5000L)
        assertThat(deletedCount).isEqualTo(1)

        assertThat(repository.getById("old_expired")).isNull()
        assertThat(repository.getById("old_favorited")).isNotNull()
    }
}
