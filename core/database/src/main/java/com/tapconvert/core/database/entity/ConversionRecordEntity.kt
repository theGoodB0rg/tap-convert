package com.tapconvert.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tapconvert.core.model.ConversionResult
import com.tapconvert.core.model.ConversionType
import java.util.UUID

@Entity(tableName = "conversion_records")
data class ConversionRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "conversion_type")
    val conversionType: String,

    @ColumnInfo(name = "input_uris")
    val inputUris: List<String>,

    @ColumnInfo(name = "output_uris")
    val outputUris: List<String>,

    @ColumnInfo(name = "original_size_bytes")
    val originalSizeBytes: Long,

    @ColumnInfo(name = "output_size_bytes")
    val outputSizeBytes: Long,

    @ColumnInfo(name = "saved_bytes")
    val savedBytes: Long,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    @ColumnInfo(name = "preset_id")
    val presetId: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_favorited")
    val isFavorited: Boolean = false,

    @ColumnInfo(name = "metadata")
    val metadata: Map<String, String> = emptyMap()
) {
    val savingsPercentage: Float
        get() = if (originalSizeBytes > 0) {
            ((savedBytes.toFloat() / originalSizeBytes.toFloat()) * 100f).coerceIn(0f, 100f)
        } else {
            0f
        }

    companion object {
        fun fromDomain(
            result: ConversionResult,
            inputUris: List<String>,
            presetId: String? = null,
            isFavorited: Boolean = false
        ): ConversionRecordEntity {
            return ConversionRecordEntity(
                id = result.requestId,
                conversionType = result.conversionType.name,
                inputUris = inputUris,
                outputUris = result.outputUris,
                originalSizeBytes = result.originalSizeBytes,
                outputSizeBytes = result.outputSizeBytes,
                savedBytes = result.bytesSaved,
                durationMs = result.durationMs,
                presetId = presetId,
                createdAt = System.currentTimeMillis(),
                isFavorited = isFavorited,
                metadata = result.metadata
            )
        }
    }
}
