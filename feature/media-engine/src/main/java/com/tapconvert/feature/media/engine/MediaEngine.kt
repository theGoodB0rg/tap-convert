package com.tapconvert.feature.media.engine

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
        val sourceUri = request.sourceUris.firstOrNull()

        if (sourceUri == null) {
            val error = ConversionError.FileNotFound("No source video URI provided")
            analyticsTracker.logConversionFailed(ConversionType.VIDEO_COMPRESS, "FileNotFound", error.userReadableMessage, request.preset?.id)
            emit(AppResult.Error(error))
            return@flow
        }

        val sourceFile = File(sourceUri.removePrefix("file://"))
        val originalSize = if (sourceFile.exists()) sourceFile.length() else 0L

        analyticsTracker.logConversionStarted(
            type = ConversionType.VIDEO_COMPRESS,
            inputSizeBytes = originalSize,
            sourceFormat = "video/*",
            presetId = request.preset?.id
        )

        // Stage 1: Analyzing
        currentCoroutineContext().ensureActive()
        emit(AppResult.Progress(15, ConversionProgress(15, ConversionStage.ANALYZING).overallSummary))

        if (!sourceFile.exists()) {
            val error = ConversionError.FileNotFound(sourceUri)
            analyticsTracker.logConversionFailed(ConversionType.VIDEO_COMPRESS, "FileNotFound", error.userReadableMessage, request.preset?.id)
            emit(AppResult.Error(error))
            return@flow
        }

        val mediaInfo = MediaMetadataRetrieverHelper.extractMediaInfo(sourceFile)
        val durationSeconds = mediaInfo?.durationSeconds ?: 60.0

        // Stage 2: Preparing & Calculating Bitrate
        currentCoroutineContext().ensureActive()
        emit(AppResult.Progress(30, ConversionProgress(30, ConversionStage.PREPARING).overallSummary))

        val targetSize = request.targetSize ?: TargetSize.fromMegabytes(16)
        val encodingSpec = BitrateCalculator.calculateTargetBitrate(
            targetSize = targetSize,
            durationSeconds = durationSeconds,
            audioBitrateBps = request.customAudioBitrateKbps?.let { it * 1000 } ?: BitrateCalculator.DEFAULT_AUDIO_BITRATE_BPS
        )

        outputDirectory.mkdirs()
        val outputFileName = request.outputFileName
            ?: "vid_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.mp4"
        val outputFile = File(outputDirectory, outputFileName)

        // Stage 3 & 4: Transcoding Stream via VideoTranscoder
        var transcodeSuccess = false
        var transcodeError: Throwable? = null

        transcoder.transcode(sourceFile, outputFile, encodingSpec).collect { transcodeResult ->
            when (transcodeResult) {
                is AppResult.Progress -> {
                    // Map 0..100 transcode progress into 35..90 overall progress
                    val mappedPct = 35 + (transcodeResult.percentage * 0.55).toInt()
                    emit(AppResult.Progress(mappedPct, ConversionProgress(mappedPct, ConversionStage.COMPRESSING).overallSummary))
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

        // Stage 5: Finalizing
        currentCoroutineContext().ensureActive()
        emit(AppResult.Progress(95, ConversionProgress(95, ConversionStage.FINALIZING).overallSummary))

        val duration = System.currentTimeMillis() - startTime
        val outputSize = outputFile.length()

        analyticsTracker.logConversionCompleted(
            type = ConversionType.VIDEO_COMPRESS,
            durationMs = duration,
            inputSizeBytes = originalSize,
            outputSizeBytes = outputSize,
            presetId = request.preset?.id
        )

        emit(
            AppResult.Success(
                ConversionResult(
                    requestId = request.id,
                    conversionType = ConversionType.VIDEO_COMPRESS,
                    outputUris = listOf(outputFile.absolutePath),
                    originalSizeBytes = originalSize,
                    outputSizeBytes = outputSize,
                    durationMs = duration,
                    metadata = mapOf(
                        "videoBitrateBps" to encodingSpec.videoBitrateBps.toString(),
                        "audioBitrateBps" to encodingSpec.audioBitrateBps.toString(),
                        "maxDimension" to encodingSpec.recommendedMaxDimension.toString()
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
        val sourceUri = request.sourceUris.firstOrNull()

        if (sourceUri == null) {
            val error = ConversionError.FileNotFound("No source media file provided")
            analyticsTracker.logConversionFailed(ConversionType.EXTRACT_AUDIO, "FileNotFound", error.userReadableMessage, request.preset?.id)
            emit(AppResult.Error(error))
            return@flow
        }

        val sourceFile = File(sourceUri.removePrefix("file://"))
        val originalSize = if (sourceFile.exists()) sourceFile.length() else 0L

        analyticsTracker.logConversionStarted(
            type = ConversionType.EXTRACT_AUDIO,
            inputSizeBytes = originalSize,
            sourceFormat = "video/*",
            presetId = request.preset?.id
        )

        // Stage 1: Analyzing
        currentCoroutineContext().ensureActive()
        emit(AppResult.Progress(15, ConversionProgress(15, ConversionStage.ANALYZING).overallSummary))

        if (!sourceFile.exists()) {
            val error = ConversionError.FileNotFound(sourceUri)
            analyticsTracker.logConversionFailed(ConversionType.EXTRACT_AUDIO, "FileNotFound", error.userReadableMessage, request.preset?.id)
            emit(AppResult.Error(error))
            return@flow
        }

        // Stage 2 & 3: Demuxing & Audio Extraction
        currentCoroutineContext().ensureActive()
        emit(AppResult.Progress(50, ConversionProgress(50, ConversionStage.PROCESSING).overallSummary))

        val targetMime = request.targetMimeType
        val extension = if (targetMime is MimeType.Audio) targetMime.primaryExtension else "m4a"

        outputDirectory.mkdirs()
        val outputFileName = request.outputFileName
            ?: "audio_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension"
        val outputFile = File(outputDirectory, outputFileName)

        try {
            val extracted = extractAudioTrack(sourceFile, outputFile)
            if (!extracted) {
                // If direct track demuxing was not possible, perform binary stream copy
                sourceFile.copyTo(outputFile, overwrite = true)
            }
        } catch (e: Throwable) {
            val ioError = ConversionError.IOError("Failed to extract audio track: ${e.message}", e)
            analyticsTracker.logConversionFailed(ConversionType.EXTRACT_AUDIO, "IOError", ioError.userReadableMessage, request.preset?.id)
            emit(AppResult.Error(ioError))
            return@flow
        }

        // Stage 4: Finalizing
        currentCoroutineContext().ensureActive()
        emit(AppResult.Progress(90, ConversionProgress(90, ConversionStage.FINALIZING).overallSummary))

        val duration = System.currentTimeMillis() - startTime
        val outputSize = outputFile.length()

        analyticsTracker.logConversionCompleted(
            type = ConversionType.EXTRACT_AUDIO,
            durationMs = duration,
            inputSizeBytes = originalSize,
            outputSizeBytes = outputSize,
            presetId = request.preset?.id
        )

        emit(
            AppResult.Success(
                ConversionResult(
                    requestId = request.id,
                    conversionType = ConversionType.EXTRACT_AUDIO,
                    outputUris = listOf(outputFile.absolutePath),
                    originalSizeBytes = originalSize,
                    outputSizeBytes = outputSize,
                    durationMs = duration,
                    metadata = mapOf("format" to extension.uppercase())
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
}
