package com.tapconvert.feature.media.engine

import com.tapconvert.core.common.AppResult
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Interface defining video transcoding operations.
 * Allows decoupling of high-level engine orchestration from low-level Android Media3/MediaCodec pipelines.
 */
interface VideoTranscoder {

    /**
     * Transcodes [sourceFile] to [outputFile] using constraints specified by [encodingSpec].
     * Emits [AppResult.Progress], [AppResult.Success], or [AppResult.Error].
     */
    fun transcode(
        sourceFile: File,
        outputFile: File,
        encodingSpec: BitrateCalculator.VideoEncodingSpec
    ): Flow<AppResult<File>>

    /**
     * Cancels any active transcoding session immediately.
     */
    fun cancel() {}
}
