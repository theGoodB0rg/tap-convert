package com.tapconvert.feature.media.engine

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.ReturnCode
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.ConversionError
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Deterministic audio fallback for devices whose platform AAC encoder ignores
 * low bitrate requests. The command is built from quoted absolute paths only;
 * no shell is involved and user input never becomes an option.
 */
class FfmpegAudioTrackReencoder(
    private val context: Context
) : AudioTrackReencoder {
    @Volatile private var activeSessionId: Long? = null

    override fun transcodeAudio(
        sourceFile: File,
        outputFile: File,
        bitrateBps: Int
    ): Flow<AppResult<File>> = callbackFlow {
        outputFile.parentFile?.mkdirs()
        outputFile.delete()
        val input = quote(sourceFile.absolutePath)
        val output = quote(outputFile.absolutePath)
        val bitrate = bitrateBps.coerceIn(8_000, 192_000)
        val audioShape = if (bitrate <= 32_000) "-ac 1 -ar 8000" else "-ac 2 -ar 44100"
        // The staging name intentionally ends in .tmp, so specify the MP4
        // muxer explicitly instead of relying on filename extension probing.
        val command = "-y -hide_banner -loglevel error -i $input -map 0:a:0 -vn -c:a aac -b:a ${bitrate} $audioShape -avoid_negative_ts make_zero -f mp4 $output"
        val lastLog = AtomicReference("")
        val progressJob = launch {
            while (true) {
                delay(250)
                trySend(AppResult.Progress(50, "FFmpeg audio fallback..."))
            }
        }
        try {
            val session = FFmpegKit.executeAsync(
                command,
                { completed ->
                activeSessionId = null
                progressJob.cancel()
                if (ReturnCode.isSuccess(completed.returnCode) && outputFile.exists() && outputFile.length() > 0L) {
                    trySend(AppResult.Progress(100, "FFmpeg audio re-encoding complete"))
                    trySend(AppResult.Success(outputFile))
                    close()
                } else {
                    val message = completed.failStackTrace ?: lastLog.get().ifBlank { "FFmpeg audio fallback failed" }
                    trySend(AppResult.Error(ConversionError.IOError(message)))
                    close()
                }
                },
                LogCallback { log -> lastLog.set(log.message.trim()) },
                null
            )
            activeSessionId = session.sessionId
        } catch (error: Throwable) {
            progressJob.cancel()
            trySend(AppResult.Error(ConversionError.IOError("Unable to start FFmpeg audio fallback: ${error.message}", error)))
            close(error)
        }
        awaitClose {
            progressJob.cancel()
            activeSessionId?.let { id -> FFmpegKit.cancel(id) }
            activeSessionId = null
        }
    }

    private fun quote(path: String): String = "'${path.replace("'", "'\\''")}'"
}
