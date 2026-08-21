package com.tapconvert.core.database.repository

import com.tapconvert.core.database.entity.ConversionRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

interface ConversionHistoryRepository {
    fun getAll(): Flow<List<ConversionRecordEntity>>
    suspend fun getById(id: String): ConversionRecordEntity?
    suspend fun save(record: ConversionRecordEntity): Long
    suspend fun saveAll(records: List<ConversionRecordEntity>)
    suspend fun deleteById(id: String): Boolean
    suspend fun setFavorited(id: String, isFavorited: Boolean): Boolean
    suspend fun getExpiredNonFavorited(olderThanTimestamp: Long): List<ConversionRecordEntity>
    suspend fun deleteExpiredNonFavorited(olderThanTimestamp: Long): Int
    suspend fun getFavorited(): List<ConversionRecordEntity>
    suspend fun getNonFavorited(): List<ConversionRecordEntity>
    suspend fun deleteNonFavorited(): Int
    fun getTotalStorageUsage(): Flow<Long>
    suspend fun clearAll()
}

class InMemoryConversionHistoryRepository : ConversionHistoryRepository {

    private val storage = ConcurrentHashMap<String, ConversionRecordEntity>()
    private val _recordsFlow = MutableStateFlow<List<ConversionRecordEntity>>(emptyList())

    private fun updateFlow() {
        _recordsFlow.value = storage.values.sortedByDescending { it.createdAt }
    }

    override fun getAll(): Flow<List<ConversionRecordEntity>> = _recordsFlow.asStateFlow()

    override suspend fun getById(id: String): ConversionRecordEntity? = storage[id]

    override suspend fun save(record: ConversionRecordEntity): Long {
        storage[record.id] = record
        updateFlow()
        return 1L
    }

    override suspend fun saveAll(records: List<ConversionRecordEntity>) {
        records.forEach { storage[it.id] = it }
        updateFlow()
    }

    override suspend fun deleteById(id: String): Boolean {
        val removed = storage.remove(id) != null
        if (removed) updateFlow()
        return removed
    }

    override suspend fun setFavorited(id: String, isFavorited: Boolean): Boolean {
        val existing = storage[id] ?: return false
        storage[id] = existing.copy(isFavorited = isFavorited)
        updateFlow()
        return true
    }

    override suspend fun getExpiredNonFavorited(olderThanTimestamp: Long): List<ConversionRecordEntity> {
        return storage.values
            .filter { !it.isFavorited && it.createdAt < olderThanTimestamp }
            .sortedBy { it.createdAt }
    }

    override suspend fun deleteExpiredNonFavorited(olderThanTimestamp: Long): Int {
        val expiredKeys = storage.values
            .filter { !it.isFavorited && it.createdAt < olderThanTimestamp }
            .map { it.id }
        expiredKeys.forEach { storage.remove(it) }
        if (expiredKeys.isNotEmpty()) updateFlow()
        return expiredKeys.size
    }

    override suspend fun getFavorited(): List<ConversionRecordEntity> {
        return storage.values
            .filter { it.isFavorited }
            .sortedBy { it.createdAt }
    }

    override suspend fun getNonFavorited(): List<ConversionRecordEntity> {
        return storage.values
            .filter { !it.isFavorited }
            .sortedBy { it.createdAt }
    }

    override suspend fun deleteNonFavorited(): Int {
        val nonFavKeys = storage.values
            .filter { !it.isFavorited }
            .map { it.id }
        nonFavKeys.forEach { storage.remove(it) }
        if (nonFavKeys.isNotEmpty()) updateFlow()
        return nonFavKeys.size
    }

    override fun getTotalStorageUsage(): Flow<Long> {
        return _recordsFlow.map { list -> list.sumOf { it.outputSizeBytes } }
    }

    override suspend fun clearAll() {
        storage.clear()
        updateFlow()
    }
}

class RoomConversionHistoryRepository(
    private val dao: com.tapconvert.core.database.dao.ConversionDao
) : ConversionHistoryRepository {

    override fun getAll(): Flow<List<ConversionRecordEntity>> = dao.getAll()

    override suspend fun getById(id: String): ConversionRecordEntity? = dao.getById(id)

    override suspend fun save(record: ConversionRecordEntity): Long = dao.insert(record)

    override suspend fun saveAll(records: List<ConversionRecordEntity>) = dao.insertAll(records)

    override suspend fun deleteById(id: String): Boolean = dao.deleteById(id) > 0

    override suspend fun setFavorited(id: String, isFavorited: Boolean): Boolean = dao.setFavorited(id, isFavorited) > 0

    override suspend fun getExpiredNonFavorited(olderThanTimestamp: Long): List<ConversionRecordEntity> =
        dao.getExpiredNonFavorited(olderThanTimestamp)

    override suspend fun deleteExpiredNonFavorited(olderThanTimestamp: Long): Int =
        dao.deleteExpiredNonFavorited(olderThanTimestamp)

    override suspend fun getFavorited(): List<ConversionRecordEntity> = dao.getFavorited()

    override suspend fun getNonFavorited(): List<ConversionRecordEntity> = dao.getNonFavorited()

    override suspend fun deleteNonFavorited(): Int = dao.deleteNonFavorited()

    override fun getTotalStorageUsage(): Flow<Long> = dao.getTotalStorageUsage().map { it ?: 0L }

    override suspend fun clearAll() = dao.clearAll()

    companion object {
        fun create(context: android.content.Context): ConversionHistoryRepository {
            val db = com.tapconvert.core.database.TapConvertDatabase.getInstance(context)
            return RoomConversionHistoryRepository(db.conversionDao())
        }
    }
}


