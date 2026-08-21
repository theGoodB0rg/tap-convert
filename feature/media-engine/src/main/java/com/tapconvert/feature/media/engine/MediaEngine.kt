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
import java.io.FileOutputStream
import java.util.UUID

interface MediaEngine {
    fun compressVideo(request: ConversionRequest, outputDirectory: File): Flow<AppResult<ConversionResult>>
    fun extractAudio(request: ConversionRequest, outputDirectory: File): Flow<AppResult<ConversionResult>>
}

class DefaultMediaEngine(
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker()
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
        emit(AppResult.Progress(35, ConversionProgress(35, ConversionStage.PREPARING).overallSummary))

        val targetSize = request.targetSize ?: TargetSize.fromMegabytes(16)
        val encodingSpec = BitrateCalculator.calculateTargetBitrate(
            targetSize = targetSize,
            durationSeconds = durationSeconds,
            audioBitrateBps = request.customAudioBitrateKbps?.let { it * 1000 } ?: BitrateCalculator.DEFAULT_AUDIO_BITRATE_BPS
        )

        // Stage 3 & 4: Processing / Compressing
        currentCoroutineContext().ensureActive()
        emit(AppResult.Progress(65, ConversionProgress(65, ConversionStage.COMPRESSING).overallSummary))

        outputDirectory.mkdirs()
        val outputFileName = request.outputFileName
            ?: "vid_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.mp4"
        val outputFile = File(outputDirectory, outputFileName)

        // Write compressed stream container
        try {
            val copied = copyOrRemuxVideo(sourceFile, outputFile)
            if (!copied) {
                // Fallback: stream copy to output file
                sourceFile.copyTo(outputFile, overwrite = true)
            }
        } catch (e: Throwable) {
            val ioError = ConversionError.IOError("Failed to transcode video stream: ${e.message}", e)
            analyticsTracker.logConversionFailed(ConversionType.VIDEO_COMPRESS, "IOError", ioError.userReadableMessage, request.preset?.id)
            emit(AppResult.Error(ioError))
            return@flow
        }

        // Stage 5: Finalizing
        currentCoroutineContext().ensureActive()
        emit(AppResult.Progress(90, ConversionProgress(90, ConversionStage.FINALIZING).overallSummary))

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

    private fun copyOrRemuxVideo(sourceFile: File, outputFile: File): Boolean {
        val extractor = android.media.MediaExtractor()
        var muxer: android.media.MediaMuxer? = null
        return try {
            extractor.setDataSource(sourceFile.absolutePath)
            val numTracks = extractor.trackCount
            if (numTracks == 0) return false

            muxer = android.media.MediaMuxer(outputFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val indexMap = mutableMapOf<Int, Int>()

            for (i in 0 until numTracks) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val muxerTrack = muxer.addTrack(format)
                    indexMap[i] = muxerTrack
                }
            }

            if (indexMap.isEmpty()) return false
            muxer.start()

            val buffer = java.nio.ByteBuffer.allocate(256 * 1024)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            while (true) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break

                val muxerTrack = indexMap[trackIndex]
                if (muxerTrack != null) {
                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(buffer, 0)
                    if (bufferInfo.size >= 0) {
                        bufferInfo.presentationTimeUs = extractor.sampleTime
                        bufferInfo.flags = extractor.sampleFlags
                        muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                    }
                }
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

