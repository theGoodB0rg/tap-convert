package com.tapconvert.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tapconvert.app.ui.theme.SavingsGreen
import com.tapconvert.app.ui.theme.SavingsGreenLight
import com.tapconvert.core.model.MediaCategory
import com.tapconvert.core.model.Preset

@Composable
fun DashboardScreen(
    onCategoryClick: (MediaCategory) -> Unit,
    onPresetClick: (Preset) -> Unit,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Savings Header Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = SavingsGreenLight),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "TapConvert Engine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SavingsGreen
                    )
                    Text(
                        text = "Fast, lossless & offline conversion",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray
                    )
                }
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Fast Engine",
                    tint = SavingsGreen,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Quick Category Cards
        Text(
            text = "Convert by Category",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CategoryCard(
                category = MediaCategory.IMAGE,
                icon = Icons.Default.Image,
                title = "Images",
                subtitle = "JPG, PNG, WebP",
                onClick = { onCategoryClick(MediaCategory.IMAGE) },
                modifier = Modifier.weight(1f)
            )
            CategoryCard(
                category = MediaCategory.VIDEO,
                icon = Icons.Default.Videocam,
                title = "Videos",
                subtitle = "MP4, 16MB Cap",
                onClick = { onCategoryClick(MediaCategory.VIDEO) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CategoryCard(
                category = MediaCategory.DOCUMENT,
                icon = Icons.Default.PictureAsPdf,
                title = "PDF Documents",
                subtitle = "Photos to PDF",
                onClick = { onCategoryClick(MediaCategory.DOCUMENT) },
                modifier = Modifier.weight(1f)
            )
            CategoryCard(
                category = MediaCategory.AUDIO,
                icon = Icons.Default.Audiotrack,
                title = "Audio",
                subtitle = "Extract to MP3",
                onClick = { onCategoryClick(MediaCategory.AUDIO) },
                modifier = Modifier.weight(1f)
            )
        }

        // Instant Preset Chips
        Text(
            text = "Quick Presets",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        val quickPresets = listOf(
            Preset.WhatsAppVideo16MB,
            Preset.GovPassport200KB,
            Preset.Mp3HighQuality320,
            Preset.PdfToImages,
            Preset.JpegToWebp,
            Preset.DiscordVideo10MB
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(quickPresets) { preset ->
                AssistChip(
                    onClick = { onPresetClick(preset) },
                    label = { Text(preset.name) },
                    leadingIcon = {
                        Icon(
                            imageVector = when (preset.category) {
                                MediaCategory.IMAGE -> Icons.Default.Image
                                MediaCategory.VIDEO -> Icons.Default.Videocam
                                MediaCategory.DOCUMENT -> Icons.Default.PictureAsPdf
                                MediaCategory.AUDIO -> Icons.Default.Audiotrack
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun CategoryCard(
    category: MediaCategory,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
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
