package com.tapconvert.app.share

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.tapconvert.core.model.MediaCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ShareIntentParser {

    private const val BUFFER_SIZE = 64 * 1024

    suspend fun parseAsync(
        intent: Intent?,
        contentResolver: ContentResolver?,
        cacheDirectory: File,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): SharePayload? = withContext(ioDispatcher) {
        parse(intent, contentResolver, cacheDirectory)
    }

    /**
     * Parses an incoming ACTION_SEND or ACTION_SEND_MULTIPLE intent into a normalized SharePayload.
     * Safely copies content URIs to working files in cacheDirectory if necessary.
     */
    fun parse(
        intent: Intent?,
        contentResolver: ContentResolver?,
        cacheDirectory: File
    ): SharePayload? {
        if (intent == null) return null

        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) {
            return null
        }

        val rawUris = extractUris(intent)
        if (rawUris.isEmpty()) {
            return null
        }

        val mimeType = detectMimeType(intent, rawUris)
        val category = determineCategory(mimeType, rawUris)

        val workingUris = mutableListOf<String>()
        val fileNames = mutableListOf<String>()
        var totalSize = 0L

        val stagingDir = File(cacheDirectory, "share_staging").apply { mkdirs() }

        for (uri in rawUris) {
            val fileName = resolveFileName(uri, contentResolver)
            val fileSize = resolveFileSize(uri, contentResolver)

            val workingFile = if (uri.scheme == "file" || uri.scheme == null) {
                val path = uri.path ?: uri.toString()
                val f = File(path)
                if (f.exists()) f else copyToStaging(uri, contentResolver, stagingDir, fileName)
            } else {
                copyToStaging(uri, contentResolver, stagingDir, fileName)
            }

            if (workingFile != null && workingFile.exists()) {
                workingUris.add("file://${workingFile.absolutePath}")
                fileNames.add(fileName)
                totalSize += if (fileSize > 0) fileSize else workingFile.length()
            }
        }

        if (workingUris.isEmpty()) {
            return null
        }

        return SharePayload(
            sourceUris = workingUris,
            fileNames = fileNames,
            mimeType = mimeType,
            category = category,
            totalSizeBytes = totalSize,
            isMultiple = workingUris.size > 1
        )
    }

    private fun extractUris(intent: Intent): List<Uri> {
        val uris = mutableListOf<Uri>()

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }

                if (stream != null) {
                    uris.add(stream)
                } else if (intent.data != null) {
                    uris.add(intent.data!!)
                } else if (intent.clipData != null && intent.clipData!!.itemCount > 0) {
                    for (i in 0 until intent.clipData!!.itemCount) {
                        intent.clipData!!.getItemAt(i).uri?.let { uris.add(it) }
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }

                if (!list.isNullOrEmpty()) {
                    uris.addAll(list)
                } else if (intent.clipData != null && intent.clipData!!.itemCount > 0) {
                    for (i in 0 until intent.clipData!!.itemCount) {
                        intent.clipData!!.getItemAt(i).uri?.let { uris.add(it) }
                    }
                }
            }
        }

        return uris
    }

    fun detectMimeType(intent: Intent?, uris: List<Uri>): String {
        val intentType = intent?.type
        if (!intentType.isNullOrBlank() && intentType != "*/*") {
            return intentType
        }

        val firstUri = uris.firstOrNull() ?: return "*/*"
        val extension = (firstUri.lastPathSegment ?: firstUri.path ?: "").substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "pdf" -> "application/pdf"
            else -> intentType ?: "*/*"
        }
    }

    fun determineCategory(mimeType: String, uris: List<Uri> = emptyList()): MediaCategory {
        val lowerMime = mimeType.lowercase()
        return when {
            lowerMime.startsWith("image/") -> MediaCategory.IMAGE
            lowerMime.startsWith("video/") -> MediaCategory.VIDEO
            lowerMime.startsWith("audio/") -> MediaCategory.AUDIO
            lowerMime == "application/pdf" || uris.any { (it.lastPathSegment ?: it.path ?: "").endsWith(".pdf", ignoreCase = true) } -> MediaCategory.DOCUMENT
            else -> {
                val firstUri = uris.firstOrNull()
                val pathSegment = (firstUri?.lastPathSegment ?: firstUri?.path ?: "").lowercase()
                when {
                    pathSegment.endsWith(".png") || pathSegment.endsWith(".jpg") || pathSegment.endsWith(".jpeg") || pathSegment.endsWith(".webp") || pathSegment.endsWith(".heic") -> MediaCategory.IMAGE
                    pathSegment.endsWith(".mp4") || pathSegment.endsWith(".mkv") || pathSegment.endsWith(".mov") || pathSegment.endsWith(".webm") -> MediaCategory.VIDEO
                    pathSegment.endsWith(".mp3") || pathSegment.endsWith(".aac") || pathSegment.endsWith(".m4a") || pathSegment.endsWith(".wav") -> MediaCategory.AUDIO
                    pathSegment.endsWith(".pdf") -> MediaCategory.DOCUMENT
                    else -> MediaCategory.IMAGE
                }
            }
        }
    }

    fun resolveFileName(uri: Uri, contentResolver: ContentResolver?): String {
        var name: String? = null
        if (uri.scheme == "content" && contentResolver != null) {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (_: Throwable) {
                // Ignore and fall back
            }
        }

        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/')
        }

        return if (!name.isNullOrBlank()) name!! else "shared_file_${UUID.randomUUID().toString().take(6)}"
    }

    fun resolveFileSize(uri: Uri, contentResolver: ContentResolver?): Long {
        if (uri.scheme == "content" && contentResolver != null) {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1) {
                            return cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (_: Throwable) {
                // Fall back
            }
        } else if (uri.scheme == "file" || uri.scheme == null) {
            val path = uri.path ?: uri.toString()
            val file = File(path)
            if (file.exists()) return file.length()
        }
        return 0L
    }

    private fun copyToStaging(
        uri: Uri,
        contentResolver: ContentResolver?,
        stagingDir: File,
        fileName: String
    ): File? {
        val safeFileName = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}_$fileName"
        val destinationFile = File(stagingDir, safeFileName)

        return try {
            if (contentResolver != null) {
                contentResolver.openInputStream(uri)?.use { inStream ->
                    BufferedInputStream(inStream, BUFFER_SIZE).use { bufIn ->
                        BufferedOutputStream(FileOutputStream(destinationFile), BUFFER_SIZE).use { bufOut ->
                            bufIn.copyTo(bufOut, BUFFER_SIZE)
                        }
                    }
                }
                destinationFile
            } else {
                val sourceFile = File(uri.path ?: uri.toString())
                if (sourceFile.exists()) {
                    sourceFile.copyTo(destinationFile, overwrite = true)
                    destinationFile
                } else null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
