package com.tapconvert.app.ui.result

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tapconvert.app.ui.theme.SavingsGreen
import com.tapconvert.app.ui.theme.SavingsGreenLight
import com.tapconvert.core.database.entity.ConversionRecordEntity
import com.tapconvert.core.model.ConversionResult

@Composable
fun ResultScreen(
    result: ConversionResult,
    record: ConversionRecordEntity,
    onShareClick: (String) -> Unit,
    onFavoriteToggle: (String, Boolean) -> Unit,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFavorited by remember { mutableStateOf(record.isFavorited) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Savings Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SavingsGreenLight),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${result.percentageSaved}% SAVED",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = SavingsGreen
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${formatBytes(result.originalSizeBytes)}  ➔  ${formatBytes(result.outputSizeBytes)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.DarkGray
                )
            }
        }

        // Output Details Card
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Conversion Type", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = result.conversionType.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Duration", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "${result.durationMs} ms",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    isFavorited = !isFavorited
                    onFavoriteToggle(record.id, isFavorited)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Icon(
                    imageVector = if (isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorited) Color.Red else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isFavorited) "Favorited" else "Favorite")
            }

            Button(
                onClick = {
                    result.outputUris.firstOrNull()?.let { onShareClick(it) }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share")
            }
        }

        TextButton(
            onClick = onDoneClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done / Convert Another")
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
