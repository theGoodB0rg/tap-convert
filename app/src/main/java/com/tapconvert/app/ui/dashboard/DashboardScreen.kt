package com.tapconvert.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapconvert.app.ui.theme.*
import com.tapconvert.core.database.entity.ConversionRecordEntity
import com.tapconvert.core.model.MediaCategory
import com.tapconvert.core.model.Preset

@Composable
fun DashboardScreen(
    records: List<ConversionRecordEntity> = emptyList(),
    totalStorageBytes: Long = 0L,
    onCategoryClick: (MediaCategory) -> Unit,
    onPresetClick: (Preset) -> Unit,
    onUniversalIntakeClick: () -> Unit = { onCategoryClick(MediaCategory.IMAGE) },
    onHistoryClick: () -> Unit,
    onFastPassClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalSavedBytes = records.sumOf { (it.originalSizeBytes - it.outputSizeBytes).coerceAtLeast(0L) }
    val avgSavingsPercent = if (records.isNotEmpty()) {
        (records.map { it.savingsPercentage }.average()).toInt().coerceIn(0, 100)
    } else {
        0
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Hero Savings Metric Card (Real Dynamic Storage Metrics)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                SavingsGreen.copy(alpha = 0.15f),
                                PrimaryTeal.copy(alpha = 0.08f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        SavingsGreen.copy(alpha = 0.35f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Storage Reclaimed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatBytes(totalSavedBytes),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = SavingsGreen,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = if (records.isEmpty()) "Ready for your 1st conversion" else "${records.size} conversions completed offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        color = SavingsGreen.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (records.isEmpty()) "100% Offline" else "$avgSavingsPercent% Saved",
                            color = SavingsGreen,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Universal 1-Tap Intake Dropzone
        Card(
            onClick = onUniversalIntakeClick,
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Select Media",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Tap or Drop Media to Convert",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Photos, 4K Videos, PDF Documents & Audio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1-Tap Instant Goal Presets
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "1-Tap Goal Presets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Fast Pathways",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val quickPresets = listOf(
                Preset.WhatsAppVideo16MB,
                Preset.GovPassport200KB,
                Preset.JpegToWebp,
                Preset.Mp3HighQuality320,
                Preset.PdfToImages,
                Preset.DiscordVideo10MB
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(quickPresets) { preset ->
                    PresetGoalCard(preset = preset, onClick = { onPresetClick(preset) })
                }
            }
        }

        // Categorical Hub
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Convert by Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CategoryTile(
                    title = "Images",
                    subtitle = "JPG, PNG, WebP",
                    icon = Icons.Default.Image,
                    gradient = Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF0369A1))),
                    onClick = { onCategoryClick(MediaCategory.IMAGE) },
                    modifier = Modifier.weight(1f)
                )
                CategoryTile(
                    title = "Videos",
                    subtitle = "MP4, 16MB Cap",
                    icon = Icons.Default.Videocam,
                    gradient = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))),
                    onClick = { onCategoryClick(MediaCategory.VIDEO) },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CategoryTile(
                    title = "PDF Docs",
                    subtitle = "Photos to PDF",
                    icon = Icons.Default.PictureAsPdf,
                    gradient = Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFB91C1C))),
                    onClick = { onCategoryClick(MediaCategory.DOCUMENT) },
                    modifier = Modifier.weight(1f)
                )
                CategoryTile(
                    title = "Audio",
                    subtitle = "Extract to MP3",
                    icon = Icons.Default.Audiotrack,
                    gradient = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706))),
                    onClick = { onCategoryClick(MediaCategory.AUDIO) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Polite Native Sponsorship Card (Ethical Monetization)
        Card(
            onClick = onFastPassClick,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    AccentAmber.copy(alpha = 0.35f),
                    RoundedCornerShape(16.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentAmber.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Fast Pass",
                        tint = AccentAmber,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Unlock Batch Mode & Ultra Speed",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Watch a 30s video for 24h Fast-Pass pass",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = onFastPassClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Unlock", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun PresetGoalCard(
    preset: Preset,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .width(180.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = when (preset.tag) {
                    "Social" -> Color(0xFF3B82F6).copy(alpha = 0.18f)
                    "Government" -> Color(0xFFEF4444).copy(alpha = 0.18f)
                    "Music", "Audio" -> Color(0xFFF59E0B).copy(alpha = 0.18f)
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                },
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = preset.tag,
                    color = when (preset.tag) {
                        "Social" -> Color(0xFF3B82F6)
                        "Government" -> Color(0xFFEF4444)
                        "Music", "Audio" -> Color(0xFFF59E0B)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Text(
                text = preset.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Text(
                text = preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun CategoryTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        else -> "%.0f KB".format(kb)
    }
}


