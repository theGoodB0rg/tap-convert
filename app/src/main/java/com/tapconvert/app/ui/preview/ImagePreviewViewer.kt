package com.tapconvert.app.ui.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tapconvert.app.ui.theme.SavingsGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ImagePreviewViewer(
    imageFile: File,
    originalFile: File? = null,
    modifier: Modifier = Modifier
) {
    var isShowingOriginal by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var convertedBitmap by remember(imageFile.absolutePath) { mutableStateOf<Bitmap?>(null) }
    var originalBitmap by remember(originalFile?.absolutePath) { mutableStateOf<Bitmap?>(null) }
    var imageDimensions by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Load Bitmaps asynchronously with safe bounds decoding
    LaunchedEffect(imageFile.absolutePath, originalFile?.absolutePath) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                if (imageFile.exists() && imageFile.length() > 0) {
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(imageFile.absolutePath, boundsOpts)
                    imageDimensions = boundsOpts.outWidth to boundsOpts.outHeight

                    val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, 2048, 2048)
                    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    convertedBitmap = BitmapFactory.decodeFile(imageFile.absolutePath, opts)
                }

                if (originalFile != null && originalFile.exists() && originalFile.length() > 0) {
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(originalFile.absolutePath, boundsOpts)
                    val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, 2048, 2048)
                    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    originalBitmap = BitmapFactory.decodeFile(originalFile.absolutePath, opts)
                }
            } catch (_: Throwable) {
            } finally {
                isLoading = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            convertedBitmap?.let { if (!it.isRecycled) it.recycle() }
            originalBitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    val activeBitmap = if (isShowingOriginal && originalBitmap != null) originalBitmap else convertedBitmap

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Zoomable & Pannable Viewport
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = newScale
                        offset = if (newScale > 1f) {
                            Offset(offset.x + pan.x, offset.y + pan.y)
                        } else {
                            Offset.Zero
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.2f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (activeBitmap != null && !activeBitmap.isRecycled) {
                Image(
                    bitmap = activeBitmap.asImageBitmap(),
                    contentDescription = "Image preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            } else if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            } else {
                Text(
                    text = "Unable to load image",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        // Top Header: Dimensions & Zoom Factor
        Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                imageDimensions?.let { (w, h) ->
                    Text(
                        text = "${w} × ${h} px",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (scale > 1.05f) {
                    Text(
                        text = "• ${String.format("%.1fx", scale)}",
                        color = SavingsGreen,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Bottom Inspection Toggle: Compare Original vs Optimized
        if (originalBitmap != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !isShowingOriginal,
                        onClick = { isShowingOriginal = false },
                        label = {
                            Text(
                                text = "Optimized (${formatBytes(imageFile.length())})",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SavingsGreen,
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = isShowingOriginal,
                        onClick = { isShowingOriginal = true },
                        label = {
                            Text(
                                text = "Original (${formatBytes(originalFile?.length() ?: 0L)})",
                                fontWeight = FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            selectedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize.coerceAtLeast(1)
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%.0f KB", kb)
}
