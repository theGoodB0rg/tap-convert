package com.tapconvert.app.share.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapconvert.app.share.SharePayload
import com.tapconvert.app.share.ShareTargetUiState
import com.tapconvert.app.ui.theme.AccentAmber
import com.tapconvert.app.ui.theme.PrimaryTeal
import com.tapconvert.app.ui.theme.SavingsGreen
import com.tapconvert.core.model.ConversionQuality
import com.tapconvert.core.model.MediaCategory
import com.tapconvert.core.model.MimeType
import com.tapconvert.core.model.Preset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareTargetBottomSheet(
    uiState: ShareTargetUiState,
    onSelectPreset: (Preset) -> Unit,
    onSelectTargetMime: (MimeType) -> Unit,
    onQualityChange: (ConversionQuality) -> Unit,
    onStartConversion: () -> Unit,
    onCancelConversion: () -> Unit,
    onShareResult: (String) -> Unit,
    onOpenInFullStudio: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 10.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(5.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), CircleShape)
                    .align(Alignment.CenterHorizontally)
            )

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "TapConvert Quick Action",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "1-Tap Offline Conversion",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            when (uiState) {
                is ShareTargetUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = "Analyzing shared media on-device...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is ShareTargetUiState.Ready -> {
                    ReadyContent(
                        payload = uiState.payload,
                        suggestedPresets = uiState.suggestedPresets,
                        selectedPreset = uiState.selectedPreset,
                        selectedTargetMimeType = uiState.selectedTargetMimeType,
                        customQuality = uiState.customQuality,
                        onSelectPreset = onSelectPreset,
                        onSelectTargetMime = onSelectTargetMime,
                        onQualityChange = onQualityChange,
                        onConvertClick = onStartConversion,
                        onOpenInFullStudio = onOpenInFullStudio
                    )
                }

                is ShareTargetUiState.Converting -> {
                    ConvertingContent(
                        percentage = uiState.percentage,
                        statusMessage = uiState.statusMessage,
                        onCancelClick = onCancelConversion
                    )
                }

                is ShareTargetUiState.Success -> {
                    SuccessContent(
                        state = uiState,
                        onShareClick = onShareResult,
                        onDoneClick = onDismiss
                    )
                }

                is ShareTargetUiState.Error -> {
                    ErrorContent(
                        message = uiState.error.userReadableMessage,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadyContent(
    payload: SharePayload,
    suggestedPresets: List<Preset>,
    selectedPreset: Preset?,
    selectedTargetMimeType: MimeType,
    customQuality: ConversionQuality,
    onSelectPreset: (Preset) -> Unit,
    onSelectTargetMime: (MimeType) -> Unit,
    onQualityChange: (ConversionQuality) -> Unit,
    onConvertClick: () -> Unit,
    onOpenInFullStudio: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // File Summary Badge Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (payload.category) {
                            MediaCategory.IMAGE -> Icons.Default.Image
                            MediaCategory.VIDEO -> Icons.Default.Videocam
                            MediaCategory.DOCUMENT -> Icons.Default.PictureAsPdf
                            MediaCategory.AUDIO -> Icons.Default.Audiotrack
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (payload.fileNames.size == 1) payload.fileNames.first() else "${payload.fileNames.size} files shared",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "${formatSize(payload.totalSizeBytes)} • ${payload.category.name} Input",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Smart Recommendation Card (Zero-Cognitive Load)
        selectedPreset?.let { preset ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        SavingsGreen.copy(alpha = 0.12f),
                        RoundedCornerShape(14.dp)
                    )
                    .border(
                        1.dp,
                        SavingsGreen.copy(alpha = 0.35f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "SMART RECOMMENDED GOAL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SavingsGreen
                    )
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = preset.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Suggested Presets Quick Chips
        if (suggestedPresets.isNotEmpty()) {
            Text(
                text = "1-Tap Goal Presets",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(suggestedPresets) { preset ->
                    FilterChip(
                        selected = selectedPreset?.id == preset.id,
                        onClick = { onSelectPreset(preset) },
                        label = { Text(preset.name) },
                        leadingIcon = {
                            if (selectedPreset?.id == preset.id) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }
            }
        }

        // Target Format selection row
        Text(
            text = "Target Format",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        val targetFormats = when (payload.category) {
            MediaCategory.IMAGE -> listOf(MimeType.Image.WEBP, MimeType.Image.JPEG, MimeType.Image.PNG, MimeType.Document.PDF)
            MediaCategory.VIDEO -> listOf(MimeType.Video.MP4, MimeType.Audio.MP3, MimeType.Audio.AAC)
            MediaCategory.DOCUMENT -> listOf(MimeType.Document.PDF, MimeType.Image.JPEG, MimeType.Image.PNG)
            MediaCategory.AUDIO -> listOf(MimeType.Audio.MP3, MimeType.Audio.AAC)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            targetFormats.forEach { mime ->
                FilterChip(
                    selected = selectedTargetMimeType == mime,
                    onClick = { onSelectTargetMime(mime) },
                    label = { Text(mime.displayName) }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Convert & Return to Share Button
        Button(
            onClick = onConvertClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (selectedPreset != null) "Convert & Return: ${selectedPreset.name}" else "Convert & Return: ${selectedTargetMimeType.displayName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        OutlinedButton(
            onClick = onOpenInFullStudio,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Open in Full Studio (Advanced)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConvertingContent(
    percentage: Int,
    statusMessage: String,
    onCancelClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Converting Media On-Device...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        LinearProgressIndicator(
            progress = { percentage / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = statusMessage.ifBlank { "Transcoding..." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        OutlinedButton(
            onClick = onCancelClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun SuccessContent(
    state: ShareTargetUiState.Success,
    onShareClick: (String) -> Unit,
    onDoneClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        SavingsGreen.copy(alpha = 0.15f),
                        RoundedCornerShape(16.dp)
                    )
                    .border(
                        1.dp,
                        SavingsGreen.copy(alpha = 0.4f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SavingsGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Ready to Share!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SavingsGreen
                        )
                        Text(
                            text = "${formatSize(state.result.originalSizeBytes)} ➔ ${formatSize(state.result.outputSizeBytes)} (${state.record.savingsPercentage}% saved)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                val primaryOutput = state.outputFilePaths.firstOrNull() ?: ""
                onShareClick(primaryOutput)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Share Converted File Back",
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        OutlinedButton(
            onClick = onDoneClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Done", maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "Conversion Failed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Close", maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        "%.1f MB".format(mb)
    } else {
        "%.0f KB".format(kb)
    }
}

