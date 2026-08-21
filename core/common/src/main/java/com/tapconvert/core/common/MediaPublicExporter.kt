package com.tapconvert.core.common

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.tapconvert.core.model.MimeType
import java.io.File

data class PublicExportResult(
    val contentUri: Uri?,
    val absolutePath: String,
    val destinationDisplay: String,
    val isSuccess: Boolean
)

object MediaPublicExporter {

    private const val APP_FOLDER_NAME = "TapConvert"

    fun exportFile(
        context: Context,
        sourceFile: File,
        mimeType: MimeType,
        customTreeUriString: String? = null
    ): PublicExportResult {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return PublicExportResult(
                contentUri = null,
                absolutePath = sourceFile.absolutePath,
                destinationDisplay = "Original file unavailable",
                isSuccess = false
            )
        }

        // Custom SAF Directory Export
        if (!customTreeUriString.isNullOrBlank()) {
            val customResult = exportToCustomTreeUri(context, sourceFile, mimeType, customTreeUriString)
            if (customResult.isSuccess) {
                return customResult
            }
        }

        // Default Public MediaStore Export
        return exportToMediaStore(context, sourceFile, mimeType)
    }

    private fun exportToCustomTreeUri(
        context: Context,
        sourceFile: File,
        mimeType: MimeType,
        treeUriString: String
    ): PublicExportResult {
        return try {
            val treeUri = Uri.parse(treeUriString)
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val targetUri = DocumentsContract.createDocument(
                context.contentResolver,
                docUri,
                mimeType.rawMimeType,
                sourceFile.name
            )

            if (targetUri != null) {
                context.contentResolver.openOutputStream(targetUri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                PublicExportResult(
                    contentUri = targetUri,
                    absolutePath = targetUri.toString(),
                    destinationDisplay = "Saved to chosen custom folder",
                    isSuccess = true
                )
            } else {
                PublicExportResult(null, sourceFile.absolutePath, "Failed to create in custom folder", false)
            }
        } catch (_: Throwable) {
            PublicExportResult(null, sourceFile.absolutePath, "Custom folder access error", false)
        }
    }

    private fun exportToMediaStore(
        context: Context,
        sourceFile: File,
        mimeType: MimeType
    ): PublicExportResult {
        val fileName = sourceFile.name
        val destinationFolder: String = when (mimeType) {
            is MimeType.Image -> "${Environment.DIRECTORY_PICTURES}/$APP_FOLDER_NAME"
            is MimeType.Video -> "${Environment.DIRECTORY_MOVIES}/$APP_FOLDER_NAME"
            is MimeType.Audio -> "${Environment.DIRECTORY_MUSIC}/$APP_FOLDER_NAME"
            is MimeType.Document, is MimeType.Other -> "${Environment.DIRECTORY_DOCUMENTS}/$APP_FOLDER_NAME"
        }

        val collectionUri: Uri = when (mimeType) {
            is MimeType.Image -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            }
            is MimeType.Video -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
            }
            is MimeType.Audio -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
            }
            is MimeType.Document, is MimeType.Other -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Files.getContentUri("external")
                }
            }
        }

        val destinationDisplay = "Saved to $destinationFolder"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType.rawMimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, destinationFolder)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val itemUri = context.contentResolver.insert(collectionUri, values)
                if (itemUri != null) {
                    context.contentResolver.openOutputStream(itemUri)?.use { out ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }

                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(itemUri, values, null, null)

                    return PublicExportResult(
                        contentUri = itemUri,
                        absolutePath = itemUri.toString(),
                        destinationDisplay = destinationDisplay,
                        isSuccess = true
                    )
                }
            } else {
                // Legacy storage (API < 29)
                val targetDir = File(Environment.getExternalStorageDirectory(), destinationFolder).apply { mkdirs() }
                val targetFile = File(targetDir, fileName)
                sourceFile.copyTo(targetFile, overwrite = true)

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(mimeType.rawMimeType),
                    null
                )

                return PublicExportResult(
                    contentUri = Uri.fromFile(targetFile),
                    absolutePath = targetFile.absolutePath,
                    destinationDisplay = destinationDisplay,
                    isSuccess = true
                )
            }
        } catch (_: Throwable) {
            // Fallback: Copy to public external files dir
            try {
                val publicDir = context.getExternalFilesDir(null) ?: context.filesDir
                val fallbackFolder = File(publicDir, APP_FOLDER_NAME).apply { mkdirs() }
                val fallbackFile = File(fallbackFolder, fileName)
                sourceFile.copyTo(fallbackFile, overwrite = true)

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(fallbackFile.absolutePath),
                    arrayOf(mimeType.rawMimeType),
                    null
                )

                return PublicExportResult(
                    contentUri = Uri.fromFile(fallbackFile),
                    absolutePath = fallbackFile.absolutePath,
                    destinationDisplay = "Saved to $APP_FOLDER_NAME folder",
                    isSuccess = true
                )
            } catch (_: Throwable) {
                return PublicExportResult(
                    contentUri = null,
                    absolutePath = sourceFile.absolutePath,
                    destinationDisplay = "Saved to app storage",
                    isSuccess = false
                )
            }
        }

        return PublicExportResult(
            contentUri = null,
            absolutePath = sourceFile.absolutePath,
            destinationDisplay = destinationDisplay,
            isSuccess = true
        )
    }

    fun getDestinationDisplayForMime(mimeType: MimeType, customPath: String? = null): String {
        if (!customPath.isNullOrBlank()) {
            return "Saved to $customPath"
        }
        return when (mimeType) {
            is MimeType.Image -> "Saved to Pictures/$APP_FOLDER_NAME"
            is MimeType.Video -> "Saved to Movies/$APP_FOLDER_NAME"
            is MimeType.Audio -> "Saved to Music/$APP_FOLDER_NAME"
            is MimeType.Document, is MimeType.Other -> "Saved to Documents/$APP_FOLDER_NAME"
        }
    }
}
