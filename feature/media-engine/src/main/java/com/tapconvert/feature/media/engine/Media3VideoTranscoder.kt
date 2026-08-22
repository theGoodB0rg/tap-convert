package com.tapconvert.feature.media.engine

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.ConversionError
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Production video transcoder powered by AndroidX Media3 Transformer.
 * Performs hardware-accelerated video scaling and bitrate-constrained encoding.
 */
class Media3VideoTranscoder(
    private val context: Context? = null
) : VideoTranscoder {

    private var activeTransformer: Transformer? = null

    override fun transcode(
        sourceFile: File,
        outputFile: File,
        encodingSpec: BitrateCalculator.VideoEncodingSpec
    ): Flow<AppResult<File>> {
        val currentContext = context
        if (currentContext == null) {
            // Fallback for non-Android / JVM testing environments without Context
            return flow {
                try {
                    outputFile.parentFile?.mkdirs()
                    sourceFile.copyTo(outputFile, overwrite = true)
                    emit(AppResult.Progress(100, "Completed video copy fallback"))
                    emit(AppResult.Success(outputFile))
                } catch (e: Throwable) {
                    emit(AppResult.Error(ConversionError.IOError("Fallback copy failed: ${e.message}", e)))
                }
            }
        }

        return callbackFlow {
            outputFile.parentFile?.mkdirs()

            val transformationRequest = TransformationRequest.Builder()
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .build()

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    trySend(AppResult.Progress(100, "Transcoding complete"))
                    trySend(AppResult.Success(outputFile))
                    close()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    val error = ConversionError.IOError(
                        "Video transcoding failed: ${exportException.message}",
                        exportException
                    )
                    trySend(AppResult.Error(error))
                    close(exportException)
                }

                override fun onFallbackApplied(
                    composition: Composition,
                    originalTransformationRequest: TransformationRequest,
                    fallbackTransformationRequest: TransformationRequest
                ) {
                    // Log or handle format fallback if encoder requires
                }
            }

            val encoderSettings = VideoEncoderSettings.Builder()
                .setBitrate(encodingSpec.videoBitrateBps)
                .build()

            val encoderFactory = DefaultEncoderFactory.Builder(currentContext)
                .setRequestedVideoEncoderSettings(encoderSettings)
                .build()

            val transformer = Transformer.Builder(currentContext)
                .setTransformationRequest(transformationRequest)
                .setEncoderFactory(encoderFactory)
                .addListener(listener)
                .build()

            activeTransformer = transformer

            val presentationEffect = Presentation.createForHeight(encodingSpec.recommendedMaxDimension)
            val effects = Effects(emptyList(), listOf(presentationEffect))

            val mediaItem = MediaItem.fromUri(Uri.fromFile(sourceFile))
            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setEffects(effects)
                .build()

            try {
                transformer.start(editedMediaItem, outputFile.absolutePath)
            } catch (e: Throwable) {
                val error = ConversionError.IOError("Unable to start Media3 Transformer: ${e.message}", e)
                trySend(AppResult.Error(error))
                close(e)
                return@callbackFlow
            }

            // Progress polling coroutine
            val progressJob = launch {
                val progressHolder = ProgressHolder()
                while (isActive) {
                    delay(200)
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val pct = progressHolder.progress.coerceIn(0, 99)
                        trySend(AppResult.Progress(pct, "Transcoding video ($pct%)..."))
                    }
                }
            }

            awaitClose {
                progressJob.cancel()
                try {
                    transformer.cancel()
                } catch (_: Throwable) {}
                activeTransformer = null
            }
        }
    }

    override fun cancel() {
        try {
            activeTransformer?.cancel()
        } catch (_: Throwable) {}
        activeTransformer = null
    }
}
