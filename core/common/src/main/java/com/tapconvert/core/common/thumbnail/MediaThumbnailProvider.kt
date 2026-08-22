package com.tapconvert.core.common.thumbnail

import android.content.Context
import android.graphics.Bitmap
import com.tapconvert.core.model.MediaCategory

/**
 * Result state returned by MediaThumbnailProvider.
 */
sealed class ThumbnailResult {
    data class Loaded(val bitmap: Bitmap) : ThumbnailResult()
    data class FallbackIcon(val category: MediaCategory) : ThumbnailResult()
    data class Error(val throwable: Throwable? = null) : ThumbnailResult()
}

/**
 * Interface defining asynchronous thumbnail retrieval across multiple media categories.
 */
interface MediaThumbnailProvider {

    /**
     * Loads a downsampled thumbnail Bitmap or fallback metadata for [uriOrPath].
     */
    suspend fun loadThumbnail(
        context: Context,
        uriOrPath: String,
        targetSizePx: Int = 160
    ): ThumbnailResult

    /**
     * Clears in-memory thumbnail cache.
     */
    fun clearCache()
}
