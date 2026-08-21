package com.tapconvert.feature.media.engine

import android.media.MediaMetadataRetriever
import java.io.File

object MediaMetadataRetrieverHelper {

    data class MediaInfo(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val rotationDegrees: Int,
        val hasAudio: Boolean,
        val mimeType: String?
    ) {
        val durationSeconds: Double
            get() = durationMs / 1000.0
    }

    /**
     * Extracts duration, dimensions, rotation and track information from a media file.
     */
    fun extractMediaInfo(file: File): MediaInfo? {
        if (!file.exists() || file.length() == 0L) return null

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val width = widthStr?.toIntOrNull() ?: 0

            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val height = heightStr?.toIntOrNull() ?: 0

            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val rotation = rotationStr?.toIntOrNull() ?: 0

            val hasAudioStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            val hasAudio = hasAudioStr != null && hasAudioStr == "yes"

            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

            MediaInfo(
                durationMs = durationMs,
                width = width,
                height = height,
                rotationDegrees = rotation,
                hasAudio = hasAudio,
                mimeType = mimeType
            )
        } catch (_: Throwable) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {}
        }
    }
}
