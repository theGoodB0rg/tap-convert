package com.tapconvert.feature.media.engine

import android.content.Context
import com.tapconvert.core.analytics.AnalyticsTracker
import com.tapconvert.core.analytics.NoOpAnalyticsTracker
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.ConversionError
import com.tapconvert.core.model.ConversionProgress
import com.tapconvert.core.model.ConversionRequest
import com.tapconvert.core.model.ConversionResult
import com.tapconvert.core.model.ConversionStage
import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MimeType
import com.tapconvert.core.model.TargetSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.UUID

interface MediaEngine {
    fun compressVideo(request: ConversionRequest, outputDirectory: File): Flow<AppResult<ConversionResult>>
    fun extractAudio(request: ConversionRequest, outputDirectory: File): Flow<AppResult<ConversionResult>>
}

class DefaultMediaEngine(
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker(),
    private val transcoder: VideoTranscoder = Media3VideoTranscoder()
) : MediaEngine {

    override fun compressVideo(
        request: ConversionRequest,
        outputDirectory: File
    ): Flow<AppResult<ConversionResult>> = flow {
        val startTime = System.currentTimeMillis()
        if (request.sourceUris.isEmpty()) {
            val error = ConversionError.FileNotFound("No source video URI provided")
            analyticsTracker.logConversionFailed(ConversionType.VIDEO_COMPRESS, "FileNotFound", error.userReadableMessage, request.preset?.id)
            emit(AppResult.Error(error))
            return@flow
        }

        val totalFiles = request.sourceUris.size
        val outputUris = mutableListOf<String>()
        var totalOriginalSize = 0L
        var totalOutputSize = 0L
        outputDirectory.mkdirs()

        analyticsTracker.logConversionStarted(
            type = ConversionType.VIDEO_COMPRESS,
            inputSizeBytes = 0L,
            sourceFormat = "video/*",
            presetId = request.preset?.id
        )

        request.sourceUris.forEachIndexed { index, uriStr ->
            currentCoroutineContext().ensureActive()
            val sourceFile = File(uriStr.removePrefix("file://"))
            val originalSize = if (sourceFile.exists()) sourceFile.length() else 0L
            totalOriginalSize += originalSize

            val baseProgress = (index.toFloat() / totalFiles.toFloat() * 100f).toInt()
            emit(AppResult.Progress(
                percentage = baseProgress + (10 / totalFiles).coerceAtLeast(1),
                currentStep = ConversionProgress(15, ConversionStage.ANALYZING).overallSummary
            ))

            if (!sourceFile.exists()) {
                val error = ConversionError.FileNotFound(uriStr)
                analyticsTracker.logConversionFailed(ConversionType.VIDEO_COMPRESS, "FileNotFound", error.userReadableMessage, request.preset?.id)
                emit(AppResult.Error(error))
                return@flow
            }

            val mediaInfo = MediaMetadataRetrieverHelper.extractMediaInfo(sourceFile)
            val durationSeconds = mediaInfo?.durationSeconds ?: 60.0

            emit(AppResult.Progress(
                percentage = baseProgress + (25 / totalFiles).coerceAtLeast(1),
                currentStep = ConversionProgress(30, ConversionStage.PREPARING).overallSummary
            ))

            val encodingSpec = BitrateCalculator.calculateTargetBitrate(
                targetSize = request.targetSize,
                durationSeconds = durationSeconds,
                sourceSizeBytes = originalSize,
                sourceHeight = mediaInfo?.height ?: 1080,
                quality = request.quality,
                audioBitrateBps = request.customAudioBitrateKbps?.let { it * 1000 } ?: BitrateCalculator.DEFAULT_AUDIO_BITRATE_BPS
            )

            val outputFileName = if (totalFiles == 1 && !request.outputFileName.isNullOrBlank()) {
                request.outputFileName!!
            } else {
                com.tapconvert.core.common.ExportFileNameGenerator.generate(
                    originalName = sourceFile.name,
                    extension = "mp4",
                    batchIndex = if (totalFiles > 1) index + 1 else null,
                    fallbackName = "Video"
                )
            }
            val outputFile = File(outputDirectory, outputFileName)

            var transcodeSuccess = false
            var transcodeError: Throwable? = null

            transcoder.transcode(sourceFile, outputFile, encodingSpec).collect { transcodeResult ->
                when (transcodeResult) {
                    is AppResult.Progress -> {
                        val mappedPct = baseProgress + (35 + transcodeResult.percentage * 0.55f) / totalFiles
                        emit(AppResult.Progress(mappedPct.toInt(), ConversionProgress(mappedPct.toInt(), ConversionStage.COMPRESSING).overallSummary))
                    }
                    is AppResult.Success -> {
                        transcodeSuccess = true
                    }
                    is AppResult.Error -> {
                        transcodeError = transcodeResult.throwable
                    }
                }
            }

            if (!transcodeSuccess) {
                val failureError = transcodeError?.let {
                    if (it is ConversionError) it else ConversionError.IOError("Transcoding failed: ${it.message}", it)
                } ?: ConversionError.IOError("Unknown error occurred during video transcoding")

                analyticsTracker.logConversionFailed(
                    type = ConversionType.VIDEO_COMPRESS,
                    errorType = failureError::class.java.simpleName,
                    errorMessage = failureError.userReadableMessage,
                    presetId = request.preset?.id
                )
                emit(AppResult.Error(failureError))
                return@flow
            }

            totalOutputSize += outputFile.length()
            outputUris.add(outputFile.absolutePath)
        }

        emit(AppResult.Progress(95, ConversionProgress(95, ConversionStage.FINALIZING).overallSummary))

        val duration = System.currentTimeMillis() - startTime
        analyticsTracker.logConversionCompleted(
            type = ConversionType.VIDEO_COMPRESS,
            durationMs = duration,
            inputSizeBytes = totalOriginalSize,
            outputSizeBytes = totalOutputSize,
            presetId = request.preset?.id
        )

        emit(
            AppResult.Success(
                ConversionResult(
                    requestId = request.id,
                    conversionType = ConversionType.VIDEO_COMPRESS,
                    outputUris = outputUris,
                    originalSizeBytes = totalOriginalSize,
                    outputSizeBytes = totalOutputSize,
                    durationMs = duration,
                    metadata = mapOf(
                        "videoBitrateBps" to (request.targetSize?.bytes ?: 0L).toString(),
                        "audioBitrateBps" to BitrateCalculator.DEFAULT_AUDIO_BITRATE_BPS.toString(),
                        "maxDimension" to "1280",
                        "batchCount" to totalFiles.toString()
                    )
                )
            )
        )
    }.flowOn(Dispatchers.IO)

    override fun extractAudio(
        request: ConversionRequest,
        outputDirectory: File
    ): Flow<AppResult<ConversionResult>> = flow {
        val startTime = System.currentTimeMillis()
        if (request.sourceUris.isEmpty()) {
            val error = ConversionError.FileNotFound("No source media file provided")
            analyticsTracker.logConversionFailed(ConversionType.EXTRACT_AUDIO, "FileNotFound", error.userReadableMessage, request.preset?.id)
            emit(AppResult.Error(error))
            return@flow
        }

        val totalFiles = request.sourceUris.size
        val outputUris = mutableListOf<String>()
        var totalOriginalSize = 0L
        var totalOutputSize = 0L

        val targetMime = request.targetMimeType
        val extension = if (targetMime is MimeType.Audio) targetMime.primaryExtension else "m4a"
        outputDirectory.mkdirs()

        analyticsTracker.logConversionStarted(
            type = ConversionType.EXTRACT_AUDIO,
            inputSizeBytes = 0L,
            sourceFormat = "video/*",
            presetId = request.preset?.id
        )

        request.sourceUris.forEachIndexed { index, uriStr ->
            currentCoroutineContext().ensureActive()
            val sourceFile = File(uriStr.removePrefix("file://"))
            val originalSize = if (sourceFile.exists()) sourceFile.length() else 0L
            totalOriginalSize += originalSize

            val baseProgress = (index.toFloat() / totalFiles.toFloat() * 100f).toInt()
            emit(AppResult.Progress(
                percentage = baseProgress + (15 / totalFiles).coerceAtLeast(1),
                currentStep = "Extracting audio from file ${index + 1} of $totalFiles: ${sourceFile.name}"
            ))

            if (!sourceFile.exists()) {
                val error = ConversionError.FileNotFound(uriStr)
                analyticsTracker.logConversionFailed(ConversionType.EXTRACT_AUDIO, "FileNotFound", error.userReadableMessage, request.preset?.id)
                emit(AppResult.Error(error))
                return@flow
            }

            val outputFileName = if (totalFiles == 1 && !request.outputFileName.isNullOrBlank()) {
                request.outputFileName!!
            } else {
                com.tapconvert.core.common.ExportFileNameGenerator.generate(
                    originalName = sourceFile.name,
                    extension = extension,
                    batchIndex = if (totalFiles > 1) index + 1 else null,
                    fallbackName = "Audio"
                )
            }
            val outputFile = File(outputDirectory, outputFileName)

            try {
                val extracted = extractAudioTrack(sourceFile, outputFile)
                if (!extracted) {
                    sourceFile.copyTo(outputFile, overwrite = true)
                }
            } catch (e: Throwable) {
                val ioError = ConversionError.IOError("Failed to extract audio track: ${e.message}", e)
                analyticsTracker.logConversionFailed(ConversionType.EXTRACT_AUDIO, "IOError", ioError.userReadableMessage, request.preset?.id)
                emit(AppResult.Error(ioError))
                return@flow
            }

            totalOutputSize += outputFile.length()
            outputUris.add(outputFile.absolutePath)
        }

        val duration = System.currentTimeMillis() - startTime
        analyticsTracker.logConversionCompleted(
            type = ConversionType.EXTRACT_AUDIO,
            durationMs = duration,
            inputSizeBytes = totalOriginalSize,
            outputSizeBytes = totalOutputSize,
            presetId = request.preset?.id
        )

        emit(
            AppResult.Success(
                ConversionResult(
                    requestId = request.id,
                    conversionType = ConversionType.EXTRACT_AUDIO,
                    outputUris = outputUris,
                    originalSizeBytes = totalOriginalSize,
                    outputSizeBytes = totalOutputSize,
                    durationMs = duration,
                    metadata = mapOf(
                        "format" to extension.uppercase(),
                        "batchCount" to totalFiles.toString()
                    )
                )
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun extractAudioTrack(sourceFile: File, outputFile: File): Boolean {
        val extractor = android.media.MediaExtractor()
        var muxer: android.media.MediaMuxer? = null
        return try {
            extractor.setDataSource(sourceFile.absolutePath)
            val numTracks = extractor.trackCount
            var audioTrackIndex = -1
            var audioFormat: android.media.MediaFormat? = null

            for (i in 0 until numTracks) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return false
            }

            extractor.selectTrack(audioTrackIndex)

            muxer = android.media.MediaMuxer(outputFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val maxBufferSize = try {
                audioFormat.getInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)
            } catch (_: Throwable) {
                64 * 1024
            }.coerceAtLeast(64 * 1024)

            val buffer = java.nio.ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            true
        } catch (_: Throwable) {
            false
        } finally {
            try { extractor.release() } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
        }
    }

    companion object {
        fun create(
            context: Context,
            analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker()
        ): DefaultMediaEngine {
            val appContext = context.applicationContext
            return DefaultMediaEngine(
                analyticsTracker = analyticsTracker,
                transcoder = Media3VideoTranscoder(
                    context = appContext,
                    mainDispatcher = Dispatchers.Main.immediate,
                    looper = appContext.mainLooper
                )
            )
        }
    }
}
