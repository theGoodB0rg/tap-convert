package com.tapconvert.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapconvert.app.ui.theme.AccentAmber
import com.tapconvert.app.ui.theme.AccentPurple
import com.tapconvert.app.ui.theme.AccentSky
import com.tapconvert.app.ui.theme.ErrorRed
import com.tapconvert.app.ui.theme.SavingsGreen
import com.tapconvert.core.database.entity.ConversionRecordEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    records: List<ConversionRecordEntity>,
    totalStorageBytes: Long,
    onlyFavoritesFilter: Boolean,
    onToggleFavoritesFilter: () -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onDeleteRecord: (String) -> Unit,
    onCleanCacheClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

    val filteredRecords = remember(records, onlyFavoritesFilter, selectedCategoryFilter) {
        records.filter { record ->
            val favMatch = !onlyFavoritesFilter || record.isFavorited
            val catMatch = when (selectedCategoryFilter) {
                "IMAGE" -> record.conversionType.contains("IMAGE")
                "VIDEO" -> record.conversionType.contains("VIDEO")
                "PDF" -> record.conversionType.contains("PDF")
                "AUDIO" -> record.conversionType.contains("AUDIO")
                else -> true
            }
            favMatch && catMatch
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Storage Breakdown Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Cumulative Space Reclaimed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatStorage(totalStorageBytes),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = SavingsGreen,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    FilledTonalButton(
                        onClick = onCleanCacheClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "Free Up Space",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Free Up Space",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Segmented Storage Color Meter Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(0.50f).fillMaxHeight().background(AccentPurple))
                        Box(modifier = Modifier.weight(0.25f).fillMaxHeight().background(AccentSky))
                        Box(modifier = Modifier.weight(0.15f).fillMaxHeight().background(ErrorRed))
                        Box(modifier = Modifier.weight(0.10f).fillMaxHeight().background(AccentAmber))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StorageLegendItem(color = AccentPurple, label = "Videos")
                    StorageLegendItem(color = AccentSky, label = "Photos")
                    StorageLegendItem(color = ErrorRed, label = "PDFs")
                    StorageLegendItem(color = AccentAmber, label = "Audio")
                }
            }
        }

        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            item {
                FilterChip(
                    selected = !onlyFavoritesFilter && selectedCategoryFilter == "ALL",
                    onClick = {
                        if (onlyFavoritesFilter) onToggleFavoritesFilter()
                        selectedCategoryFilter = "ALL"
                    },
                    label = { Text("All (${records.size})") }
                )
            }
            item {
                FilterChip(
                    selected = onlyFavoritesFilter,
                    onClick = onToggleFavoritesFilter,
                    label = { Text("Favorites") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (onlyFavoritesFilter) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (onlyFavoritesFilter) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
            item {
                FilterChip(
                    selected = selectedCategoryFilter == "VIDEO",
                    onClick = { selectedCategoryFilter = if (selectedCategoryFilter == "VIDEO") "ALL" else "VIDEO" },
                    label = { Text("Videos") }
                )
            }
            item {
                FilterChip(
                    selected = selectedCategoryFilter == "IMAGE",
                    onClick = { selectedCategoryFilter = if (selectedCategoryFilter == "IMAGE") "ALL" else "IMAGE" },
                    label = { Text("Photos") }
                )
            }
            item {
                FilterChip(
                    selected = selectedCategoryFilter == "PDF",
                    onClick = { selectedCategoryFilter = if (selectedCategoryFilter == "PDF") "ALL" else "PDF" },
                    label = { Text("PDFs") }
                )
            }
        }

        // Records List
        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (onlyFavoritesFilter) "No favorite conversions found" else "No conversion history matches",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredRecords, key = { it.id }) { record ->
                    HistoryItemCard(
                        record = record,
                        onToggleFavorite = { onToggleFavorite(record.id, !record.isFavorited) },
                        onDelete = { onDeleteRecord(record.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageLegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun HistoryItemCard(
    record: ConversionRecordEntity,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = record.conversionType.replace("_", " "),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${formatStorage(record.originalSizeBytes)} ➔ ${formatStorage(record.outputSizeBytes)} (${String.format("%.0f", record.savingsPercentage)}% saved)",
                    style = MaterialTheme.typography.bodySmall,
                    color = SavingsGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (record.isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (record.isFavorited) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun formatStorage(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return if (gb >= 1.0) {
        String.format("%.2f GB", gb)
    } else if (mb >= 1.0) {
        String.format("%.1f MB", mb)
    } else {
        String.format("%.1f KB", kb)
    }
}

