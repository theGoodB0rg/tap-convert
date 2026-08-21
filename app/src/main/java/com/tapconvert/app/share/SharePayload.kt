package com.tapconvert.app.share

import com.tapconvert.core.model.MediaCategory

data class SharePayload(
    val sourceUris: List<String>,
    val fileNames: List<String>,
    val mimeType: String,
    val category: MediaCategory,
    val totalSizeBytes: Long,
    val isMultiple: Boolean = sourceUris.size > 1
)
