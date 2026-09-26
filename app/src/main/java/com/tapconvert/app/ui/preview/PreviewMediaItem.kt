package com.tapconvert.app.ui.preview

import com.tapconvert.core.database.entity.ConversionRecordEntity
import com.tapconvert.core.model.ConversionResult
import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MediaCategory
import com.tapconvert.core.model.MimeType
import java.io.File

data class PreviewMediaItem(
    val uriString: String,
    val file: File,
    val fileName: String,
    val fileSizeBytes: Long,
    val category: MediaCategory,
    val mimeType: String,
    val originalUriString: String? = null,
    val originalSizeBytes: Long = 0L,
    val percentageSaved: Int = 0,
    val conversionType: String = ""
) {
    companion object {
        fun fromResult(result: ConversionResult, record: ConversionRecordEntity, targetUri: String? = null): PreviewMediaItem? {
            val chosenUri = targetUri ?: result.outputUris.firstOrNull() ?: return null
            val file = File(chosenUri.removePrefix("file://"))
            val mime = MimeType.fromFileName(file.name)
            val cat = when {
                mime.isVideo -> MediaCategory.VIDEO
                mime.isImage -> MediaCategory.IMAGE
                mime.isAudio -> MediaCategory.AUDIO
                mime.isPdf -> MediaCategory.DOCUMENT
                else -> MediaCategory.IMAGE
            }

            val originalUri = record.inputUris.firstOrNull()

            return PreviewMediaItem(
                uriString = chosenUri,
                file = file,
                fileName = file.name,
                fileSizeBytes = if (file.exists()) file.length() else result.outputSizeBytes,
                category = cat,
                mimeType = mime.mimeString,
                originalUriString = originalUri,
                originalSizeBytes = result.originalSizeBytes,
                percentageSaved = result.percentageSaved,
                conversionType = result.conversionType.name
            )
        }

        fun fromRecord(record: ConversionRecordEntity, targetUri: String? = null): PreviewMediaItem? {
            val chosenUri = targetUri ?: record.outputUris.firstOrNull() ?: return null
            val file = File(chosenUri.removePrefix("file://"))
            val mime = MimeType.fromFileName(file.name)
            val cat = when {
                mime.isVideo -> MediaCategory.VIDEO
                mime.isPdf -> MediaCategory.DOCUMENT
                mime.isAudio -> MediaCategory.AUDIO
                mime.isImage -> MediaCategory.IMAGE
                record.conversionType.endsWith("PDF") || record.conversionType.contains("PDF_COMPRESS") -> MediaCategory.DOCUMENT
                record.conversionType.contains("AUDIO") -> MediaCategory.AUDIO
                record.conversionType.contains("VIDEO") -> MediaCategory.VIDEO
                record.conversionType.contains("IMAGE") -> MediaCategory.IMAGE
                else -> MediaCategory.IMAGE
            }

            return PreviewMediaItem(
                uriString = chosenUri,
                file = file,
                fileName = file.name,
                fileSizeBytes = if (file.exists()) file.length() else record.outputSizeBytes,
                category = cat,
                mimeType = mime.mimeString,
                originalUriString = record.inputUris.firstOrNull(),
                originalSizeBytes = record.originalSizeBytes,
                percentageSaved = record.savingsPercentage.toInt(),
                conversionType = record.conversionType
            )
        }
    }
}
