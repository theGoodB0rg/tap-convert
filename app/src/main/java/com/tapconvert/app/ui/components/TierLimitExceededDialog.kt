package com.tapconvert.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tapconvert.app.ui.theme.AccentAmber
import com.tapconvert.core.ads.TierLimitResult

@Composable
fun TierLimitExceededDialog(
    limitInfo: TierLimitResult.LimitExceeded,
    onProceedWithLimit: (Int) -> Unit,
    onUnlockFastPass: () -> Unit,
    onUpgradePro: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = if (limitInfo.isPdf) "Photos to PDF Limit Reached" else "Batch Limit Reached"
    val unitName = if (limitInfo.isPdf) "photos" else "files"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = AccentAmber,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "You selected ${limitInfo.requestedCount} $unitName. Your current plan converts up to ${limitInfo.allowedCount} $unitName at once.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Choose an option below to continue:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Option 1: Trim to allowed limit
                FilledTonalButton(
                    onClick = { onProceedWithLimit(limitInfo.allowedCount) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Convert first ${limitInfo.allowedCount} $unitName", maxLines = 1, softWrap = false)
                }

                // Option 2: Watch Ad for 24h Power Pass (if not already active)
                if (!limitInfo.isFastPassActive && !limitInfo.isPro) {
                    Button(
                        onClick = onUnlockFastPass,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentAmber)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (limitInfo.isPdf) "⚡ Unlock 15 Photos for 24h (Watch Video)" else "⚡ Unlock 10 Files for 24h (Watch Video)",
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Option 3: Pro Upgrade
                if (!limitInfo.isPro) {
                    OutlinedButton(
                        onClick = onUpgradePro,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentAmber)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (limitInfo.isPdf) "⭐ Get Pro (Up to 500 Photos)" else "⭐ Get Pro (Up to 100 Files)",
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Option 4: Cancel
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", maxLines = 1, softWrap = false)
                }
            }
        },
        dismissButton = null
    )
}
