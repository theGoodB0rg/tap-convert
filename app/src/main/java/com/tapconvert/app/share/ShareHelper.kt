package com.tapconvert.app.share

import android.content.ClipData
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

    /**
     * Resolves the most specific compatible MIME type for a collection of MIME types.
     */
    fun resolveCommonMimeType(mimeTypes: List<String>): String {
        if (mimeTypes.isEmpty()) return "*/*"
        val distinct = mimeTypes.distinct()
        if (distinct.size == 1) return distinct.first()

        val primaryTypes = distinct.map { it.substringBefore('/') }.distinct()
        return if (primaryTypes.size == 1 && primaryTypes.first() != "*") {
            "${primaryTypes.first()}/*"
        } else {
            "*/*"
        }
    }

    const val VIRAL_SHARE_SUBJECT = "Converted with TapConvert"

    fun getViralShareBody(packageName: String): String {
        return "Converted with TapConvert — 100% Offline & Private\nhttps://play.google.com/store/apps/details?id=$packageName"
    }

    /**
     * Builds a single-file share intent (ACTION_SEND).
     * Attaches the file stream, viral subject, and viral text caption.
     */
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
                clipData = ClipData.newRawUri("Converted File", uri)
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

    /**
     * Builds a multi-file batch share intent (ACTION_SEND_MULTIPLE).
     *
     * Note: EXTRA_TEXT is strictly omitted for batch shares so third-party receivers (e.g. WhatsApp)
     * render clean media carousels without treating the text string as an invalid/corrupt media file.
     * EXTRA_SUBJECT is preserved for email and storage clients.
     */
    fun buildMultipleShareIntent(
        context: Context,
        filePathsOrUris: List<String>,
        explicitMimeType: String? = null
    ): Intent {
        val uriList = ArrayList<Uri>()
        val detectedMimes = mutableListOf<String>()

        for (path in filePathsOrUris) {
            val cleanPath = path.removePrefix("file://")
            val file = File(cleanPath)
            val uri: Uri? = if (cleanPath.startsWith("content://")) {
                try { Uri.parse(cleanPath) } catch (_: Throwable) { null }
            } else if (file.exists()) {
                detectedMimes.add(getMimeTypeForFile(file))
                getShareableUri(context, file)
            } else {
                try { Uri.parse(path) } catch (_: Throwable) { null }
            }
            if (uri != null) {
                uriList.add(uri)
            }
        }

        val resolvedMime = explicitMimeType ?: resolveCommonMimeType(detectedMimes)

        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = resolvedMime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
            putExtra(Intent.EXTRA_SUBJECT, VIRAL_SHARE_SUBJECT)
            if (uriList.isNotEmpty()) {
                val clip = ClipData.newRawUri("Converted Files", uriList.first())
                for (i in 1 until uriList.size) {
                    clip.addItem(ClipData.Item(uriList[i]))
                }
                clipData = clip
            }
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


