package com.tapconvert.feature.media.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.os.Build
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

data class MediaIntegrityCheck(
    val outputInfo: MediaMetadataRetrieverHelper.MediaInfo?,
    val failureReason: String?
)

fun interface MediaIntegrityChecker {
    fun check(
        sourceInfo: MediaMetadataRetrieverHelper.MediaInfo?,
        outputFile: File
    ): MediaIntegrityCheck
}

private val strictMediaIntegrityChecker = MediaIntegrityChecker { sourceInfo, outputFile ->
    val outputInfo = MediaMetadataRetrieverHelper.extractMediaInfo(outputFile)
    val sourceDurationMs = sourceInfo?.durationMs ?: 0L
    val outputDurationMs = outputInfo?.durationMs ?: 0L
    val durationIsValid = sourceDurationMs <= 0L ||
        (outputDurationMs >= (sourceDurationMs * 0.98).toLong() &&
            outputDurationMs <= (sourceDurationMs * 1.02).toLong())
    val audioIsValid = sourceInfo?.hasAudio != true || outputInfo?.hasAudio == true
    val reason = when {
        outputInfo == null -> "output could not be parsed"
        !audioIsValid -> "source audio track was not preserved"
        !durationIsValid -> "duration changed from ${sourceDurationMs}ms to ${outputDurationMs}ms"
        else -> null
    }
    MediaIntegrityCheck(outputInfo, reason)
}

