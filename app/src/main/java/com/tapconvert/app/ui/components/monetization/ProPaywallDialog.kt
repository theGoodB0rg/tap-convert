package com.tapconvert.app.ui.components.monetization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tapconvert.app.ui.monetization.ProFeatureIcon
import com.tapconvert.app.ui.monetization.ProFeatureItem
import com.tapconvert.app.ui.monetization.ProFeatureProvider
import com.tapconvert.app.ui.monetization.SubscriptionPlanUiModel
import com.tapconvert.app.ui.monetization.toUiModel
import com.tapconvert.app.ui.theme.AccentAmber
import com.tapconvert.app.ui.theme.AccentPurple
import com.tapconvert.app.ui.theme.PrimaryTeal
import com.tapconvert.app.ui.theme.SavingsGreen
import com.tapconvert.core.ads.SubscriptionPlan

@Composable
fun ProPaywallDialog(
    isPro: Boolean,
    onPurchasePlan: (SubscriptionPlan) -> Unit,
    onUnlockRewardedPass: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val features = ProFeatureProvider.getProFeatures()
    val annualPlan = SubscriptionPlan.Annual.toUiModel()
    val monthlyPlan = SubscriptionPlan.Monthly.toUiModel()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Star
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(AccentAmber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = AccentAmber,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Title
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isPro) "TapConvert Pro Active" else "TapConvert Pro",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!isPro) {
                        Text(
                            text = "Supercharge your offline media workflow",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Features list with professional tonal icons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    features.forEach { feature ->
                        ProFeatureItemRow(feature = feature)
                    }
                }

                if (!isPro) {
                    Spacer(modifier = Modifier.height(2.dp))

                    // Plan Selection Cards
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SubscriptionPlanTile(
                            model = annualPlan,
                            onClick = { onPurchasePlan(SubscriptionPlan.Annual) }
                        )

                        SubscriptionPlanTile(
                            model = monthlyPlan,
                            onClick = { onPurchasePlan(SubscriptionPlan.Monthly) }
                        )
                    }

                    // 24h Pass Button
                    FilledTonalButton(
                        onClick = onUnlockRewardedPass,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = AccentAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("24-Hour Pass (Watch Video)", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Dismiss Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isPro) "Done" else "Maybe Later",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ProFeatureItemRow(
    feature: ProFeatureItem,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (feature.icon) {
        ProFeatureIcon.BATCH -> Icons.Default.Layers to PrimaryTeal
        ProFeatureIcon.SPEED -> Icons.Default.Speed to SavingsGreen
        ProFeatureIcon.AD_FREE -> Icons.Default.Block to AccentPurple
        ProFeatureIcon.WATERMARK_FREE -> Icons.Default.PictureAsPdf to Color(0xFFEF4444)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }

        Text(
            text = feature.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SubscriptionPlanTile(
    model: SubscriptionPlanUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (model.isBestValue) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = if (model.isBestValue) 1.5.dp else 1.dp,
                color = if (model.isBestValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${model.title} Plan",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (model.isBestValue) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                if (model.badge != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = model.badge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = model.priceFormatted,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (model.isBestValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
