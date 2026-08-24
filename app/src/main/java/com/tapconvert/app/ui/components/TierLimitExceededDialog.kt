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
    val title = if (limitInfo.isPdf) "Photos to PDF Limit" else "Batch Limit Reached"
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
                if (limitInfo.canUnlockWithReward) {
                    Text(
                        text = "You selected ${limitInfo.requestedCount} $unitName. The standard free tier converts up to ${limitInfo.freeLimit} $unitName at once.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Watch a short video ad to unlock and convert all ${limitInfo.requestedCount} $unitName in this batch, or upgrade to Pro for unlimited batching.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "You selected ${limitInfo.requestedCount} $unitName. Rewarded batch pass supports up to ${limitInfo.rewardedLimit} $unitName. Converting ${limitInfo.requestedCount} $unitName in one tap requires Pro.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Upgrade to Pro for up to 100 files at once with zero ads, or trim to the allowed limit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Option 1: Watch video to unlock THIS batch (if <= rewardedLimit)
                if (limitInfo.canUnlockWithReward) {
                    Button(
                        onClick = onUnlockFastPass,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = AccentAmber
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Watch Video to Convert All ${limitInfo.requestedCount} $unitName",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else if (!limitInfo.isPro) {
                    // Over rewarded limit: Option to watch video for max rewarded batch
                    Button(
                        onClick = {
                            onUnlockFastPass()
                            onProceedWithLimit(limitInfo.rewardedLimit)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = AccentAmber
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Unlock Max ${limitInfo.rewardedLimit} $unitName (Watch Video)",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Option 2: Pro Upgrade
                if (!limitInfo.isPro) {
                    OutlinedButton(
                        onClick = onUpgradePro,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = AccentAmber
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Upgrade to Pro (Unlimited & No Ads)",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Option 3: Convert First FreeLimit files
                FilledTonalButton(
                    onClick = { onProceedWithLimit(limitInfo.freeLimit) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Convert First ${limitInfo.freeLimit} $unitName (Free)")
                }

                // Option 4: Cancel
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        dismissButton = null
    )
}
