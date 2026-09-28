@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.tapconvert.feature.media.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.ConversionError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** Re-encodes only the audio track, leaving video to the low-floor native path. */
interface AudioTrackReencoder {
    fun transcodeAudio(sourceFile: File, outputFile: File, bitrateBps: Int): Flow<AppResult<File>>
}

/** Keeps the fast/platform audio path for ordinary budgets and uses FFmpeg
 * only when the platform codec floor is the reason a byte contract is at risk. */
class HybridAudioTrackReencoder(
    private val normal: AudioTrackReencoder,
    private val constrained: AudioTrackReencoder,
    private val constrainedThresholdBps: Int = 32_000
) : AudioTrackReencoder {
    override fun transcodeAudio(sourceFile: File, outputFile: File, bitrateBps: Int): Flow<AppResult<File>> =
        if (bitrateBps <= constrainedThresholdBps) {
            constrained.transcodeAudio(sourceFile, outputFile, bitrateBps)
        } else {
            normal.transcodeAudio(sourceFile, outputFile, bitrateBps)
        }
}

class Media3AudioTrackReencoder(
    private val context: Context,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val looper: Looper? = context.mainLooper
) : AudioTrackReencoder {

    @Volatile
    private var activeTransformer: Transformer? = null

    override fun transcodeAudio(
        sourceFile: File,
        outputFile: File,
        bitrateBps: Int
    ): Flow<AppResult<File>> = callbackFlow {
        outputFile.parentFile?.mkdirs()
        val sourceChannelCount = sourceAudioChannelCount(sourceFile)
        val audioSettings = AudioEncoderSettings.Builder()
            .setBitrate(bitrateBps.coerceIn(8_000, 192_000))
            .build()
        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedAudioEncoderSettings(audioSettings)
            .build()
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                trySend(AppResult.Progress(100, "Audio re-encoding complete"))
                trySend(AppResult.Success(outputFile))
                close()
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                val error = ConversionError.IOError(
                    "Audio re-encoding failed: ${exportException.message}",
                    exportException
                )
                trySend(AppResult.Error(error))
                close(exportException)
            }
        }
        val builder = Transformer.Builder(context)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setAudioProcessors(
                if (bitrateBps <= 32_000) {
                    listOf(LowRateMonoAudioProcessor())
                } else if (sourceChannelCount >= 2) {
                    listOf(
                        ChannelMixingAudioProcessor().apply {
                            putChannelMixingMatrix(
                                ChannelMixingMatrix(
                                    2,
                                    1,
                                    floatArrayOf(0.5f, 0.5f)
                                )
                            )
                        }
                    )
                } else {
                    emptyList()
                }
            )
            .setEncoderFactory(encoderFactory)
            .addListener(listener)
        if (looper != null) builder.setLooper(looper)
        val transformer = builder.build()
        activeTransformer = transformer
        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(sourceFile)))
            .setRemoveVideo(true)
            .build()
        try {
            withContext(mainDispatcher) {
                transformer.start(item, outputFile.absolutePath)
            }
        } catch (error: Throwable) {
            trySend(AppResult.Error(ConversionError.IOError("Unable to start audio re-encoding: ${error.message}", error)))
            close(error)
            return@callbackFlow
        }

        val progressJob = launch(mainDispatcher) {
            while (isActive) {
                delay(250)
                trySend(AppResult.Progress(50, "Re-encoding audio track..."))
            }
        }
        awaitClose {
            progressJob.cancel()
            runCatching { transformer.cancel() }
            activeTransformer = null
        }
    }

    private fun sourceAudioChannelCount(sourceFile: File): Int {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(sourceFile.absolutePath)
            val audioFormat = (0 until extractor.trackCount)
                .map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            audioFormat?.let { runCatching { it.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(1) } ?: 1
        } catch (_: Throwable) {
            1
        } finally {
            runCatching { extractor.release() }
        }
    }
}
