package com.tapconvert.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tapconvert.core.database.entity.ConversionRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ConversionRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ConversionRecordEntity>)

    @Query("SELECT * FROM conversion_records ORDER BY created_at DESC")
    fun getAll(): Flow<List<ConversionRecordEntity>>

    @Query("SELECT * FROM conversion_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConversionRecordEntity?

    @Query("DELETE FROM conversion_records WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("UPDATE conversion_records SET is_favorited = :isFavorited WHERE id = :id")
    suspend fun setFavorited(id: String, isFavorited: Boolean): Int

    @Query("SELECT * FROM conversion_records WHERE is_favorited = 0 AND created_at < :olderThanTimestamp ORDER BY created_at ASC")
    suspend fun getExpiredNonFavorited(olderThanTimestamp: Long): List<ConversionRecordEntity>

    @Query("DELETE FROM conversion_records WHERE is_favorited = 0 AND created_at < :olderThanTimestamp")
    suspend fun deleteExpiredNonFavorited(olderThanTimestamp: Long): Int

    @Query("SELECT * FROM conversion_records WHERE is_favorited = 1")
    suspend fun getFavorited(): List<ConversionRecordEntity>

    @Query("SELECT * FROM conversion_records WHERE is_favorited = 0 ORDER BY created_at ASC")
    suspend fun getNonFavorited(): List<ConversionRecordEntity>

    @Query("DELETE FROM conversion_records WHERE is_favorited = 0")
    suspend fun deleteNonFavorited(): Int

    @Query("SELECT SUM(output_size_bytes) FROM conversion_records")
    fun getTotalStorageUsage(): Flow<Long?>

    @Query("DELETE FROM conversion_records")
    suspend fun clearAll()
}
