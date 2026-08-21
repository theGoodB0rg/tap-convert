package com.tapconvert.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tapconvert.app.ui.theme.AccentAmber
import com.tapconvert.app.ui.theme.SavingsGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToUseScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "How to Use TapConvert",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Quick Tips & Feature Guide",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Learn how to convert, compress, and merge media in seconds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Guide Item 1: Photos to PDF
            GuideCard(
                icon = Icons.Default.PictureAsPdf,
                iconTint = Color(0xFFEF4444),
                title = "Photos to PDF with Visual Reordering",
                summary = "Create clean, multi-page PDF documents from gallery pictures",
                details = listOf(
                    "1. Tap 'PDF Docs' on the dashboard and choose 'Photos ➔ PDF'.",
                    "2. Select your photos from your gallery or file manager.",
                    "3. In the setup screen, inspect image thumbnails at a glance.",
                    "4. Tap on any image thumbnail to view an enlarged full-screen preview.",
                    "5. Use the Up/Down arrows to reorder pages or tap (X) to remove unwanted photos.",
                    "6. Tap 'Create PDF' to instantly generate your PDF 100% offline."
                )
            )

            // Guide Item 2: Smart Goal Presets
            GuideCard(
                icon = Icons.Default.Bolt,
                iconTint = AccentAmber,
                title = "1-Tap Goal Presets",
                summary = "Never guess bitrates or dimensions for specific apps",
                details = listOf(
                    "• WhatsApp Video (16MB): Compresses videos to fit strictly under WhatsApp's 16MB file transfer limit without failing.",
                    "• Discord Video (10MB): Fits videos under Discord's free tier upload cap.",
                    "• Gov Passport (200KB): Resizes and compresses ID photos strictly under 200KB for government and job portal submissions.",
                    "• JPEG to WebP: Squeezes photos up to 80% smaller with zero visible loss in quality.",
                    "• Extract Audio (MP3): Strips video tracks and saves crisp audio files."
                )
            )

            // Guide Item 3: Direct Share Sheet Integration
            GuideCard(
                icon = Icons.Default.Share,
                iconTint = Color(0xFF3B82F6),
                title = "Convert Directly from Other Apps",
                summary = "Use Android Share Sheet without opening TapConvert first",
                details = listOf(
                    "1. In WhatsApp, Telegram, Files, or Google Photos, select any photo, video, or PDF.",
                    "2. Tap 'Share' and pick 'TapConvert Quick Action'.",
                    "3. Select a 1-tap preset (e.g. 'WhatsApp Compress' or 'Merge to PDF') or choose 'Open in Full Studio' for full custom controls.",
                    "4. The converted file is automatically saved to your storage and ready to share back."
                )
            )

            // Guide Item 4: Offline Privacy & Zero Cloud
            GuideCard(
                icon = Icons.Default.Security,
                iconTint = SavingsGreen,
                title = "100% Offline & Private",
                summary = "Zero cloud uploads, no network permissions needed for processing",
                details = listOf(
                    "• All compression, decoding, transcoding, and PDF generation happen directly on your device's processor.",
                    "• Your photos, videos, and sensitive documents never leave your phone.",
                    "• Works completely in Airplane mode or in areas without internet connection."
                )
            )

            // Guide Item 5: Free, Fast Pass & Pro Limits
            GuideCard(
                icon = Icons.Default.Star,
                iconTint = AccentAmber,
                title = "Batch Limits & Fast Pass",
                summary = "Understand file capacities and free daily rewards",
                details = listOf(
                    "• Free Tier: Up to 5 photos for PDF and 2 files for batch media conversion.",
                    "• Fast Pass (24h): Watch a single 30s video to unlock 15 photos for PDF and 10 files for batch media conversion for a full 24 hours.",
                    "• TapConvert Pro: Unlimited batching up to 500 photos and 100 media files, ultra-fast parallel processing, and permanent 100% ad-free experience."
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GuideCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    summary: String,
    details: List<String>
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(4.dp))
                    details.forEach { step ->
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
