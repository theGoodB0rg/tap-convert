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
        val mimeType: String?,
        val overallBitrateBps: Int = 0,
        val fileSizeBytes: Long = 0L
    ) {
        val durationSeconds: Double
            get() = (durationMs / 1000.0).coerceAtLeast(0.1)

        val isPortrait: Boolean
            get() = if (rotationDegrees == 90 || rotationDegrees == 270) width > height else height > width
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

            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val bitrate = bitrateStr?.toIntOrNull() ?: 0

            MediaInfo(
                durationMs = durationMs,
                width = width,
                height = height,
                rotationDegrees = rotation,
                hasAudio = hasAudio,
                mimeType = mimeType,
                overallBitrateBps = bitrate,
                fileSizeBytes = file.length()
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

