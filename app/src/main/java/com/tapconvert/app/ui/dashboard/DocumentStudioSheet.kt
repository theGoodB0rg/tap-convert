package com.tapconvert.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PictureAsPdf
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentStudioSheet(
    onPhotosToPdfClick: () -> Unit,
    onPdfToPhotosClick: () -> Unit,
    onCompressPdfClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PDF & Document Studio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "100% Offline • Zero Cloud Uploads",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Option 1: Compress PDF
            DocumentStudioOptionCard(
                title = "Compress PDF (Reduce Size)",
                subtitle = "Shrink multi-page PDF documents for email attachments and portal uploads.",
                badgeText = "Save Space",
                icon = Icons.Default.PictureAsPdf,
                gradient = Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669))),
                onClick = {
                    onDismiss()
                    onCompressPdfClick()
                }
            )

            // Option 2: Photos to PDF
            DocumentStudioOptionCard(
                title = "Photos ➔ PDF (Create PDF)",
                subtitle = "Select photos, preview & reorder pages, and merge into a multi-page PDF.",
                badgeText = "Most Popular",
                icon = Icons.Default.Collections,
                gradient = Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF0369A1))),
                onClick = {
                    onDismiss()
                    onPhotosToPdfClick()
                }
            )

            // Option 3: PDF to Photos
            DocumentStudioOptionCard(
                title = "PDF ➔ Photos (Extract Pages)",
                subtitle = "Select an existing PDF and extract each page as high-res JPG/PNG images.",
                badgeText = "Fast Extract",
                icon = Icons.Default.PictureAsPdf,
                gradient = Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFB91C1C))),
                onClick = {
                    onDismiss()
                    onPdfToPhotosClick()
                }
            )
        }
    }
}

@Composable
private fun DocumentStudioOptionCard(
    title: String,
    subtitle: String,
    badgeText: String,
    icon: ImageVector,
    gradient: Brush,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                RoundedCornerShape(16.dp)
            )
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
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
