package com.tapconvert.app.ui.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapconvert.app.ui.theme.PrimaryTeal
import com.tapconvert.app.ui.theme.SavingsGreen
import com.tapconvert.core.database.entity.ConversionRecordEntity
import com.tapconvert.core.model.ConversionResult
import com.tapconvert.core.model.ConversionType
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    result: ConversionResult,
    record: ConversionRecordEntity,
    onShareClick: (String) -> Unit,
    onShareMultipleClick: (List<String>) -> Unit = { list -> list.firstOrNull()?.let { onShareClick(it) } },
    onShareAsDocumentClick: (String) -> Unit = onShareClick,
    onShareMultipleAsDocumentClick: (List<String>) -> Unit = onShareMultipleClick,
    onFavoriteToggle: (String, Boolean) -> Unit,
    onDoneClick: () -> Unit,
    isPro: Boolean = false,
    onUpgradeProClick: () -> Unit = {},
    showReviewPrompt: Boolean = false,
    onReviewAccepted: () -> Unit = {},
    onReviewDismissed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isFavorited by remember { mutableStateOf(record.isFavorited) }
    val isBatch = result.outputUris.size > 1

    val inputPath = record.inputUris.firstOrNull()?.removePrefix("file://")
    val outputPath = result.outputUris.firstOrNull()?.removePrefix("file://")

    var inputImageBitmap by remember(inputPath) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    var outputImageBitmap by remember(outputPath) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }

    LaunchedEffect(inputPath, outputPath) {
        if (!isBatch) {
            if (inputPath != null) {
                val f = File(inputPath)
                if (f.exists() && f.length() > 0) {
                    try {
                        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                        val b = android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts)
                        if (b != null) {
                            inputImageBitmap = b.asImageBitmap()
                        }
                    } catch (_: Throwable) {}
                }
            }
            if (outputPath != null) {
                val f = File(outputPath)
                if (f.exists() && f.length() > 0) {
                    try {
                        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                        val b = android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts)
                        if (b != null) {
                            outputImageBitmap = b.asImageBitmap()
                        }
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Upper Viewport: Vertically centered celebratory content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Unified Celebration Hero Card
                ResultHeroCard(
                    result = result,
                    isBatch = isBatch
                )

                // 2. Visual Fidelity Inspection Card (Rendered ONLY for images with valid before/after bitmaps)
                if (!isBatch && inputImageBitmap != null && outputImageBitmap != null) {
                    ResultVisualDiffCard(
                        inputBitmap = inputImageBitmap!!,
                        outputBitmap = outputImageBitmap!!,
                        originalSize = result.originalSizeBytes,
                        outputSize = result.outputSizeBytes
                    )
                }

                // 3. Batch Breakdown (Only for multi-file batches)
                if (isBatch) {
                    ResultBatchListCard(
                        outputUris = result.outputUris,
                        onShareClick = onShareClick
                    )
                }

                // 4. Pro CRO Promo Pill (Slim, Non-Intrusive)
                if (!isPro) {
                    ResultProPromoPill(
                        onUpgradeClick = onUpgradeProClick
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom Dock: Ergonomic Primary Actions, Utility Footer & Mandatory Banner Ad
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 5. High-Converting, Ergonomic Action Section (Thumb reach)
            ResultActionSection(
                result = result,
                isBatch = isBatch,
                onShareClick = onShareClick,
                onShareMultipleClick = onShareMultipleClick,
                onShareAsDocumentClick = onShareAsDocumentClick,
                onShareMultipleAsDocumentClick = onShareMultipleAsDocumentClick
            )

            // 6. Clean Utility Footer: Favorite & Done
            ResultUtilityFooter(
                isFavorited = isFavorited,
                onFavoriteToggle = {
                    isFavorited = !isFavorited
                    onFavoriteToggle(record.id, isFavorited)
                },
                onDoneClick = onDoneClick
            )

            // 7. Mandatory Persistent Bottom Banner Ad (Non-Pro Monetization)
            if (!isPro) {
                com.tapconvert.app.ui.components.monetization.AdaptiveBannerAd(
                    isAdFree = isPro
                )
            }
        }
    }

    // 5-Second Review Bottom Sheet
    if (showReviewPrompt) {
        ReviewBottomSheet(
            onReviewAccepted = onReviewAccepted,
            onReviewDismissed = onReviewDismissed
        )
    }
}

@Composable
private fun ResultHeroCard(
    result: ConversionResult,
    isBatch: Boolean,
    modifier: Modifier = Modifier
) {
    val singleOutputUri = result.outputUris.firstOrNull()?.removePrefix("file://")
    val singleOutputFile = singleOutputUri?.let { File(it) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SavingsGreen.copy(alpha = 0.18f),
                            PrimaryTeal.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
                .border(
                    1.dp,
                    SavingsGreen.copy(alpha = 0.4f),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Victory Check Icon
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(SavingsGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Bold Savings Headline
                if (result.percentageSaved > 0) {
                    Text(
                        text = if (isBatch) "${result.percentageSaved}% Total Saved" else "${result.percentageSaved}% Smaller",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = SavingsGreen,
                        letterSpacing = (-0.5).sp
                    )
                } else {
                    Text(
                        text = "Conversion Complete!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Size Comparison (Before ➔ After)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatBytes(result.originalSizeBytes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "➔",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = SavingsGreen
                    )
                    Text(
                        text = formatBytes(result.outputSizeBytes),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = SavingsGreen
                    )
                }

                // Output File Identifier Pill
                if (!isBatch && singleOutputFile != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = singleOutputFile.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else if (isBatch) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(0.5.dp, SavingsGreen.copy(alpha = 0.3f)),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "${result.outputUris.size} files converted & saved",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = SavingsGreen,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultVisualDiffCard(
    inputBitmap: androidx.compose.ui.graphics.ImageBitmap,
    outputBitmap: androidx.compose.ui.graphics.ImageBitmap,
    originalSize: Long,
    outputSize: Long,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quality Preview",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "100% Offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = SavingsGreen,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = inputBitmap,
                        contentDescription = "Original",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Original: ${formatBytes(originalSize)}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, SavingsGreen, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = outputBitmap,
                        contentDescription = "Optimized",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        color = SavingsGreen.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Optimized: ${formatBytes(outputSize)}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultBatchListCard(
    outputUris: List<String>,
    onShareClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Batch Output (${outputUris.size} files)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            outputUris.forEachIndexed { index, path ->
                val f = File(path.removePrefix("file://"))
                val fileLen = if (f.exists()) f.length() else 0L

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "#${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = f.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatBytes(fileLen),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { onShareClick(path) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share item",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultProPromoPill(
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onUpgradeClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(
                0.8.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )

            Text(
                text = "100-File Batches & Zero Ads",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Upgrade ➔",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun ResultActionSection(
    result: ConversionResult,
    isBatch: Boolean,
    onShareClick: (String) -> Unit,
    onShareMultipleClick: (List<String>) -> Unit,
    onShareAsDocumentClick: (String) -> Unit,
    onShareMultipleAsDocumentClick: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryShareLabel = when (result.conversionType) {
        ConversionType.VIDEO_COMPRESS -> if (isBatch) "Share All Videos (${result.outputUris.size})" else "Share Video"
        ConversionType.IMAGE_COMPRESS,
        ConversionType.IMAGE_CONVERT,
        ConversionType.PDF_TO_IMAGES -> if (isBatch) "Share All Photos (${result.outputUris.size})" else "Share Photo"
        ConversionType.IMAGES_TO_PDF,
        ConversionType.PDF_COMPRESS -> if (isBatch) "Share All PDFs (${result.outputUris.size})" else "Share PDF"
        ConversionType.EXTRACT_AUDIO -> if (isBatch) "Share All Audio (${result.outputUris.size})" else "Share Audio"
        else -> if (isBatch) "Share All (${result.outputUris.size})" else "Share Converted"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Primary Share Button
        Button(
            onClick = {
                if (isBatch) {
                    onShareMultipleClick(result.outputUris)
                } else {
                    result.outputUris.firstOrNull()?.let { onShareClick(it) }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = primaryShareLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Secondary "Share as File" Button (Supports raw document sharing)
        OutlinedButton(
            onClick = {
                if (isBatch) {
                    onShareMultipleAsDocumentClick(result.outputUris)
                } else {
                    result.outputUris.firstOrNull()?.let { onShareAsDocumentClick(it) }
                }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isBatch) "Share as Files" else "Share as File",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ResultUtilityFooter(
    isFavorited: Boolean,
    onFavoriteToggle: () -> Unit,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onFavoriteToggle,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = if (isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorited) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isFavorited) "Saved to Favorites" else "Save to Favorites",
                style = MaterialTheme.typography.labelLarge,
                color = if (isFavorited) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }

        TextButton(
            onClick = onDoneClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Done ➔",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewBottomSheet(
    onReviewAccepted: () -> Unit,
    onReviewDismissed: () -> Unit
) {
    var selectedRating by remember { mutableIntStateOf(5) }
    ModalBottomSheet(
        onDismissRequest = onReviewDismissed,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFB800).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFB800),
                    modifier = Modifier.size(30.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Enjoying TapConvert?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Rate us in 5 seconds to support 100% offline tools.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (star in 1..5) {
                    IconButton(
                        onClick = { selectedRating = star },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = if (star <= selectedRating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "$star stars",
                            tint = if (star <= selectedRating) Color(0xFFFFB800) else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }

            Button(
                onClick = onReviewAccepted,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFFFFB800)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Rate TapConvert", fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = onReviewDismissed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Maybe Later",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format("%.1f MB", mb)
    } else {
        String.format("%.1f KB", kb)
    }
}

