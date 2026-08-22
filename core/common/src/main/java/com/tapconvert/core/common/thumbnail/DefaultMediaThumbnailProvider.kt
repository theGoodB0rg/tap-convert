package com.tapconvert.core.common.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import com.tapconvert.core.model.MediaCategory
import com.tapconvert.core.model.MimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Production implementation of MediaThumbnailProvider with strategy extraction per media category
 * and high-performance in-memory LRU caching.
 */
class DefaultMediaThumbnailProvider(
    private val maxCacheSizeBytes: Long = (Runtime.getRuntime().maxMemory() / 8).coerceAtLeast(4 * 1024 * 1024L)
) : MediaThumbnailProvider {

    private val cacheLock = Any()
    private var currentByteCount: Long = 0L
    private val memoryCache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            val shouldRemove = currentByteCount > maxCacheSizeBytes
            if (shouldRemove && eldest?.value != null) {
                currentByteCount -= try { eldest.value.byteCount.toLong() } catch (_: Throwable) { 0L }
            }
            return shouldRemove
        }
    }

    override suspend fun loadThumbnail(
        context: Context,
        uriOrPath: String,
        targetSizePx: Int
    ): ThumbnailResult = withContext(Dispatchers.IO) {
        if (uriOrPath.isBlank()) {
            return@withContext ThumbnailResult.Error(IllegalArgumentException("Empty URI or path"))
        }

        val cacheKey = "${uriOrPath}_$targetSizePx"
        synchronized(cacheLock) {
            val cached = memoryCache[cacheKey]
            if (cached != null) {
                val isRecycled = try { cached.isRecycled } catch (_: Throwable) { false }
                if (!isRecycled) {
                    return@withContext ThumbnailResult.Loaded(cached)
                } else {
                    memoryCache.remove(cacheKey)
                }
            }
        }

        val cleanPath = uriOrPath.removePrefix("file://")
        val fileName = if (cleanPath.startsWith("content://")) {
            resolveFileNameFromUri(context, Uri.parse(cleanPath)) ?: cleanPath
        } else {
            File(cleanPath).name
        }

        val mimeType = MimeType.fromFileName(fileName)
        val category = mimeType.category

        val result = when (category) {
            MediaCategory.IMAGE -> extractImageThumbnail(context, cleanPath, targetSizePx)
            MediaCategory.VIDEO -> extractVideoThumbnail(context, cleanPath, targetSizePx)
            MediaCategory.DOCUMENT -> extractDocumentThumbnail(context, cleanPath, targetSizePx)
            MediaCategory.AUDIO -> extractAudioThumbnail(context, cleanPath, targetSizePx)
        }

        if (result is ThumbnailResult.Loaded) {
            synchronized(cacheLock) {
                val size = try { result.bitmap.byteCount.toLong() } catch (_: Throwable) { 0L }
                currentByteCount += size
                val prev = memoryCache.put(cacheKey, result.bitmap)
                if (prev != null) {
                    currentByteCount -= try { prev.byteCount.toLong() } catch (_: Throwable) { 0L }
                }
            }
        }

        result
    }

    override fun clearCache() {
        synchronized(cacheLock) {
            memoryCache.clear()
            currentByteCount = 0L
        }
    }

    private fun extractImageThumbnail(
        context: Context,
        cleanPath: String,
        targetSizePx: Int
    ): ThumbnailResult {
        return try {
            val file = File(cleanPath)
            val bitmap = if (file.exists() && file.length() > 0) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                options.inSampleSize = calculateInSampleSize(options, targetSizePx, targetSizePx)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565
                BitmapFactory.decodeFile(file.absolutePath, options)
            } else if (cleanPath.startsWith("content://")) {
                val uri = Uri.parse(cleanPath)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
                options.inSampleSize = calculateInSampleSize(options, targetSizePx, targetSizePx)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
            } else {
                null
            }

            if (bitmap != null) {
                ThumbnailResult.Loaded(bitmap)
            } else {
                ThumbnailResult.FallbackIcon(MediaCategory.IMAGE)
            }
        } catch (t: Throwable) {
            ThumbnailResult.FallbackIcon(MediaCategory.IMAGE)
        }
    }

    private fun extractVideoThumbnail(
        context: Context,
        cleanPath: String,
        targetSizePx: Int
    ): ThumbnailResult {
        val retriever = MediaMetadataRetriever()
        return try {
            if (cleanPath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(cleanPath))
            } else {
                val file = File(cleanPath)
                if (!file.exists()) return ThumbnailResult.FallbackIcon(MediaCategory.VIDEO)
                retriever.setDataSource(file.absolutePath)
            }

            val rawFrame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    1_000_000L, // 1 second into video
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetSizePx,
                    targetSizePx
                ) ?: retriever.frameAtTime
            } else {
                retriever.frameAtTime
            }

            if (rawFrame != null) {
                val scaled = scaleBitmapDownIfNeeded(rawFrame, targetSizePx)
                ThumbnailResult.Loaded(scaled)
            } else {
                ThumbnailResult.FallbackIcon(MediaCategory.VIDEO)
            }
        } catch (_: Throwable) {
            ThumbnailResult.FallbackIcon(MediaCategory.VIDEO)
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    private fun extractDocumentThumbnail(
        context: Context,
        cleanPath: String,
        targetSizePx: Int
    ): ThumbnailResult {
        return try {
            val file = File(cleanPath)
            if (file.exists() && file.extension.equals("pdf", ignoreCase = true)) {
                var pfd: ParcelFileDescriptor? = null
                var renderer: PdfRenderer? = null
                try {
                    pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    renderer = PdfRenderer(pfd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val maxDim = maxOf(page.width, page.height).coerceAtLeast(1)
                        val scale = targetSizePx.toFloat() / maxDim.toFloat()
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)

                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        ThumbnailResult.Loaded(bitmap)
                    } else {
                        ThumbnailResult.FallbackIcon(MediaCategory.DOCUMENT)
                    }
                } finally {
                    try { renderer?.close() } catch (_: Throwable) {}
                    try { pfd?.close() } catch (_: Throwable) {}
                }
            } else {
                ThumbnailResult.FallbackIcon(MediaCategory.DOCUMENT)
            }
        } catch (_: Throwable) {
            ThumbnailResult.FallbackIcon(MediaCategory.DOCUMENT)
        }
    }

    private fun extractAudioThumbnail(
        context: Context,
        cleanPath: String,
        targetSizePx: Int
    ): ThumbnailResult {
        val retriever = MediaMetadataRetriever()
        return try {
            if (cleanPath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(cleanPath))
            } else {
                val file = File(cleanPath)
                if (!file.exists()) return ThumbnailResult.FallbackIcon(MediaCategory.AUDIO)
                retriever.setDataSource(file.absolutePath)
            }

            val picture = retriever.embeddedPicture
            if (picture != null && picture.isNotEmpty()) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(picture, 0, picture.size, options)
                options.inSampleSize = calculateInSampleSize(options, targetSizePx, targetSizePx)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565

                val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size, options)
                if (bitmap != null) {
                    ThumbnailResult.Loaded(bitmap)
                } else {
                    ThumbnailResult.FallbackIcon(MediaCategory.AUDIO)
                }
            } else {
                ThumbnailResult.FallbackIcon(MediaCategory.AUDIO)
            }
        } catch (_: Throwable) {
            ThumbnailResult.FallbackIcon(MediaCategory.AUDIO)
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun scaleBitmapDownIfNeeded(bitmap: Bitmap, targetSizePx: Int): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= targetSizePx * 1.5f) return bitmap

        val scale = targetSizePx.toFloat() / maxDim.toFloat()
        val destW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val destH = (bitmap.height * scale).toInt().coerceAtLeast(1)

        return try {
            Bitmap.createScaledBitmap(bitmap, destW, destH, true)
        } catch (_: Throwable) {
            bitmap
        }
    }

    private fun resolveFileNameFromUri(context: Context, uri: Uri): String? {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) it.getString(nameIdx) else null
                } else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        val defaultInstance: DefaultMediaThumbnailProvider by lazy {
            DefaultMediaThumbnailProvider()
        }
    }
}
