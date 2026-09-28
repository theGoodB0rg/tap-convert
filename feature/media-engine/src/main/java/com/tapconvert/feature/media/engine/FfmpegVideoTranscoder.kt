package com.tapconvert.feature.media.engine

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.StatisticsCallback
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.ConversionError
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Deterministic software fallback for very low video bitrates where a device
 * MediaCodec encoder ignores the requested bitrate and inflates the result.
 * The normal path remains hardware-first for throughput.
 */
class FfmpegVideoTranscoder : VideoTranscoder {
    @Volatile private var activeSessionId: Long? = null

    override fun transcode(
        sourceFile: File,
        outputFile: File,
        encodingSpec: BitrateCalculator.VideoEncodingSpec
    ): Flow<AppResult<File>> = callbackFlow {
        outputFile.parentFile?.mkdirs()
        outputFile.delete()
        val input = quote(sourceFile.absolutePath)
        val output = quote(outputFile.absolutePath)
        val sourceInfo = MediaMetadataRetrieverHelper.extractMediaInfo(sourceFile)
        val durationMs = sourceInfo?.durationMs ?: 0L
        val audioShape = if (encodingSpec.audioBitrateBps <= 32_000) "-ac 1 -ar 8000" else "-ac 2 -ar 44100"
        val frameRate = if (encodingSpec.videoBitrateBps < 300_000) "-r 8" else "-r 15"
        val scale = "scale=${encodingSpec.targetWidth}:${encodingSpec.targetHeight}:force_original_aspect_ratio=decrease,pad=${encodingSpec.targetWidth}:${encodingSpec.targetHeight}:(ow-iw)/2:(oh-ih)/2"
        val command = buildString {
            append("-y -hide_banner -loglevel error -i $input ")
            append("-map 0:v:0")
            if (sourceInfo?.hasAudio == true) append(" -map 0:a:0")
            append(" -vf \"$scale\" $frameRate -c:v libopenh264")
            append(" -b:v ${encodingSpec.videoBitrateBps} -maxrate ${encodingSpec.videoBitrateBps} -bufsize ${encodingSpec.videoBitrateBps * 2}")
            if (sourceInfo?.hasAudio == true) {
                append(" -c:a aac -b:a ${encodingSpec.audioBitrateBps} $audioShape")
            }
            append(" -movflags +faststart -avoid_negative_ts make_zero -f mp4 $output")
        }
        val lastLog = AtomicReference("")
        val lastReportedProgress = AtomicInteger(5)
        val progressJob = launch {
            while (true) {
                delay(300)
                // Statistics are the authoritative progress source. The heartbeat
                // only keeps the UI alive when FFmpeg has not emitted a sample yet.
                trySend(AppResult.Progress(lastReportedProgress.get(), "FFmpeg video fallback..."))
            }
        }
        try {
            val session = FFmpegKit.executeAsync(
                command,
                { completed ->
                    activeSessionId = null
                    progressJob.cancel()
                    if (ReturnCode.isSuccess(completed.returnCode) && outputFile.exists() && outputFile.length() > 0L) {
                        trySend(AppResult.Progress(100, "FFmpeg video fallback complete"))
                        trySend(AppResult.Success(outputFile))
                        close()
                    } else {
                        val message = completed.failStackTrace ?: lastLog.get().ifBlank { "FFmpeg video fallback failed" }
                        trySend(AppResult.Error(ConversionError.IOError(message)))
                        close()
                    }
                },
                LogCallback { log -> lastLog.set(log.message.trim()) },
                StatisticsCallback { statistics ->
                    if (durationMs > 0L) {
                        val ratio = (statistics.time / durationMs.toDouble()).coerceIn(0.0, 1.0)
                        val progress = (5 + ratio * 90.0).toInt().coerceIn(5, 95)
                        lastReportedProgress.updateAndGet { previous -> maxOf(previous, progress) }
                    }
                }
            )
            activeSessionId = session.sessionId
        } catch (error: Throwable) {
            progressJob.cancel()
            trySend(AppResult.Error(ConversionError.IOError("Unable to start FFmpeg video fallback: ${error.message}", error)))
            close(error)
        }
        awaitClose {
            progressJob.cancel()
            activeSessionId?.let { FFmpegKit.cancel(it) }
            activeSessionId = null
        }
    }

    private fun quote(path: String): String = "'${path.replace("'", "'\\''")}'"
}
