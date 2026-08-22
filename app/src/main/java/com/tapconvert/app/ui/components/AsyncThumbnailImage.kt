package com.tapconvert.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tapconvert.core.common.thumbnail.DefaultMediaThumbnailProvider
import com.tapconvert.core.common.thumbnail.MediaThumbnailProvider
import com.tapconvert.core.common.thumbnail.ThumbnailResult
import com.tapconvert.core.model.MediaCategory

@Composable
fun AsyncThumbnailImage(
    uriOrPath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    targetSizePx: Int = 160,
    contentScale: ContentScale = ContentScale.Crop,
    thumbnailProvider: MediaThumbnailProvider = DefaultMediaThumbnailProvider.defaultInstance
) {
    val context = LocalContext.current
    var bitmap by remember(uriOrPath) { mutableStateOf<Bitmap?>(null) }
    var fallbackCategory by remember(uriOrPath) { mutableStateOf<MediaCategory?>(null) }
    var isLoading by remember(uriOrPath) { mutableStateOf(true) }
    var isError by remember(uriOrPath) { mutableStateOf(false) }

    LaunchedEffect(uriOrPath) {
        isLoading = true
        isError = false
        fallbackCategory = null
        bitmap = null

        when (val result = thumbnailProvider.loadThumbnail(context, uriOrPath, targetSizePx)) {
            is ThumbnailResult.Loaded -> {
                bitmap = result.bitmap
                isLoading = false
            }
            is ThumbnailResult.FallbackIcon -> {
                fallbackCategory = result.category
                isLoading = false
            }
            is ThumbnailResult.Error -> {
                isLoading = false
                isError = true
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
            isLoading -> {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            fallbackCategory != null -> {
                val icon = when (fallbackCategory) {
                    MediaCategory.VIDEO -> Icons.Default.Videocam
                    MediaCategory.DOCUMENT -> Icons.Default.PictureAsPdf
                    MediaCategory.AUDIO -> Icons.Default.Audiotrack
                    MediaCategory.IMAGE, null -> Icons.Default.Image
                }
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(24.dp)
                )
            }
            isError -> {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

