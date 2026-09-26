package com.tapconvert.app.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tapconvert.app.ui.theme.SavingsGreen
import com.tapconvert.core.model.MediaCategory
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppMediaPreviewDialog(
    item: PreviewMediaItem,
    onDismiss: () -> Unit,
    onShareClick: (String) -> Unit,
    onShareAsDocumentClick: (String) -> Unit = onShareClick,
    onSaveToDeviceClick: ((String) -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF121212)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Navigation Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close preview",
                            tint = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = item.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = item.category.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Text(
                                text = formatBytes(item.fileSizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )

                            if (item.percentageSaved > 0) {
                                Text(
                                    text = "-${item.percentageSaved}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SavingsGreen
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.size(40.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Central Media Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when (item.category) {
                        MediaCategory.VIDEO -> {
                            VideoPreviewPlayer(
                                videoFile = item.file,
                                modifier = Modifier.fillMaxSize(),
                                autoPlay = true
                            )
                        }
                        MediaCategory.IMAGE -> {
                            val origFile = item.originalUriString?.removePrefix("file://")?.let { File(it) }
                            ImagePreviewViewer(
                                imageFile = item.file,
                                originalFile = origFile,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        MediaCategory.DOCUMENT -> {
                            PdfPreviewViewer(
                                pdfFile = item.file,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        MediaCategory.AUDIO -> {
                            AudioPreviewPlayer(
                                audioFile = item.file,
                                modifier = Modifier.wrapContentHeight()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Dock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Primary Share
                    Button(
                        onClick = { onShareClick(item.uriString) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Share",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Share as File
                    OutlinedButton(
                        onClick = { onShareAsDocumentClick(item.uriString) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Share as File",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "As File",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Save to Device / Gallery if requested
                    if (onSaveToDeviceClick != null) {
                        FilledTonalIconButton(
                            onClick = { onSaveToDeviceClick(item.uriString) },
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Save to device",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%.0f KB", kb)
}
