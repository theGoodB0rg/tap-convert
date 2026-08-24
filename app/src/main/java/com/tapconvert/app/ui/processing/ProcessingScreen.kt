package com.tapconvert.app.ui.processing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapconvert.app.ui.components.QuantumApertureVisualizer
import com.tapconvert.app.ui.theme.SavingsGreen
import com.tapconvert.core.common.status.ProcessingStatusResolver
import com.tapconvert.core.model.ConversionStage

@Composable
fun ProcessingScreen(
    stage: ConversionStage,
    percentage: Int,
    statusMessage: String,
    onCancelClick: () -> Unit,
    onRunInBackgroundClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val friendlyStatus = ProcessingStatusResolver.resolveFriendlyStatus(stage, percentage)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val availableHeight = maxHeight
        val apertureSize = (availableHeight * 0.26f).coerceIn(110.dp, 170.dp)
        val spacing = (availableHeight * 0.025f).coerceIn(8.dp, 24.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. Quantum Aperture in Active Processing Mode
            QuantumApertureVisualizer(
                percentage = percentage,
                badgeText = "PROCESSING",
                sizeDp = apertureSize
            )

            Spacer(modifier = Modifier.height(spacing))

            // 2. Human-Friendly Primary Headline
            Text(
                text = friendlyStatus,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Reassuring Sub-badge / Stage context
            Text(
                text = statusMessage.ifBlank { "Processing media transcode on-device..." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(spacing))

            // 4. 4-Stage Tactile Milestone Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentStageIndex = when {
                    percentage < 25 -> 0
                    percentage < 55 -> 1
                    percentage < 85 -> 2
                    else -> 3
                }

                for (i in 0..3) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    i < currentStageIndex -> SavingsGreen
                                    i == currentStageIndex -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing))

            // 5. Privacy Anchor Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = SavingsGreen,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "100% On-Device • Private & Safe",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(spacing * 1.5f))

            // 6. Non-Overflowing Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancelClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "Cancel",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalButton(
                    onClick = onRunInBackgroundClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "Background",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
