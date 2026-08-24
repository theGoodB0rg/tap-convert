package com.tapconvert.core.common.intake

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tapconvert.core.model.MimeType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class StagedMediaItem(
    val uri: String, // file:// path
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long
)

sealed interface IntakeResult {
    data class Success(val items: List<StagedMediaItem>, val totalSizeBytes: Long) : IntakeResult
    data class Empty(val reason: String = "No valid files selected") : IntakeResult
    data class Error(val message: String, val cause: Throwable? = null) : IntakeResult
}

interface MediaIntakeManager {
    suspend fun stageUris(
        context: Context,
        uris: List<Uri>,
        stagingDirectory: File
    ): IntakeResult
}

class DefaultMediaIntakeManager(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MediaIntakeManager {

    companion object {
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB high-efficiency buffer
    }

    override suspend fun stageUris(
        context: Context,
        uris: List<Uri>,
        stagingDirectory: File
    ): IntakeResult = withContext(ioDispatcher) {
        if (uris.isEmpty()) {
            return@withContext IntakeResult.Empty("No URIs provided")
        }

        try {
            if (!stagingDirectory.exists()) {
                stagingDirectory.mkdirs()
            }
        } catch (e: Throwable) {
            return@withContext IntakeResult.Error("Failed to create staging directory: ${e.message}", e)
        }

        val stagedItems = mutableListOf<StagedMediaItem>()
        var totalSize = 0L

        for (uri in uris) {
            try {
                val fileName = resolveFileName(context.contentResolver, uri)
                val safeFileName = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}_$fileName"
                val destinationFile = File(stagingDirectory, safeFileName)

                val copied = if (uri.scheme == "file" || uri.scheme == null) {
                    val srcFile = File(uri.path ?: uri.toString())
                    if (srcFile.exists()) {
                        srcFile.copyTo(destinationFile, overwrite = true)
                        true
                    } else {
                        false
                    }
                } else {
                    copyFromContentUri(context.contentResolver, uri, destinationFile)
                }

                if (copied && destinationFile.exists() && destinationFile.length() > 0L) {
                    val size = destinationFile.length()
                    val detectedMime = MimeType.fromFileName(fileName).rawMimeType
                    stagedItems.add(
                        StagedMediaItem(
                            uri = "file://${destinationFile.absolutePath}",
                            originalName = fileName,
                            mimeType = detectedMime,
                            sizeBytes = size
                        )
                    )
                    totalSize += size
                }
            } catch (_: Throwable) {
                // Gracefully ignore individual file intake failures to ensure partial batch availability
            }
        }

        if (stagedItems.isEmpty()) {
            IntakeResult.Empty("Failed to stage any of the selected files")
        } else {
            IntakeResult.Success(items = stagedItems, totalSizeBytes = totalSize)
        }
    }

    private fun copyFromContentUri(
        contentResolver: ContentResolver,
        uri: Uri,
        destinationFile: File
    ): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { rawIn ->
                BufferedInputStream(rawIn, BUFFER_SIZE).use { bufIn ->
                    BufferedOutputStream(FileOutputStream(destinationFile), BUFFER_SIZE).use { bufOut ->
                        bufIn.copyTo(bufOut, BUFFER_SIZE)
                    }
                }
            }
            destinationFile.exists() && destinationFile.length() > 0L
        } catch (_: Throwable) {
            if (destinationFile.exists()) destinationFile.delete()
            false
        }
    }

    private fun resolveFileName(contentResolver: ContentResolver, uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/')
        }

        return if (!name.isNullOrBlank()) name!! else "media_${UUID.randomUUID().toString().take(6)}"
    }
}