class DefaultMediaEngine(
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker(),
    private val transcoder: VideoTranscoder = Media3VideoTranscoder(),
    private val integrityChecker: MediaIntegrityChecker = strictMediaIntegrityChecker
) : MediaEngine {

    override fun compressVideo(
        request: ConversionRequest,
        outputDirectory: File
    ): Flow<AppResult<ConversionResult>> = flow {
        val startTime = System.currentTimeMillis()
        var lastReportedProgress = 0
        suspend fun emitMonotonicProgress(percentage: Int, step: String) {
            val monotonic = percentage.coerceIn(lastReportedProgress, 100)
            lastReportedProgress = monotonic
            emit(AppResult.Progress(monotonic, step))
        }
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
        var totalHardOutputLimit = 0L
        var totalOvershootRetries = 0
        outputDirectory.mkdirs()

        // Capture the immutable input-size baseline before encoding begins so
        // diagnostics can correlate failures and throughput with real payloads.
        val requestedInputSizeBytes = request.sourceUris.sumOf { uriStr ->
            File(uriStr.removePrefix("file://")).takeIf { it.exists() }?.length() ?: 0L
        }

        analyticsTracker.logConversionStarted(
            type = ConversionType.VIDEO_COMPRESS,
            inputSizeBytes = requestedInputSizeBytes,
            sourceFormat = "video/*",
            presetId = request.preset?.id
        )

        request.sourceUris.forEachIndexed { index, uriStr ->
            currentCoroutineContext().ensureActive()
            val sourceFile = File(uriStr.removePrefix("file://"))
            val originalSize = if (sourceFile.exists()) sourceFile.length() else 0L
            totalOriginalSize += originalSize

            val itemStart = (index.toFloat() / totalFiles.toFloat() * 100f)
            val itemSpan = 100f / totalFiles.toFloat()
            fun itemProgress(phasePercent: Float): Int =
                (itemStart + itemSpan * phasePercent / 100f).toInt()

            emitMonotonicProgress(
                itemProgress(10f),
                ConversionProgress(itemProgress(10f), ConversionStage.ANALYZING).overallSummary
            )

            if (!sourceFile.exists()) {
                val error = ConversionError.FileNotFound(uriStr)
                analyticsTracker.logConversionFailed(ConversionType.VIDEO_COMPRESS, "FileNotFound", error.userReadableMessage, request.preset?.id)
                emit(AppResult.Error(error))
                return@flow
            }

            val mediaInfo = MediaMetadataRetrieverHelper.extractMediaInfo(sourceFile)
            val durationSeconds = mediaInfo?.durationSeconds ?: 60.0
            val sourceWidth = mediaInfo?.width ?: 1920
            val sourceHeight = mediaInfo?.height ?: 1080

            emitMonotonicProgress(
                itemProgress(25f),
                ConversionProgress(itemProgress(25f), ConversionStage.PREPARING).overallSummary
            )

            var encodingSpec = BitrateCalculator.calculateTargetBitrate(
                targetSize = request.targetSize,
                durationSeconds = durationSeconds,
                sourceSizeBytes = originalSize,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                quality = request.quality,
                audioBitrateBps = request.customAudioBitrateKbps?.let { it * 1000 } ?: BitrateCalculator.DEFAULT_AUDIO_BITRATE_BPS
            )
            // Compression must never publish an artifact larger than either the
            // requested budget or the input. This is the invariant the UI promises.
            // The requested target is an estimate, not a byte-for-byte promise.
            // Video upload services commonly allow a small fixed slack; keep the
            // same absolute tolerance in the UI estimate, encoder budget and
            // final publication check while never exceeding the source.
            val hardOutputLimit = minOf(
                request.targetSize?.maxAllowedBytes ?: encodingSpec.effectiveTargetBytes,
                encodingSpec.effectiveTargetBytes,
                originalSize
            )
            val minimumPlayableBytes = BitrateCalculator.minimumPlayableBytes(durationSeconds)
            val compressionRatio = if (originalSize > 0L) {
                encodingSpec.effectiveTargetBytes.toDouble() / originalSize.toDouble()
            } else 1.0
            if (compressionRatio < 0.10) {
                analyticsTracker.logEvent(
                    "video_compression_warning",
                    mapOf(
                        "warning" to "aggressive_compression_ratio",
                        "input_size_bytes" to originalSize,
                        "estimated_output_bytes" to encodingSpec.effectiveTargetBytes,
                        "estimated_ratio" to compressionRatio,
                        "last_resort" to true
                    )
                )
            }
            if (hardOutputLimit < minimumPlayableBytes) {
                val warning = "Target may be unrealistic for ${durationSeconds.toInt()}s while preserving audio"
                analyticsTracker.logEvent(
                    "video_compression_warning",
                    mapOf(
                        "warning" to "unrealistic_media_budget",
                        "input_size_bytes" to originalSize,
                        "hard_output_limit_bytes" to hardOutputLimit,
                        "conservative_minimum_playable_bytes" to minimumPlayableBytes,
                        "duration_seconds" to durationSeconds,
                        "audio_preservation_required" to (mediaInfo?.hasAudio == true),
                        "last_resort" to true
                    )
                )
            }
            totalHardOutputLimit += hardOutputLimit

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
            val tempFile = File(outputDirectory, "${outputFileName}.tmp")

            var transcodeSuccess = false
            var transcodeError: Throwable? = null
            var attempt = 1
            // A single compensation pass handles normal VBR jitter. Severe
            // overshoot is treated as a codec incompatibility and fails fast;
            // repeating a full encode cannot make a codec-imposed floor fit.
            val maxAttempts = 2

            while (attempt <= maxAttempts && !transcodeSuccess) {
                if (tempFile.exists()) tempFile.delete()

                transcoder.transcode(sourceFile, tempFile, encodingSpec).collect { transcodeResult ->
                    when (transcodeResult) {
                        is AppResult.Progress -> {
                            val mappedPct = itemProgress(35f + transcodeResult.percentage * 0.55f)
                            val stepText = ConversionProgress(mappedPct, ConversionStage.COMPRESSING).overallSummary
                            emitMonotonicProgress(mappedPct, stepText)
                        }
                        is AppResult.Success -> {
                            transcodeSuccess = true
                        }
                        is AppResult.Error -> {
                            transcodeError = transcodeResult.throwable
                        }
                    }
                }

                if (transcodeSuccess && tempFile.exists() && tempFile.length() > 0L) {
                    val actualBytes = tempFile.length()
                    val targetBudget = hardOutputLimit

                    // Closed-loop verification: the published output must never exceed the
                    // effective target budget. A codec may overshoot the requested bitrate,
                    // so compensate until the hard byte contract is met or fail safely.
                    val overshootRatio = if (targetBudget > 0L) {
                        actualBytes.toDouble() / targetBudget.toDouble()
                    } else Double.POSITIVE_INFINITY
                    if (actualBytes > targetBudget &&
                        overshootRatio < 2.0 &&
                        attempt < maxAttempts &&
                        originalSize > 0L
                    ) {
                        val overshootFactor = targetBudget.toDouble() / actualBytes.toDouble()
                        val reduction = (overshootFactor * 0.88).coerceIn(0.45, 0.82)
                        encodingSpec = BitrateCalculator.createCompensatedSpec(encodingSpec, reduction)
                        transcodeSuccess = false
                        totalOvershootRetries++
                        attempt++
                    } else {
                        break
                    }
                } else {
                    break
                }
            }

            if (!transcodeSuccess || !tempFile.exists() || tempFile.length() == 0L) {
                if (tempFile.exists()) tempFile.delete()

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

            if (tempFile.length() > hardOutputLimit) {
                val actualOversizeBytes = tempFile.length()
                tempFile.delete()
                val targetError = ConversionError.OutputBudgetExceeded(
                    actualBytes = actualOversizeBytes,
                    limitBytes = hardOutputLimit
                )
                analyticsTracker.logEvent(
                    "video_output_budget_exceeded",
                    mapOf(
                        "input_size_bytes" to originalSize,
                        "output_size_bytes" to actualOversizeBytes,
                        "hard_output_limit_bytes" to hardOutputLimit
                    )
                )
                analyticsTracker.logConversionFailed(
                    type = ConversionType.VIDEO_COMPRESS,
                    errorType = "OutputBudgetExceeded",
                    errorMessage = targetError.userReadableMessage,
                    presetId = request.preset?.id
                )
                emit(AppResult.Error(targetError))
                return@flow
            }

            // Never publish a playable-looking file that silently lost its
            // soundtrack or was truncated during transcoding.
            val integrityCheck = integrityChecker.check(mediaInfo, tempFile)
            val outputMediaInfo = integrityCheck.outputInfo
            val sourceDurationMs = mediaInfo?.durationMs ?: 0L
            val outputDurationMs = outputMediaInfo?.durationMs ?: 0L
            val sourceHasAudio = mediaInfo?.hasAudio == true
            if (integrityCheck.failureReason != null) {
                val reason = integrityCheck.failureReason
                tempFile.delete()
                val integrityError = ConversionError.MediaIntegrityFailure(reason)
                analyticsTracker.logEvent(
                    "video_media_integrity_failure",
                    mapOf(
                        "reason" to reason,
                        "input_duration_ms" to sourceDurationMs,
                        "output_duration_ms" to outputDurationMs,
                        "input_has_audio" to sourceHasAudio,
                        "output_has_audio" to (outputMediaInfo?.hasAudio ?: false)
                    )
                )
                analyticsTracker.logConversionFailed(
                    type = ConversionType.VIDEO_COMPRESS,
                    errorType = "MediaIntegrityFailure",
                    errorMessage = integrityError.userReadableMessage,
                    presetId = request.preset?.id
                )
                emit(AppResult.Error(integrityError))
                return@flow
            }

            // Atomic rename from staging .tmp to final output
            if (outputFile.exists()) outputFile.delete()
            val renamed = tempFile.renameTo(outputFile)
            if (!renamed) {
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
            }

            totalOutputSize += outputFile.length()
            outputUris.add(outputFile.absolutePath)
        }

        emitMonotonicProgress(95, ConversionProgress(95, ConversionStage.FINALIZING).overallSummary)

        val duration = System.currentTimeMillis() - startTime
        analyticsTracker.logConversionCompleted(
            type = ConversionType.VIDEO_COMPRESS,
            durationMs = duration,
            inputSizeBytes = totalOriginalSize,
            outputSizeBytes = totalOutputSize,
            presetId = request.preset?.id
        )
        analyticsTracker.logEvent(
            "video_compression_contract",
            mapOf(
                "input_size_bytes" to totalOriginalSize,
                "output_size_bytes" to totalOutputSize,
                "hard_output_limit_bytes" to totalHardOutputLimit,
                "budget_compliant" to (totalOutputSize <= totalHardOutputLimit),
                "source_size_compliant" to (totalOutputSize <= totalOriginalSize),
                "overshoot_retries" to totalOvershootRetries,
                "duration_ms" to duration,
                "input_throughput_kbps" to if (duration > 0L) {
                    (totalOriginalSize * 1000L) / (duration * 1024L)
                } else 0L,
                "output_throughput_kbps" to if (duration > 0L) {
                    (totalOutputSize * 1000L) / (duration * 1024L)
                } else 0L
            )
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
                        "videoBitrateBps" to "see per-file encoder trace",
                        "audioBitrateBps" to BitrateCalculator.DEFAULT_AUDIO_BITRATE_BPS.toString(),
                        "estimatedOutputSizeBytes" to totalHardOutputLimit.toString(),
                        "maxOutputSizeBytes" to totalHardOutputLimit.toString(),
                        "targetMaxDimension" to "1280",
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
                bufferInfo.flags = if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
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
            val ffmpegAvailable = FfmpegSupport.isSupported()
            analyticsTracker.logEvent(
                "ffmpeg_capability",
                mapOf(
                    "available" to ffmpegAvailable,
                    "abi" to Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
                )
            )
            val normalAudio = Media3AudioTrackReencoder(appContext)
            return DefaultMediaEngine(
                analyticsTracker = analyticsTracker,
                transcoder = HardwarePipelinedTranscoder(
                    fallbackTranscoder = Media3VideoTranscoder(
                        context = appContext,
                        mainDispatcher = Dispatchers.Main.immediate,
                        looper = appContext.mainLooper,
                        fallbackTranscoder = NativeVideoTranscoder()
                    ),
                    analyticsTracker = analyticsTracker,
                    // FFmpeg is used for the constrained audio leg because this
                    // device family demonstrably floors MediaCodec AAC near
                    // 64 kbps, which can inflate long videos instead of meeting
                    // the negotiated byte budget. Video remains hardware-first.
                    audioTrackReencoder = HybridAudioTrackReencoder(
                        normal = normalAudio,
                        constrained = if (ffmpegAvailable) {
                            FfmpegAudioTrackReencoder(appContext)
                        } else {
                            normalAudio
                        }
                    ),
                    lowBitrateVideoFallback = if (ffmpegAvailable) FfmpegVideoTranscoder() else null
                )
            )
        }
    }
}
