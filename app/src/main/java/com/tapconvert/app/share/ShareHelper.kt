package com.tapconvert.app.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

object ShareHelper {

    fun getShareableUri(context: Context, file: File): Uri? {
        return try {
            val authority = "${context.packageName}.fileprovider"
            FileProvider.getUriForFile(context, authority, file)
        } catch (_: Throwable) {
            try {
                Uri.fromFile(file)
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun getMimeTypeForFile(file: File): String {
        val extension = file.extension.lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "pdf" -> "application/pdf"
            else -> {
                try {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
                } catch (_: Throwable) {
                    "*/*"
                }
            }
        }
    }

    const val VIRAL_SHARE_SUBJECT = "Converted with TapConvert"

    fun getViralShareBody(packageName: String): String {
        return "Converted with TapConvert — 100% Offline & Private\nhttps://play.google.com/store/apps/details?id=$packageName"
    }

    fun buildShareIntent(
        context: Context,
        filePathOrUri: String,
        explicitMimeType: String? = null
    ): Intent {
        val cleanPath = filePathOrUri.removePrefix("file://")
        val file = File(cleanPath)

        val uri: Uri? = if (cleanPath.startsWith("content://")) {
            try { Uri.parse(cleanPath) } catch (_: Throwable) { null }
        } else if (file.exists()) {
            getShareableUri(context, file)
        } else {
            try { Uri.parse(filePathOrUri) } catch (_: Throwable) { null }
        }

        val mimeType = explicitMimeType ?: if (file.exists()) {
            getMimeTypeForFile(file)
        } else {
            "*/*"
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            if (uri != null) {
                putExtra(Intent.EXTRA_STREAM, uri)
            }
            putExtra(Intent.EXTRA_SUBJECT, VIRAL_SHARE_SUBJECT)
            putExtra(Intent.EXTRA_TEXT, getViralShareBody(context.packageName))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createShareChooserIntent(
        context: Context,
        filePathOrUri: String,
        explicitMimeType: String? = null,
        title: String = "Share Converted File"
    ): Intent {
        val shareIntent = buildShareIntent(context, filePathOrUri, explicitMimeType)
        val chooser = Intent.createChooser(shareIntent, title)
        return chooser ?: shareIntent.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildMultipleShareIntent(
        context: Context,
        filePathsOrUris: List<String>,
        explicitMimeType: String? = null
    ): Intent {
        val uriList = ArrayList<Uri>()
        var resolvedMime: String = explicitMimeType ?: "*/*"

        for (path in filePathsOrUris) {
            val cleanPath = path.removePrefix("file://")
            val file = File(cleanPath)
            val uri: Uri? = if (cleanPath.startsWith("content://")) {
                try { Uri.parse(cleanPath) } catch (_: Throwable) { null }
            } else if (file.exists()) {
                if (resolvedMime == "*/*") {
                    resolvedMime = getMimeTypeForFile(file)
                }
                getShareableUri(context, file)
            } else {
                try { Uri.parse(path) } catch (_: Throwable) { null }
            }
            if (uri != null) {
                uriList.add(uri)
            }
        }

        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = resolvedMime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
            putExtra(Intent.EXTRA_SUBJECT, VIRAL_SHARE_SUBJECT)
            putExtra(Intent.EXTRA_TEXT, getViralShareBody(context.packageName))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createMultipleShareChooserIntent(
        context: Context,
        filePathsOrUris: List<String>,
        explicitMimeType: String? = null,
        title: String = "Share Converted Files"
    ): Intent {
        val shareIntent = buildMultipleShareIntent(context, filePathsOrUris, explicitMimeType)
        val chooser = Intent.createChooser(shareIntent, title)
        return chooser ?: shareIntent.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

