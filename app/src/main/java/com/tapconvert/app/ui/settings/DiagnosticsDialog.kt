package com.tapconvert.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tapconvert.app.ui.theme.AccentAmber
import com.tapconvert.app.ui.theme.ErrorRed
import com.tapconvert.app.ui.theme.SavingsGreen
import com.tapconvert.core.common.diagnostics.AppObservabilityRegistry
import com.tapconvert.core.common.diagnostics.MemoryPressureLevel

@Composable
fun DiagnosticsDialog(
    onDismissRequest: () -> Unit,
    registry: AppObservabilityRegistry = AppObservabilityRegistry.instance
) {
    val context = LocalContext.current
    val healthSnapshot by registry.healthState.collectAsState()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Diagnostics & Health",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Memory Health Card
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "JVM Heap Memory",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val (pressureColor, pressureLabel) = when (healthSnapshot.memoryPressureLevel) {
                                        MemoryPressureLevel.NORMAL -> SavingsGreen to "NORMAL"
                                        MemoryPressureLevel.MODERATE -> AccentAmber to "MODERATE"
                                        MemoryPressureLevel.CRITICAL -> ErrorRed to "CRITICAL"
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = pressureColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = pressureLabel,
                                            color = pressureColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                val used = healthSnapshot.usedHeapMb
                                val max = healthSnapshot.maxHeapMb
                                val fraction = if (max > 0) (used.toFloat() / max.toFloat()).coerceIn(0f, 1f) else 0f

                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = if (fraction > 0.85f) ErrorRed else if (fraction > 0.65f) AccentAmber else SavingsGreen,
                                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                )

                                Text(
                                    text = "$used MB used / $max MB max allocated (Free: ${healthSnapshot.availableMemoryMb} MB)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Conversion Pipeline Performance
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Conversion Performance",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MetricCell("Completed", "${healthSnapshot.totalConversionsCompleted}", SavingsGreen)
                                    MetricCell("Failed", "${healthSnapshot.totalConversionsFailed}", if (healthSnapshot.totalConversionsFailed > 0) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant)
                                    MetricCell("Active", "${healthSnapshot.activeConversionsCount}", MaterialTheme.colorScheme.primary)
                                    MetricCell("Avg Latency", "${healthSnapshot.averageDurationMs} ms", MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }

                    // Recent Traces
                    item {
                        Text(
                            text = "Recent Operations (${healthSnapshot.recentTraces.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (healthSnapshot.recentTraces.isEmpty()) {
                        item {
                            Text(
                                text = "No operations recorded yet in this session.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        items(healthSnapshot.recentTraces.take(10)) { trace ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = trace.conversionType,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "${trace.durationMs} ms • ${trace.throughputKbps} KB/s",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!trace.sanitizedError.isNullOrBlank()) {
                                            Text(
                                                text = trace.sanitizedError ?: "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ErrorRed
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (trace.isSuccess) SavingsGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = if (trace.isSuccess) "OK" else "FAIL",
                                            color = if (trace.isSuccess) SavingsGreen else ErrorRed,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Actions Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val json = registry.exportSanitizedReportJson()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("TapConvert Diagnostics", json)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Sanitized report copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Report", maxLines = 1)
                    }

                    Button(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Done", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
