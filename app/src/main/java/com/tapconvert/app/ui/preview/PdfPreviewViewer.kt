package com.tapconvert.app.ui.preview

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
import androidx.compose.ui.unit.sp
import com.tapconvert.core.model.DimensionConstraint
import com.tapconvert.feature.pdf.engine.DefaultPdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.LinkedHashMap

@Composable
fun PdfPreviewViewer(
    pdfFile: File,
    modifier: Modifier = Modifier
) {
    var totalPages by remember { mutableIntStateOf(1) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Reset zoom when page changes
    LaunchedEffect(currentPageIndex) {
        scale = 1f
        offset = Offset.Zero
    }

    // Bounded LRU Cache for rendered page bitmaps (reclaims memory automatically)
    val pageCache = remember(pdfFile.absolutePath) {
        object : LinkedHashMap<Int, Bitmap>(6, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?): Boolean {
                if (size > 5) {
                    eldest?.value?.recycle()
                    return true
                }
                return false
            }
        }
    }

    // Initialize renderer & load current page
    LaunchedEffect(pdfFile.absolutePath, currentPageIndex) {
        isLoadingPage = true
        loadError = null
        withContext(Dispatchers.IO) {
            try {
                if (!pdfFile.exists() || pdfFile.length() == 0L) {
                    loadError = "PDF file is empty or not found"
                    return@withContext
                }

                val cached = pageCache[currentPageIndex]
                if (cached != null && !cached.isRecycled) {
                    currentBitmap = cached
                    isLoadingPage = false
                    return@withContext
                }

                DefaultPdfPageRenderer(pdfFile).use { renderer ->
                    totalPages = renderer.pageCount.coerceAtLeast(1)
                    val safeIndex = currentPageIndex.coerceIn(0, totalPages - 1)
                    val bitmap = renderer.renderPage(
                        pageIndex = safeIndex,
                        targetDpi = 150f,
                        maxDimension = DimensionConstraint.MaxDimension(1920)
                    )
                    if (bitmap != null) {
                        pageCache[safeIndex] = bitmap
                        currentBitmap = bitmap
                    } else {
                        loadError = "Could not render page $safeIndex"
                    }
                }
            } catch (t: Throwable) {
                loadError = t.message ?: "Failed to read PDF"
            } finally {
                isLoadingPage = false
            }
        }
    }

    // Clean up cache when leaving screen
    DisposableEffect(pdfFile.absolutePath) {
        onDispose {
            pageCache.values.forEach { if (!it.isRecycled) it.recycle() }
            pageCache.clear()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Rendered Page Viewport with Pinch-to-Zoom & Pan
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 4f)
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
                                scale = 2.2f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val bmp = currentBitmap
            if (bmp != null && !bmp.isRecycled) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF Page ${currentPageIndex + 1}",
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
            } else if (isLoadingPage) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            } else if (loadError != null) {
                Text(
                    text = loadError ?: "Failed to display PDF",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // Top Page Badge
        Surface(
            color = Color.Black.copy(alpha = 0.70f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Page ${currentPageIndex + 1} of $totalPages",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                if (scale > 1.05f) {
                    Text(
                        text = "• ${String.format("%.1fx", scale)}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Bottom Page Flipping Controls
        if (totalPages > 1) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (currentPageIndex > 0) currentPageIndex--
                        },
                        enabled = currentPageIndex > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous Page",
                            tint = if (currentPageIndex > 0) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Page indicator dot or slider
                    Slider(
                        value = currentPageIndex.toFloat(),
                        onValueChange = { currentPageIndex = it.toInt().coerceIn(0, totalPages - 1) },
                        valueRange = 0f..(totalPages - 1).toFloat(),
                        steps = (totalPages - 2).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.width(130.dp).height(24.dp)
                    )

                    IconButton(
                        onClick = {
                            if (currentPageIndex < totalPages - 1) currentPageIndex++
                        },
                        enabled = currentPageIndex < totalPages - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next Page",
                            tint = if (currentPageIndex < totalPages - 1) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
