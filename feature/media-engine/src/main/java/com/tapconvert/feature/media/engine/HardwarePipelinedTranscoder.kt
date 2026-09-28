package com.tapconvert.feature.media.engine

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.tapconvert.core.analytics.AnalyticsTracker
import com.tapconvert.core.analytics.NoOpAnalyticsTracker
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.ConversionError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * High-performance hardware pipelined video transcoder.
 *
 * Utilizes:
 * 1. Unthrottled hardware codec operating rates and real-time execution priority.
 * 2. Surface-to-Surface hardware rendering without intermediate CPU copies.
 * 3. Adaptive non-blocking/micro-timeout pipeline scheduling to eliminate idle wait states.
 * 4. 16-pixel macroblock boundary alignment for optimal GPU/VPU DMA transfers.
 * 5. Hardware VBR encoding with standardized I-frame intervals to prevent bitrate overshoot.
 * 6. Automatic, seamless fallback to [fallbackTranscoder] if device hardware blocks Surface creation.
 */
class HardwarePipelinedTranscoder(
    private val fallbackTranscoder: VideoTranscoder = NativeVideoTranscoder(),
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker(),
    private val audioTrackReencoder: AudioTrackReencoder? = null,
    private val lowBitrateVideoFallback: VideoTranscoder? = null
) : VideoTranscoder {

    @Volatile
    private var isCancelled = false

    override fun transcode(
        sourceFile: File,
        outputFile: File,
        encodingSpec: BitrateCalculator.VideoEncodingSpec
    ): Flow<AppResult<File>> = flow {
        isCancelled = false
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            emit(AppResult.Error(ConversionError.FileNotFound("Source video file not found: ${sourceFile.absolutePath}")))
            return@flow
        }

        // Several device encoders have a hard floor well above the negotiated
        // bitrate. Use FFmpeg only for this constrained regime; normal videos
        // retain the fast hardware pipeline.
        if (encodingSpec.videoBitrateBps < 300_000 && lowBitrateVideoFallback != null) {
            analyticsTracker.logEvent(
                "video_codec_fallback_selected",
                mapOf(
                    "reason" to "low_bitrate_hardware_floor",
                    "requested_video_bitrate_bps" to encodingSpec.videoBitrateBps,
                    "requested_audio_bitrate_bps" to encodingSpec.audioBitrateBps
                )
            )
            lowBitrateVideoFallback.transcode(sourceFile, outputFile, encodingSpec).collect { emit(it) }
            return@flow
        }

        // The fast Surface pipeline has been observed to publish an AAC track
        // with zero samples on some emulator/device combinations. Route every
        // source with audio through the explicit audio re-encode/mux path so a
        // successful result can never silently lose its soundtrack. The video
        // leg remains hardware-first; constrained audio still selects FFmpeg.
        val sourceAudioBitrate = sourceAudioBitrate(sourceFile)
        if (sourceAudioBitrate > 0) {
            analyticsTracker.logEvent(
                "video_audio_reencode_required",
                mapOf(
                    "source_audio_bitrate_bps" to sourceAudioBitrate,
                    "requested_audio_bitrate_bps" to encodingSpec.audioBitrateBps,
                    "effective_target_bytes" to encodingSpec.effectiveTargetBytes
                )
            )
            if (audioTrackReencoder != null) {
                val videoOnlyFile = File(outputFile.parentFile, "${outputFile.name}.video.tmp")
                val audioOnlyFile = File(outputFile.parentFile, "${outputFile.name}.audio.tmp")
                videoOnlyFile.delete()
                audioOnlyFile.delete()
                var videoSucceeded = false
                var audioSucceeded = false
                var lastError: Throwable? = null
                videoOnlyTranscoder.transcode(
                    sourceFile,
                    videoOnlyFile,
                    encodingSpec.copy(audioBitrateBps = 0)
                ).collect { result ->
                    when (result) {
                        is AppResult.Progress -> emit(result)
                        is AppResult.Success -> videoSucceeded = true
                        is AppResult.Error -> lastError = result.throwable
                    }
                }
                if (videoSucceeded) {
                    audioTrackReencoder.transcodeAudio(
                        sourceFile,
                        audioOnlyFile,
                        encodingSpec.audioBitrateBps
                    ).collect { result ->
                        when (result) {
                            is AppResult.Progress -> emit(AppResult.Progress(60, result.currentStep))
                            is AppResult.Success -> audioSucceeded = true
                            is AppResult.Error -> lastError = result.throwable
                        }
                    }
                }
                if (videoSucceeded && audioSucceeded && muxVideoAndAudio(videoOnlyFile, audioOnlyFile, outputFile)) {
                    emit(AppResult.Progress(100, "Video and audio tracks combined"))
                    emit(AppResult.Success(outputFile))
                } else {
                    emit(
                        AppResult.Error(
                            lastError ?: ConversionError.IOError("Could not combine encoded video and audio tracks")
                        )
                    )
                }
                videoOnlyFile.delete()
                audioOnlyFile.delete()
            } else {
                fallbackTranscoder.transcode(sourceFile, outputFile, encodingSpec).collect { result ->
                    emit(result)
                }
            }
            return@flow
        }

        // The Android software AVC encoder raises low bitrates to a codec-specific
        // floor (observed on the emulator at ~392 kbps). Refuse impossible tight
        // budgets before spending many minutes encoding an artifact that cannot fit.
        val codecProfile = CodecCapabilityProber.probeAvcEncoder()
        analyticsTracker.logEvent(
            "video_encoder_capability",
            mapOf(
                "encoder" to codecProfile.encoderName,
                "hardware_accelerated" to codecProfile.isHardwareAccelerated,
                "supports_vbr" to codecProfile.supportsVbr,
                "max_width" to codecProfile.maxSupportedWidth,
                "max_height" to codecProfile.maxSupportedHeight
            )
        )
        val softwareAvc = codecProfile.encoderName.lowercase().let {
            it.contains("c2.android") || it.contains("omx.google")
        }
        if (softwareAvc && encodingSpec.videoBitrateBps < SOFTWARE_SAFE_MIN_VIDEO_BITRATE_BPS) {
            emit(
                AppResult.Error(
                    ConversionError.CodecBudgetUnachievable(
                        encoder = codecProfile.encoderName,
                        targetBytes = encodingSpec.effectiveTargetBytes
                    )
                )
            )
            return@flow
        }

        var hardwareSuccess = false
        var hardwareFailed = false

        try {
            runPipeline(sourceFile, outputFile, encodingSpec).collect { result ->
                when (result) {
                    is AppResult.Progress -> emit(result)
                    is AppResult.Success -> {
                        hardwareSuccess = true
                        emit(result)
                    }
                    is AppResult.Error -> {
                        if (result.throwable is ConversionError.Cancelled) {
                            emit(result)
                            return@collect
                        }
                        hardwareFailed = true
                    }
                }
            }
        } catch (_: Throwable) {
            hardwareFailed = true
        }

        if ((hardwareFailed || !hardwareSuccess) && !isCancelled) {
            fallbackTranscoder.transcode(sourceFile, outputFile, encodingSpec).collect { fallbackResult ->
                emit(fallbackResult)
            }
        }
    }

    private fun sourceAudioBitrate(sourceFile: File): Int {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(sourceFile.absolutePath)
            val audioFormat = (0 until extractor.trackCount)
                .map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            if (audioFormat == null) {
                0
            } else {
                // Some Android demuxers omit KEY_BIT_RATE for valid AAC tracks.
                // Treat that as an unknown real track, not as zero bitrate: zero
                // previously bypassed the re-encode path and silently dropped audio.
                runCatching { audioFormat.getInteger(MediaFormat.KEY_BIT_RATE) }
                    .getOrNull()
                    ?.takeIf { it > 0 }
                    ?: BitrateCalculator.DEFAULT_AUDIO_BITRATE_BPS
            }
        } catch (_: Throwable) {
            0
        } finally {
            runCatching { extractor.release() }
        }
    }

    private val videoOnlyTranscoder: VideoTranscoder = NativeVideoTranscoder()

    private fun muxVideoAndAudio(videoFile: File, audioFile: File, outputFile: File): Boolean {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)
            val videoTrack = (0 until videoExtractor.trackCount)
                .firstOrNull { videoExtractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                ?: return false
            val audioTrack = (0 until audioExtractor.trackCount)
                .firstOrNull { audioExtractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: run {
                    Log.e("TapConvertMedia", "audio reencode output has no audio track; tracks=${audioExtractor.trackCount}")
                    return false
                }
            Log.i(
                "TapConvertMedia",
                "mux tracks video=${videoExtractor.trackCount} audio=${audioExtractor.trackCount} " +
                    "audioMime=${audioExtractor.getTrackFormat(audioTrack).getString(MediaFormat.KEY_MIME)}"
            )
            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxVideoTrack = muxer.addTrack(videoExtractor.getTrackFormat(videoTrack))
            val muxAudioTrack = muxer.addTrack(audioExtractor.getTrackFormat(audioTrack))
            videoExtractor.selectTrack(videoTrack)
            audioExtractor.selectTrack(audioTrack)
            muxer.start()
            copyTracksInterleaved(
                videoExtractor,
                muxVideoTrack,
                audioExtractor,
                muxAudioTrack,
                muxer
            )
            muxer.stop()
            val verificationExtractor = MediaExtractor()
            runCatching {
                verificationExtractor.setDataSource(outputFile.absolutePath)
                Log.i(
                    "TapConvertMedia",
                    "mux verification tracks=${verificationExtractor.trackCount} mimes=" +
                        (0 until verificationExtractor.trackCount).joinToString(",") {
                            verificationExtractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty()
                        }
                )
            }
            runCatching { verificationExtractor.release() }
            true
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { muxer?.release() }
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
        }
    }

    private fun copyTracksInterleaved(
        videoExtractor: MediaExtractor,
        videoMuxerTrack: Int,
        audioExtractor: MediaExtractor,
        audioMuxerTrack: Int,
        muxer: MediaMuxer
    ) {
        val videoBuffer = ByteBuffer.allocate(4 * 1024 * 1024)
        val audioBuffer = ByteBuffer.allocate(512 * 1024)
        val videoInfo = MediaCodec.BufferInfo()
        val audioInfo = MediaCodec.BufferInfo()
        videoExtractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        audioExtractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        var videoSize = readSample(videoExtractor, videoBuffer, videoInfo)
        var audioSize = readSample(audioExtractor, audioBuffer, audioInfo)
        while (videoSize >= 0 || audioSize >= 0) {
            val writeVideo = audioSize < 0 || (videoSize >= 0 && videoInfo.presentationTimeUs <= audioInfo.presentationTimeUs)
            if (writeVideo && videoSize >= 0) {
                muxer.writeSampleData(videoMuxerTrack, videoBuffer, videoInfo)
                videoExtractor.advance()
                videoSize = readSample(videoExtractor, videoBuffer, videoInfo)
            } else if (audioSize >= 0) {
                muxer.writeSampleData(audioMuxerTrack, audioBuffer, audioInfo)
                audioExtractor.advance()
                audioSize = readSample(audioExtractor, audioBuffer, audioInfo)
            }
        }
    }

    private fun readSample(
        extractor: MediaExtractor,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo
    ): Int {
        buffer.clear()
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) return -1
        info.offset = 0
        info.size = size
        info.presentationTimeUs = extractor.sampleTime
        info.flags = if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            MediaCodec.BUFFER_FLAG_KEY_FRAME
        } else {
            0
        }
        return size
    }

    private fun runPipeline(
        sourceFile: File,
        outputFile: File,
        encodingSpec: BitrateCalculator.VideoEncodingSpec
    ): Flow<AppResult<File>> = flow {
        outputFile.parentFile?.mkdirs()
        emit(AppResult.Progress(5, "Initializing hardware pipeline..."))

        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null

        try {
            videoExtractor.setDataSource(sourceFile.absolutePath)
            audioExtractor.setDataSource(sourceFile.absolutePath)

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var sourceVideoFormat: MediaFormat? = null
            var sourceAudioFormat: MediaFormat? = null

            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && videoTrackIndex == -1) {
                    videoTrackIndex = i
                    sourceVideoFormat = format
                } else if (mime.startsWith("audio/") && audioTrackIndex == -1) {
                    audioTrackIndex = i
                    sourceAudioFormat = format
                }
            }

            if (videoTrackIndex == -1 || sourceVideoFormat == null) {
                emit(AppResult.Error(ConversionError.UnsupportedFormat("No valid video track found in ${sourceFile.name}")))
                return@flow
            }

            val sourceWidth = try { sourceVideoFormat.getInteger(MediaFormat.KEY_WIDTH) } catch (_: Throwable) { 1920 }
            val sourceHeight = try { sourceVideoFormat.getInteger(MediaFormat.KEY_HEIGHT) } catch (_: Throwable) { 1080 }
            val durationUs = try { sourceVideoFormat.getLong(MediaFormat.KEY_DURATION) } catch (_: Throwable) {
                try { sourceAudioFormat?.getLong(MediaFormat.KEY_DURATION) ?: 0L } catch (_: Throwable) { 0L }
            }.coerceAtLeast(1_000_000L)

            val rotation = try {
                if (sourceVideoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                    sourceVideoFormat.getInteger(MediaFormat.KEY_ROTATION)
                } else {
                    val mediaInfo = MediaMetadataRetrieverHelper.extractMediaInfo(sourceFile)
                    mediaInfo?.rotationDegrees ?: 0
                }
            } catch (_: Throwable) { 0 }

            val frameRate = try {
                sourceVideoFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
            } catch (_: Throwable) { 30 }.coerceIn(15, 60)

            // This pipeline remuxes audio rather than re-encoding it. Only retain
            // audio when its actual source bitrate fits the budget; otherwise the
            // copied track would make the size estimate false and retries useless.
            val sourceAudioBitrate = try {
                sourceAudioFormat?.getInteger(MediaFormat.KEY_BIT_RATE) ?: 0
            } catch (_: Throwable) { 0 }
            val copyAudio = audioTrackIndex != -1 && sourceAudioFormat != null &&
                sourceAudioBitrate > 0 && sourceAudioBitrate <= encodingSpec.audioBitrateBps

            // Calculate macroblock-aligned target dimensions
            val rawTargetW = if (encodingSpec.targetWidth > 0) encodingSpec.targetWidth else computeTargetDimensions(sourceWidth, sourceHeight, encodingSpec.recommendedMaxDimension).first
            val rawTargetH = if (encodingSpec.targetHeight > 0) encodingSpec.targetHeight else computeTargetDimensions(sourceWidth, sourceHeight, encodingSpec.recommendedMaxDimension).second

            val targetWidth = TranscoderConfigurator.alignToMacroblock(rawTargetW)
            val targetHeight = TranscoderConfigurator.alignToMacroblock(rawTargetH)

            val outputVideoMime = MediaFormat.MIMETYPE_VIDEO_AVC
            val targetFormat = TranscoderConfigurator.createEncoderFormat(
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                bitrateBps = encodingSpec.videoBitrateBps,
                frameRate = frameRate,
                iFrameIntervalSeconds = TranscoderConfigurator.DEFAULT_I_FRAME_INTERVAL_SECONDS,
                outputMime = outputVideoMime
            )

            // Some OEM hardware encoders impose a much higher floor than the
            // requested bitrate. For an ultra-tight budget, prefer the
            // platform software AVC encoder when available so the byte budget
            // remains achievable instead of publishing an inflated artifact.
            val softwareEncoderName = if (encodingSpec.videoBitrateBps < 100_000) {
                CodecCapabilityProber.findSoftwareAvcEncoder()
            } else {
                null
            }
            encoder = softwareEncoderName?.let { MediaCodec.createByCodecName(it) }
                ?: MediaCodec.createEncoderByType(outputVideoMime)
            encoder.configure(targetFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val encoderInputSurface = encoder.createInputSurface()
            encoder.start()

            val sourceMime = sourceVideoFormat.getString(MediaFormat.KEY_MIME) ?: outputVideoMime
            val optimizedDecoderFormat = TranscoderConfigurator.configureDecoderFormat(sourceVideoFormat)
            decoder = MediaCodec.createDecoderByType(sourceMime)
            decoder.configure(optimizedDecoderFormat, encoderInputSurface, null, 0)
            decoder.start()

            videoExtractor.selectTrack(videoTrackIndex)
            if (audioTrackIndex != -1) {
                audioExtractor.selectTrack(audioTrackIndex)
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (rotation > 0) {
                muxer.setOrientationHint(rotation)
            }

            var muxerStarted = false
            var muxerVideoTrack = -1
            var muxerAudioTrack = -1

            var decoderDone = false
            var encoderDone = false
            var audioDone = false
            val bufferInfo = MediaCodec.BufferInfo()
            val audioBuffer = ByteBuffer.allocate(256 * 1024)
            val audioBufferInfo = MediaCodec.BufferInfo()

            // Micro-timeouts: 0us when busy, 250us when idle waiting
            val kActiveTimeoutUs = 0L
            val kIdleTimeoutUs = 250L

            var lastReportedProgress = 5
            var lastProgressTimeMs = 0L
            val minOutputFrameIntervalUs = if (encodingSpec.videoBitrateBps < 100_000) 1_000_000L else 0L
            var lastRenderedPresentationUs = Long.MIN_VALUE

            while (!encoderDone && !isCancelled && currentCoroutineContext().isActive) {
                var hadActivity = false

                // 1. Feed input from extractor into decoder
                if (!decoderDone) {
                    val inputBufIndex = decoder.dequeueInputBuffer(if (hadActivity) kActiveTimeoutUs else kIdleTimeoutUs)
                    if (inputBufIndex >= 0) {
                        hadActivity = true
                        val inputBuffer = decoder.getInputBuffer(inputBufIndex)
                        if (inputBuffer != null) {
                            val sampleSize = videoExtractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                decoderDone = true
                            } else {
                                val presentationTimeUs = videoExtractor.sampleTime
                                decoder.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, 0)
                                videoExtractor.advance()
                            }
                        }
                    }
                }

                // 2. Dequeue decoded frames and render onto encoder's input surface
                var decoderOutputAvailable = true
                while (decoderOutputAvailable && !isCancelled) {
                    val decoderStatus = decoder.dequeueOutputBuffer(bufferInfo, kActiveTimeoutUs)
                    when {
                        decoderStatus >= 0 -> {
                            hadActivity = true
                            val shouldRender = bufferInfo.size > 0 &&
                                (minOutputFrameIntervalUs == 0L ||
                                    lastRenderedPresentationUs == Long.MIN_VALUE ||
                                    bufferInfo.presentationTimeUs - lastRenderedPresentationUs >= minOutputFrameIntervalUs)
                            if (shouldRender) {
                                lastRenderedPresentationUs = bufferInfo.presentationTimeUs
                                decoder.releaseOutputBuffer(decoderStatus, bufferInfo.presentationTimeUs * 1000L)
                            } else {
                                decoder.releaseOutputBuffer(decoderStatus, false)
                            }
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                encoder.signalEndOfInputStream()
                                decoderOutputAvailable = false
                            }
                        }
                        decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            decoderOutputAvailable = false
                        }
                        else -> {
                            // INFO_OUTPUT_FORMAT_CHANGED or buffers changed
                        }
                    }
                }

                // 3. Dequeue encoded packets from encoder and write to muxer
                var encoderOutputAvailable = true
                while (encoderOutputAvailable && !isCancelled) {
                    val encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, kActiveTimeoutUs)
                    when {
                        encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            hadActivity = true
                            if (muxerStarted) {
                                throw IllegalStateException("Encoder output format changed after muxer started")
                            }
                            val newFormat = encoder.outputFormat
                            muxerVideoTrack = muxer.addTrack(newFormat)

                            if (copyAudio) {
                                muxerAudioTrack = muxer.addTrack(sourceAudioFormat)
                            }
                            muxer.start()
                            muxerStarted = true
                        }
                        encoderStatus >= 0 -> {
                            hadActivity = true
                            val encodedBuffer = encoder.getOutputBuffer(encoderStatus)
                            if (encodedBuffer != null) {
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    bufferInfo.size = 0
                                }

                                if (bufferInfo.size > 0 && muxerStarted) {
                                    encodedBuffer.position(bufferInfo.offset)
                                    encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer.writeSampleData(muxerVideoTrack, encodedBuffer, bufferInfo)

                                    // Interleave audio samples up to current video PTS
                                    if (copyAudio && muxerAudioTrack != -1 && !audioDone) {
                                        while (true) {
                                            val audioSampleTime = audioExtractor.sampleTime
                                            if (audioSampleTime < 0 || audioSampleTime > bufferInfo.presentationTimeUs) {
                                                if (audioSampleTime < 0) audioDone = true
                                                break
                                            }
                                            audioBufferInfo.offset = 0
                                            val sampleSize = audioExtractor.readSampleData(audioBuffer, 0)
                                            if (sampleSize < 0) {
                                                audioDone = true
                                                break
                                            }
                                            audioBufferInfo.size = sampleSize
                                            audioBufferInfo.presentationTimeUs = audioSampleTime
                                            audioBufferInfo.flags = if ((audioExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                                                MediaCodec.BUFFER_FLAG_KEY_FRAME
                                            } else {
                                                0
                                            }

                                            audioBuffer.position(0)
                                            audioBuffer.limit(sampleSize)
                                            muxer.writeSampleData(muxerAudioTrack, audioBuffer, audioBufferInfo)
                                            audioExtractor.advance()
                                        }
                                    }

                                    // Throttled progress reporting
                                    val now = System.currentTimeMillis()
                                    val progressPct = ((bufferInfo.presentationTimeUs.toFloat() / durationUs.toFloat()) * 85f).toInt() + 10
                                    val clamped = progressPct.coerceIn(10, 95)
                                    if (clamped > lastReportedProgress && (clamped - lastReportedProgress >= 2 || now - lastProgressTimeMs >= 150)) {
                                        lastReportedProgress = clamped
                                        lastProgressTimeMs = now
                                        emit(AppResult.Progress(clamped, "Transcoding video ($clamped%)..."))
                                    }
                                }
                            }

                            encoder.releaseOutputBuffer(encoderStatus, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                encoderDone = true
                                encoderOutputAvailable = false
                            }
                        }
                        encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            encoderOutputAvailable = false
                        }
                    }
                }

                if (!hadActivity) {
                    try {
                        Thread.sleep(1)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }

            if (isCancelled) {
                emit(AppResult.Error(ConversionError.Cancelled))
                return@flow
            }

            // 4. Mux any remaining trailing audio track samples to completion
            if (copyAudio && muxerStarted && muxerAudioTrack != -1 && !audioDone) {
                emit(AppResult.Progress(96, "Finalizing audio stream..."))
                while (true) {
                    audioBufferInfo.offset = 0
                    val sampleSize = audioExtractor.readSampleData(audioBuffer, 0)
                    if (sampleSize < 0) break

                    audioBufferInfo.size = sampleSize
                    audioBufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    audioBufferInfo.flags = if ((audioExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }

                    audioBuffer.position(0)
                    audioBuffer.limit(sampleSize)
                    muxer.writeSampleData(muxerAudioTrack, audioBuffer, audioBufferInfo)
                    audioExtractor.advance()
                }
            }

            emit(AppResult.Progress(100, "Transcoding complete"))
            emit(AppResult.Success(outputFile))

        } catch (e: Throwable) {
            if (e is CancellationException) {
                emit(AppResult.Error(ConversionError.Cancelled))
            } else {
                emit(AppResult.Error(ConversionError.IOError("Hardware pipelined transcoding failed: ${e.message}", e)))
            }
        } finally {
            try { videoExtractor.release() } catch (_: Throwable) {}
            try { audioExtractor.release() } catch (_: Throwable) {}
            try {
                decoder?.stop()
                decoder?.release()
            } catch (_: Throwable) {}
            try {
                encoder?.stop()
                encoder?.release()
            } catch (_: Throwable) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Throwable) {}
        }
    }.flowOn(Dispatchers.IO)

    override fun cancel() {
        isCancelled = true
        fallbackTranscoder.cancel()
    }

    private fun computeTargetDimensions(
        sourceWidth: Int,
        sourceHeight: Int,
        maxAllowedDimension: Int
    ): Pair<Int, Int> {
        val safeW = if (sourceWidth > 0) sourceWidth else 1920
        val safeH = if (sourceHeight > 0) sourceHeight else 1080

        val maxSourceDim = maxOf(safeW, safeH)
        val targetMaxDim = if (maxAllowedDimension in 144..maxSourceDim) maxAllowedDimension else maxSourceDim

        val scale = if (maxSourceDim > targetMaxDim) {
            targetMaxDim.toFloat() / maxSourceDim.toFloat()
        } else {
            1.0f
        }

        val outW = ((safeW * scale).roundToInt() / 2) * 2
        val outH = ((safeH * scale).roundToInt() / 2) * 2

        return Pair(outW.coerceAtLeast(144), outH.coerceAtLeast(144))
    }

    companion object {
        const val SOFTWARE_SAFE_MIN_VIDEO_BITRATE_BPS = 400_000
    }
}
